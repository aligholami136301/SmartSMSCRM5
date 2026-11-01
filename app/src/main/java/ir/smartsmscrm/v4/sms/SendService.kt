package ir.smartsmscrm.v4.sms

import android.Manifest
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import ir.smartsmscrm.v4.MainActivity
import ir.smartsmscrm.v4.data.*
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Explicit user-started session only. Reboot and next day NEVER restart SMS automatically. */
class SendService: Service() {
    companion object {
        const val START = "ir.smartsmscrm.v4.RUN"
        const val PAUSE = "ir.smartsmscrm.v4.PAUSE"
        const val EXTRA_CAMPAIGN = "campaign"
        const val CHANNEL = "active_sms_v4"
        @Volatile var isRunning: Boolean = false
            private set
        @Volatile var isPausing: Boolean = false
            private set
        fun start(ctx:Context,id:Long) {
            val i=Intent(ctx,SendService::class.java).setAction(START).putExtra(EXTRA_CAMPAIGN,id)
            ContextCompat.startForegroundService(ctx,i)
        }
        fun pause(ctx:Context) {
            if(isRunning) ctx.startService(Intent(ctx,SendService::class.java).setAction(PAUSE))
        }
    }
    private val executor=Executors.newSingleThreadExecutor()
    private val stopped=AtomicBoolean(false)
    private var currentCampaign=0L
    private val store by lazy { Store(applicationContext) }
    override fun onBind(intent:Intent?): IBinder?=null
    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL,"ارسال پیامک در پس‌زمینه",NotificationManager.IMPORTANCE_LOW)
        )
    }
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        when(intent?.action) {
            PAUSE -> {
                if (!isRunning) {
                    stopSelfResult(startId)
                } else {
                    isPausing=true
                    stopped.set(true)
                    currentCampaign.takeIf { it>0 }?.let {
                        store.setReason(it,"توقف درخواست شد؛ پاسخ شماره در حال ارسال بررسی می‌شود. سپس سرویس بسته می‌شود.")
                    }
                }
                return START_NOT_STICKY
            }
            START -> {
                val id=intent.getLongExtra(EXTRA_CAMPAIGN,0)
                if(id<=0L) return START_NOT_STICKY
                if(isRunning) {
                    store.setReason(id,"یک سرویس ارسال هنوز فعال است یا در حال توقف است؛ پس از پایان آن، دوباره به‌صورت دستی شروع کنید.")
                    if(id!=currentCampaign)store.setState(id,"PAUSED")
                    return START_NOT_STICKY
                }
                currentCampaign=id
                isRunning=true
                isPausing=false
                stopped.set(false)
                // Must call startForeground promptly; notification includes a genuine Pause action.
                try {
                    updateNotification("آماده‌سازی ارسال",0,0)
                    executor.execute {sendLoop(id)}
                } catch(e:Exception) {
                    store.setState(id,"PAUSED")
                    store.setReason(id,"شروع سرویس پس‌زمینه ناموفق: ${e.javaClass.simpleName}: ${e.message.orEmpty()}")
                    stopSelf()
                }
            }
            else -> return START_NOT_STICKY
        }
        return START_NOT_STICKY
    }
    private fun updateNotification(text:String,sent:Int,total:Int) {
        val pause=PendingIntent.getService(this,7,Intent(this,SendService::class.java).setAction(PAUSE),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val open=PendingIntent.getActivity(this,8,Intent(this,MainActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification=Notification.Builder(this,CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("Smart SMS CRM V4 — ارسال پیامک")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(total.coerceAtLeast(0),sent.coerceAtLeast(0),total<=0)
            .addAction(Notification.Action.Builder(android.R.drawable.ic_media_pause,"توقف ارسال",pause).build())
            .build()
        if(Build.VERSION.SDK_INT>=34) startForeground(4304,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(4304,notification)
    }
    private fun sendLoop(id:Long) {
        try {
            val campaign=store.campaign(id) ?: return
            if(campaign.archived || campaign.removed) {store.setReason(id,"کمپین بایگانی یا حذف شده است");return}
            store.setReason(id,"")
            if(ContextCompat.checkSelfPermission(this,Manifest.permission.SEND_SMS)!=PackageManager.PERMISSION_GRANTED) {
                store.setState(id,"PAUSED");store.setReason(id,"مجوز SEND_SMS فعال نیست"); return
            }
            store.clearAbandonedSending(id)
            store.reviveDeferred(id)
            store.setState(id,"RUNNING")
            if(ContextCompat.checkSelfPermission(this,Manifest.permission.READ_PHONE_STATE)!=PackageManager.PERMISSION_GRANTED) {
                store.setState(id,"PAUSED");store.setReason(id,"مجوز شناسایی سیم‌کارت داده نشده است");return
            }
            val active=try {
                getSystemService(android.telephony.SubscriptionManager::class.java).activeSubscriptionInfoList.orEmpty()
                    .any { it.subscriptionId==campaign.simId }
            } catch (_:Exception) {false}
            if(!active) {store.setState(id,"PAUSED");store.setReason(id,"سیم‌کارت انتخاب‌شده فعال نیست؛ تنظیمات کمپین را بررسی کنید");return}
            val manager=SmsManager.getSmsManagerForSubscriptionId(campaign.simId)
            while(!stopped.get()) {
                // Never exceed user daily recipient limit, or shared 900-segment per-SIM daily quota.
                val fresh=store.campaign(id) ?: break
                if(fresh.state!="RUNNING")break
                if(store.attemptedToday(id)>=campaign.dailyLimit) {store.setState(id,"DAILY_LIMIT");store.setReason(id,"سقف ارسال امروز تکمیل شد؛ فردا با تأیید شما ادامه می‌یابد");break}
                val next=store.nextWaiting(id)
                if(next==null) {
                    val finished=fresh.waiting==0 && fresh.failed==0 && fresh.unknown==0
                    store.setState(id,if(finished)"DONE" else "PAUSED")
                    store.setReason(id,if(finished)"صف تکمیل شد؛ تحویل پیامک را جداگانه بررسی کنید" else
                        store.deferExplanation(id).ifBlank {"گیرنده آماده نیست؛ وضعیت ناموفق یا نامشخص را بررسی کنید"})
                    break
                }
                if(store.isBlocked(next.phone)) {store.skipBlocked(next.id);continue}
                if(store.recentlyAttempted(next.phone)) {store.defer(next.id);continue}
                val parts=manager.divideMessage(campaign.message)
                if(parts.isEmpty()) {store.setState(id,"PAUSED");store.setReason(id,"متن پیامک برای ارسال معتبر نیست");break}
                if(store.usedSegmentsToday(campaign.simId)+parts.size>900) {
                    store.setState(id,"DAILY_LIMIT")
                    store.setReason(id,"سقف مشترک ۹۰۰ بخش پیامک برای سیم‌کارت امروز تکمیل شده؛ فردا با تأیید شما ادامه دهید")
                    break
                }
                val stamp=store.checkpointSend(next,campaign.simId,parts.size)
                updateNotification("${fresh.sent} از ${fresh.total} — ${next.phone}",fresh.sent,fresh.total)
                try {
                    val sent=ArrayList<PendingIntent>(parts.size)
                    val delivered=ArrayList<PendingIntent>(parts.size)
                    parts.indices.forEach { index ->
                        sent.add(makeCallback(next.id,stamp,index,false))
                        delivered.add(makeCallback(next.id,stamp,index,true))
                    }
                    manager.sendMultipartTextMessage(next.phone,null,parts,sent,delivered)
                } catch(e:Exception) {
                    val why=e.javaClass.simpleName + ": " + (e.message?:"")
                    store.markFailed(next.id,why)
                    store.setReason(id,"خطای ارسال: $why")
                }
                // Wait for network acceptance callbacks. Unknown must NOT be blindly retried.
                val deadline=System.currentTimeMillis()+45_000
                // A manual pause stops NEW sends, but must not abandon the already-dispatched SMS.
                // Finish waiting for its callback (or timeout) to avoid false UNKNOWN and double sending.
                while(store.statusOf(next.id)==SENDING && System.currentTimeMillis()<deadline) {
                    Thread.sleep(300)
                }
                if(store.statusOf(next.id)==SENDING) {
                    store.markUnknown(next.id)
                    store.setReason(id,"پاسخ قطعی برای یک شماره دریافت نشد؛ تکرار خودکار ممنوع")
                }
                val after=store.campaign(id)
                updateNotification("${after?.sent ?: 0} از ${after?.total ?: 0} ثبت شده",after?.sent ?: 0,after?.total ?: 0)
                var remaining=campaign.spacing*1000L
                while(!stopped.get()&&remaining>0) {val step=minOf(remaining,250L);Thread.sleep(step);remaining-=step}
            }
        } catch (_:InterruptedException) {
            store.setReason(id,"ارسال قطع شد؛ وضعیت مخاطبین در حال ارسال را بررسی کنید")
            // A transaction already records in-flight recipients; they are UNKNOWN on next start.
        } catch (e:Exception) {
            store.setState(id,"PAUSED")
            store.setReason(id,"خطای موتور ارسال: ${e.javaClass.simpleName}: ${e.message.orEmpty()}")
        } finally {
            // Keep isRunning true until onDestroy, so a rapid restart cannot be accepted
            // by an executor that is about to be shut down by the prior service.
            if(store.campaign(id)?.state=="RUNNING") {
                store.setState(id,"PAUSED")
                if(stopped.get())store.setReason(id,"ارسال با درخواست شما متوقف شد؛ ادامه از شماره‌های ارسال‌نشده است")
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }
    private fun makeCallback(queueId:Long,attempt:Long,index:Int,delivery:Boolean):PendingIntent {
        val kind=if(delivery)"delivery" else "sent"
        val intent=Intent(this,SmsResultReceiver::class.java).apply {
            action=if(delivery)SmsResultReceiver.DELIVERED else SmsResultReceiver.SENT_ACTION
            data=Uri.parse("smscrm://$kind/$queueId/$attempt/$index")
            putExtra("queue",queueId);putExtra("attempt",attempt)
        }
        return PendingIntent.getBroadcast(this,0,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
    override fun onDestroy() {
        stopped.set(true)
        executor.shutdownNow()
        currentCampaign=0
        isPausing=false
        isRunning=false
        super.onDestroy()
    }
}

private fun describeSmsFailure(code:Int):String = when(code) {
    Activity.RESULT_OK -> "قبول درخواست ارسال توسط گوشی (نه تضمین تحویل)"
    SmsManager.RESULT_ERROR_GENERIC_FAILURE -> "خطای عمومی ارسال؛ شبکه یا سیم‌کارت را بررسی کنید"
    SmsManager.RESULT_ERROR_NO_SERVICE -> "سرویس شبکه موبایل در دسترس نیست"
    SmsManager.RESULT_ERROR_RADIO_OFF -> "رادیوی موبایل خاموش است"
    SmsManager.RESULT_ERROR_NULL_PDU -> "ساختار پیامک نامعتبر است"
    else -> "کد خطای ارسال اندروید: $code"
}

class SmsResultReceiver: BroadcastReceiver() {
    companion object {const val SENT_ACTION="ir.smartsmscrm.v4.SENT";const val DELIVERED="ir.smartsmscrm.v4.DELIVERED"}
    override fun onReceive(context:Context,intent:Intent) {
        val id=intent.getLongExtra("queue",-1L)
        val attempt=intent.getLongExtra("attempt",-1L)
        if(id<0 || attempt<0)return
        val db=Store(context)
        if(intent.action==SENT_ACTION) db.recordSentPart(id,attempt,resultCode==Activity.RESULT_OK,describeSmsFailure(resultCode))
        if(intent.action==DELIVERED) db.recordDeliveryPart(id,attempt,resultCode==Activity.RESULT_OK)
        db.close()
    }
}
