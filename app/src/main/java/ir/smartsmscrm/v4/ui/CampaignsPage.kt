package ir.smartsmscrm.v4.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsMessage
import android.telephony.SubscriptionManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import ir.smartsmscrm.v4.data.*
import ir.smartsmscrm.v4.sms.SendService

@Composable fun CampaignsPage(store:Store, requestedCampaignId:Long?=null, consumeRequest:()->Unit={}) {
    val ctx=LocalContext.current
    var tick by remember {mutableIntStateOf(0)}
    var status by remember {mutableStateOf("all")}
    var search by remember {mutableStateOf("")}
    var creating by remember {mutableStateOf(false)}
    var showing by remember {mutableStateOf<Long?>(null)}
    // Recompose the active campaign only; also refresh the campaign list when idle.
    LaunchedEffect(showing, creating) {
        while(true) { delay(if(showing!=null) 1300L else 8000L); tick++ }
    }
    LaunchedEffect(requestedCampaignId) {
        if(requestedCampaignId!=null) { showing=requestedCampaignId;creating=false;tick++;consumeRequest() }
    }
    var approveSend by remember {mutableStateOf<Long?>(null)}
    var confirmArchive by remember {mutableStateOf<Long?>(null)}
    var confirmRemove by remember {mutableStateOf<Long?>(null)}
    var collection by remember {mutableStateOf("active")}
    var note by remember {mutableStateOf("")}
    var actionMenuId by remember {mutableStateOf<Long?>(null)}
    var detailActions by remember {mutableStateOf(false)}
    var groupSearch by remember {mutableStateOf("")}
    val listRevision=if(showing==null) tick else -1 // avoid reloading all campaigns on every live poll
    val groups=remember(listRevision){store.lists().filter {it.count>0}}
    val templates=remember(listRevision){store.templates()}
    val campaigns=remember(listRevision){store.campaigns()}
    var title by remember {mutableStateOf("")}
    var message by remember {mutableStateOf("")}
    var chosenGroups by remember {mutableStateOf(setOf<Long>())}
    var simId by remember {mutableStateOf<Int?>(null)}
    var dailyLimit by remember {mutableStateOf("200")}
    var spacing by remember {mutableStateOf("5")}
    var consent by remember {mutableStateOf(false)}
    val required=buildList {
        add(Manifest.permission.SEND_SMS)
        add(Manifest.permission.READ_PHONE_STATE)
        if(Build.VERSION.SDK_INT>=33)add(Manifest.permission.POST_NOTIFICATIONS)
    }
    val permissions=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){ results ->
        note=if(results[Manifest.permission.SEND_SMS]==true && results[Manifest.permission.READ_PHONE_STATE]==true)"مجوزها دریافت شدند؛ سیم‌کارت را بررسی کنید." else "بدون مجوز SMS و سیم‌کارت، ارسال ممکن نیست."
        tick++
    }
    val phoneGranted=ContextCompat.checkSelfPermission(ctx,Manifest.permission.READ_PHONE_STATE)==PackageManager.PERMISSION_GRANTED
    val sendGranted=ContextCompat.checkSelfPermission(ctx,Manifest.permission.SEND_SMS)==PackageManager.PERMISSION_GRANTED
    val simOptions=remember(phoneGranted,tick){
        if(!phoneGranted) emptyList<Pair<Int,String>>()
        else try {
            val manager=ctx.getSystemService(SubscriptionManager::class.java)
            manager.activeSubscriptionInfoList.orEmpty().map {info -> info.subscriptionId to "${info.displayName} · SIM ${info.simSlotIndex+1}"}
        }catch(e:Exception){emptyList()}
    }
    Column(Modifier.fillMaxSize().padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        if(creating || showing!=null) Section(if(creating)"ساخت کمپین جدید" else "جزئیات کمپین")
        if(note.isNotBlank()) Muted(note)
        if(creating) {
            LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                item {
                    OutlinedButton(onClick={creating=false},modifier=Modifier.fillMaxWidth()){Text("بازگشت به فهرست")}
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(title,{title=it},label={Text("عنوان کمپین")},modifier=Modifier.fillMaxWidth())
                    Section("۱. انتخاب گروه یا گروه‌ها")
                    if(groups.size>5) OutlinedTextField(groupSearch,{groupSearch=it},label={Text("جستجوی گروه")},modifier=Modifier.fillMaxWidth(),singleLine=true)
                    groups.filter {it.name.contains(groupSearch,true)}.forEach {g->
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
                            Text("${g.name} · ${g.count} نفر",modifier=Modifier.weight(1f))
                            Checkbox(checked=g.id in chosenGroups,onCheckedChange={chosenGroups=if(it)chosenGroups+g.id else chosenGroups-g.id})
                        }
                    }
                    if(groups.isEmpty()) Muted("ابتدا از بخش مخاطبین یک گروه دارای شماره بسازید.")
                    Muted("${chosenGroups.size} گروه انتخاب‌شده")
                    Muted("شماره‌های تکراری بین گروه‌ها فقط یک‌بار به کمپین اضافه می‌شوند. فهرست کمپین پس از ساخت مستقل می‌ماند.")
                    Section("۲. متن پیام")
                    if(templates.isNotEmpty()) {
                        var templateExpanded by remember {mutableStateOf(false)}
                        Box {
                            OutlinedButton(onClick={templateExpanded=true}){Text("انتخاب قالب ذخیره‌شده")}
                            DropdownMenu(expanded=templateExpanded,onDismissRequest={templateExpanded=false}) {
                                templates.forEach {t->DropdownMenuItem(text={Text(t.title)},onClick={message=t.body;templateExpanded=false})}
                            }
                        }
                    }
                    OutlinedTextField(message,{message=it},label={Text("متن پیامک")},modifier=Modifier.fillMaxWidth(),minLines=4)
                    val parts=if(message.isBlank())0 else SmsMessage.calculateLength(message,false)[0]
                    Muted("حدود $parts بخش پیامک برای هر گیرنده (تخمین گوشی)")
                    Section("۳. سیم‌کارت و محدودیت‌ها")
                    if(!phoneGranted || !sendGranted) {
                        ButtonRow("دریافت مجوزهای پیامک و سیم‌کارت",{permissions.launch(required.toTypedArray())})
                    }
                    if(simOptions.isEmpty()) Muted("سیم‌کارت فعال قابل شناسایی نیست. برای ذخیره پیش‌نویس هم ابتدا مجوز سیم‌کارت لازم است.")
                    simOptions.forEach {(id,label)->Row(Modifier.fillMaxWidth()){
                        RadioButton(selected=simId==id,onClick={simId=id})
                        Text(label,modifier=Modifier.padding(top=13.dp))
                    }}
                    OutlinedTextField(dailyLimit,{dailyLimit=it.filter(Char::isDigit)},label={Text("تعداد گیرنده در روز (مثلاً ۲۰۰)")},modifier=Modifier.fillMaxWidth(),singleLine=true)
                    OutlinedTextField(spacing,{spacing=it.filter(Char::isDigit)},label={Text("فاصله ارسال، ثانیه (۵ تا ۱۲۰)")},modifier=Modifier.fillMaxWidth(),singleLine=true)
                    Muted("حداکثر داخلی: ۹۰۰ بخش پیامک در روز برای هر سیم‌کارت، مشترک میان کمپین‌ها؛ تضمین پذیرش توسط اپراتور نیست.")
                    Section("۴. تأیید و ذخیره")
                    Row(Modifier.fillMaxWidth()){
                        Checkbox(checked=consent,onCheckedChange={consent=it})
                        Text("فقط مخاطبینی را انتخاب کرده‌ام که برای دریافت پیام تبلیغاتی رضایت داده‌اند.",modifier=Modifier.weight(1f).padding(top=7.dp))
                    }
                    ButtonRow("ذخیره پیش‌نویس کمپین",{
                        try {
                            val (id,total)=store.createCampaign(title,message,chosenGroups,simId?:-1,dailyLimit.toIntOrNull()?:0,spacing.toIntOrNull()?:0)
                            note="کمپین شماره $id با $total گیرنده به‌صورت پیش‌نویس ذخیره شد. هیچ پیامکی ارسال نشد."
                            creating=false;tick++
                        }catch(e:Exception){note="خطا: ${e.message?:"مقادیر واردشده را بررسی کنید"}"}
                    },enabled=title.isNotBlank()&&message.isNotBlank()&&chosenGroups.isNotEmpty()&&simId!=null&&consent)
                    Spacer(Modifier.height(16.dp))
                }
            }
        } else if(showing!=null) {
            val c=store.campaign(showing!!)
            if(c==null) {
                EmptyView(message="کمپین پیدا نشد", icon=Icons.Outlined.Search, actionLabel="بازگشت به فهرست", onAction={showing=null;tick++})
                OutlinedButton(onClick={showing=null;tick++},modifier=Modifier.fillMaxWidth()){Text("بازگشت به کمپین‌ها")}
            } else {
                // The primary action bar stays visible, including while browsing 2000+ recipients.
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    IconButton(onClick={showing=null;tick++}) {Icon(Icons.Outlined.ArrowForward,contentDescription="بازگشت")}
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            Text(c.title,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold,
                                maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.weight(1f,fill=false))
                            if(c.state=="RUNNING") {
                                Spacer(Modifier.width(8.dp))
                                LiveBadge()
                            }
                        }
                        Muted("${c.total} گیرنده · ${c.sent} ارسال‌شده · ${c.waiting} باقی‌مانده")
                    }
                    StatusPillWithIcon(stateFa(c.state), stateIcon(c.state), stateColor(c.state))
                }
                ProgressSection(sent=c.sent, total=c.total)
                Row(horizontalArrangement=Arrangement.spacedBy(7.dp),modifier=Modifier.fillMaxWidth()) {
                    MetricTile("ارسال گوشی","${c.sent}",MaterialTheme.colorScheme.tertiary,Modifier.weight(1f))
                    MetricTile("باقی‌مانده","${c.waiting}",MaterialTheme.colorScheme.primary,Modifier.weight(1f))
                    MetricTile("ناموفق","${c.failed}",MaterialTheme.colorScheme.error,Modifier.weight(1f))
                }
                // Single primary action with no need to tap Refresh.
                val activeHere=SendService.isRunning && c.state=="RUNNING"
                if(activeHere && !SendService.isPausing) {
                    ButtonRow("توقف امن ارسال",{
                        SendService.pause(ctx)
                        note="توقف درخواست شد؛ نتیجه پیامک جاری بررسی می‌شود."
                        tick++
                    })
                } else if(SendService.isRunning && SendService.isPausing) {
                    Surface(color=MaterialTheme.colorScheme.tertiaryContainer,shape=MaterialTheme.shapes.medium) {
                        Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier=Modifier.size(21.dp),strokeWidth=2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text("در حال توقف امن؛ نتیجه پیامک جاری حفظ می‌شود",style=MaterialTheme.typography.bodyMedium)
                        }
                    }
                } else if(SendService.isRunning) {
                    Surface(color=MaterialTheme.colorScheme.surfaceVariant,shape=MaterialTheme.shapes.medium) {
                        Text("سرویس ارسال فعال است. اگر همین کمپین را تازه شروع کرده‌اید، وضعیت تا لحظاتی دیگر نمایش داده می‌شود؛ در غیر این صورت ابتدا کمپین فعال را متوقف کنید.",modifier=Modifier.padding(12.dp))
                    }
                } else if((c.waiting>0 || c.deferred>0) && c.state!="DONE" && !c.archived && !c.removed) {
                    val label=if(c.state=="DRAFT")"شروع ارسال" else "ادامه ارسال از باقی‌مانده‌ها"
                    ButtonRow(label,{ approveSend=c.id },enabled=sendGranted && phoneGranted && simOptions.any {it.first==c.simId})
                    if(!sendGranted || !phoneGranted) OutlinedButton(onClick={permissions.launch(required.toTypedArray())},modifier=Modifier.fillMaxWidth()) {
                        Text("دریافت مجوز ارسال پیامک")
                    }
                } else if(c.state=="DONE") {
                    Surface(color=MaterialTheme.colorScheme.tertiaryContainer,shape=MaterialTheme.shapes.medium) {
                        Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
                            Icon(Icons.Outlined.CheckCircle,null,tint=MaterialTheme.colorScheme.tertiary)
                            Spacer(Modifier.width(8.dp))
                            Text("این کمپین تکمیل شده است؛ گزارش و سوابق در پایین صفحه‌اند.")
                        }
                    }
                } else if(c.deferred>0) {
                    Muted("گیرندگان معوق‌اند؛ دلیل تعویق در جزئیات نمایش داده شده است.")
                }
                if(activeHere) Muted("وضعیت ارسال به‌طور خودکار به‌روزرسانی می‌شود؛ نیازی به تازه‌سازی دستی نیست.")
                var detailsExpanded by remember(showing) { mutableStateOf(false) }
                var qFilter by remember(showing) { mutableStateOf("") }
                var qPage by remember(showing,qFilter) { mutableIntStateOf(0) }
                val pageSize=100
                val totalRecipients=remember(tick,showing,qFilter) {store.queueCount(c.id,qFilter)}
                val lastPage=if(totalRecipients==0)0 else (totalRecipients-1)/pageSize
                val currentPage=qPage.coerceIn(0,lastPage)
                val rows=remember(tick,showing,qFilter,currentPage) {store.queuePage(c.id,qFilter,pageSize,currentPage*pageSize)}
                LazyColumn(modifier=Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement=Arrangement.spacedBy(8.dp),contentPadding=PaddingValues(bottom=28.dp)) {
                    item {
                        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                                Text("جزئیات و گزارش",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f))
                                TextButton(onClick={detailsExpanded=!detailsExpanded}) {
                                    Text(if(detailsExpanded)"بستن جزئیات" else "نمایش جزئیات")
                                    Icon(if(detailsExpanded)Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,null)
                                }
                            }
                            if(detailsExpanded) CardPanel {
                                Text("متن پیامک",fontWeight=FontWeight.SemiBold)
                                Text(c.message,style=MaterialTheme.typography.bodyMedium)
                                HorizontalDivider()
                                Muted("${c.delivered} تحویل تأییدشده شبکه · ${c.unknown} وضعیت نامشخص · ${c.deferred} معوق")
                                Muted("سقف ${c.dailyLimit} نفر در روز · فاصله ${c.spacing} ثانیه · سیم‌کارت ${c.simId}")
                                Muted("امروز: ${store.attemptedToday(c.id)} تلاش ثبت‌شده · ${store.usedSegmentsToday(c.simId)} بخش از سقف مشترک ۹۰۰ بخش")
                                Muted("تأیید ارسال گوشی به معنای تأیید تحویل به مخاطب نیست.")
                                Muted(if(store.allowResendWithinThreeDays())"ارسال مجدد سه‌روزه: مجاز" else "ارسال مجدد سه‌روزه: غیرفعال")
                                if(c.lastReason.isNotBlank()) Text("آخرین وضعیت: ${c.lastReason}")
                                if(c.deferred>0) Muted(store.deferExplanation(c.id))
                                if(c.failed>0 && !SendService.isRunning) OutlinedButton(
                                    onClick={val n=store.retryFailures(c.id);note="$n شماره ناموفق به صف برگشت؛ پیش از ارسال بررسی کنید.";tick++},
                                    modifier=Modifier.fillMaxWidth()) {Text("بازگرداندن ناموفق‌ها به صف")}
                                if(!SendService.isRunning && c.state!="RUNNING") {
                                    OutlinedButton(onClick={detailActions=true},modifier=Modifier.fillMaxWidth()) {Text("بایگانی و مدیریت کمپین")}
                                    DropdownMenu(expanded=detailActions,onDismissRequest={detailActions=false}) {
                                        DropdownMenuItem(text={Text(if(c.archived)"بازگردانی از بایگانی" else "بایگانی")},onClick={detailActions=false;confirmArchive=c.id})
                                        DropdownMenuItem(text={Text(if(c.removed)"بازگردانی حذف‌شده" else "حذف از فهرست")},onClick={detailActions=false;confirmRemove=c.id})
                                    }
                                }
                            }
                            Text("گیرندگان کمپین",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
                            Row(modifier=Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                                listOf("" to "همه","WAITING" to "منتظر","DEFERRED" to "تعویق","SENT" to "ارسال‌شده","FAILED" to "ناموفق","UNKNOWN" to "نامشخص").forEach {(code,label)->
                                    FilterChip(selected=qFilter==code,onClick={qFilter=code},label={Text(label)})
                                }
                            }
                            if(totalRecipients==0) Muted("گیرنده‌ای در این وضعیت وجود ندارد.")
                            else {
                                Muted("${currentPage*pageSize+1} تا ${minOf((currentPage+1)*pageSize,totalRecipients)} از $totalRecipients گیرنده · صفحه ${currentPage+1} از ${lastPage+1}")
                                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                                    OutlinedButton(onClick={qPage=currentPage-1},enabled=currentPage>0) {Text("صفحه قبل")}
                                    OutlinedButton(onClick={qPage=currentPage+1},enabled=currentPage<lastPage) {Text("صفحه بعد")}
                                }
                            }
                        }
                    }
                    items(rows,key={it.id}) {r->CardPanel {
                        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(r.name.ifBlank {r.phone},fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis)
                                Muted("${r.phone} · ${r.parts} بخش")
                            }
                            StatusPill(stateFa(r.status))
                        }
                        if(r.note.isNotBlank()) Muted("توضیح: ${r.note}")
                        if(r.delivered) Muted("تأیید تحویل شبکه دریافت شد")
                    }}
                }
            }
        } else {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("کمپین‌های پیامکی",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
                    Muted("کمپین‌ها، وضعیت ارسال و سوابق مستقل")
                }
                FilledIconButton(onClick={title="";message="";chosenGroups=emptySet();simId=null;dailyLimit="200";spacing="5";consent=false;note="";creating=true}) {
                    Icon(Icons.Outlined.Add,contentDescription="کمپین جدید")
                }
                IconButton(onClick={tick++}) {Icon(Icons.Outlined.Refresh,contentDescription="به‌روزرسانی")}
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(search,{search=it},label={Text("جستجوی نام کمپین")},
                leadingIcon={Icon(Icons.Outlined.Search,null)},modifier=Modifier.fillMaxWidth(),singleLine=true)
            Row(modifier=Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                listOf("all" to "همه","active" to "فعال","draft" to "پیش‌نویس","done" to "تمام‌شده").forEach {(code,label)->
                    FilterChip(selected=status==code,onClick={status=code},label={Text(label)})
                }
            }
            Row(modifier=Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                listOf("active" to "کمپین‌های من","archive" to "بایگانی","removed" to "حذف‌شده‌ها").forEach{(key,label)->
                    FilterChip(selected=collection==key,onClick={collection=key},label={Text(label)})
                }
            }

            val filtered=campaigns.filter { c -> (when(collection){
                "archive" -> c.archived && !c.removed
                "removed" -> c.removed
                else -> !c.archived && !c.removed
            }) && c.title.contains(search,true) && when(status) {
                "active" -> c.state in setOf("RUNNING","PAUSED","DAILY_LIMIT")
                "draft" -> c.state=="DRAFT"
                "done" -> c.state=="DONE"
                else -> true
            } }
            if(filtered.isEmpty()) EmptyView(
                message="کمپینی در این بخش پیدا نشد.",
                icon=Icons.Outlined.Campaign,
                actionLabel="ساخت کمپین جدید",
                onAction={creating=true;title="";message="";chosenGroups=emptySet();simId=null;dailyLimit="200";spacing="5";consent=false;note=""}
            )
            LazyColumn(verticalArrangement=Arrangement.spacedBy(5.dp),contentPadding=PaddingValues(bottom=22.dp)) {
                items(filtered,key={it.id}) {c -> CardPanel {
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(c.title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold,
                                maxLines=1,overflow=TextOverflow.Ellipsis)
                            Spacer(Modifier.height(4.dp))
                            StatusPillWithIcon(stateFa(c.state), stateIcon(c.state), stateColor(c.state))
                        }
                        Box {
                            IconButton(onClick={actionMenuId=c.id}) {Icon(Icons.Outlined.MoreVert,contentDescription="عملیات کمپین")}
                            DropdownMenu(expanded=actionMenuId==c.id,onDismissRequest={actionMenuId=null}) {
                                DropdownMenuItem(text={Text("مشاهده جزئیات")},onClick={showing=c.id;actionMenuId=null;tick++})
                                if(!SendService.isRunning && c.state!="RUNNING") {
                                    DropdownMenuItem(text={Text(if(c.archived)"بازگردانی از بایگانی" else "بایگانی")},
                                        onClick={confirmArchive=c.id;actionMenuId=null})
                                    DropdownMenuItem(text={Text(if(c.removed)"بازگردانی حذف‌شده" else "حذف از فهرست")},
                                        onClick={confirmRemove=c.id;actionMenuId=null})
                                }
                            }
                        }
                    }
                    if(c.total > 0) {
                        ProgressSection(sent=c.sent, total=c.total, label="${c.sent} از ${c.total}")
                    }
                    Muted("${c.sent} ارسال گوشی · ${c.delivered} تحویل · ${c.waiting} باقی‌مانده")
                    TextButton(onClick={showing=c.id;tick++}) { Text(when(c.state) {
                        "DRAFT" -> "شروع ارسال و جزئیات"
                        "RUNNING" -> "مشاهده پیشرفت زنده"
                        "PAUSED","DAILY_LIMIT" -> "ادامه ارسال و جزئیات"
                        else -> "مشاهده گزارش"
                    }) }
                } }
            }
        }
    }
    if(confirmArchive!=null) {
        val c=store.campaign(confirmArchive!!)
        if(c!=null) Confirm(if(c.archived) "بازگردانی از بایگانی" else "بایگانی کمپین",
            "کمپین «${c.title}» همراه با صف و گزارش‌هایش محفوظ می‌ماند.",
            yes=if(c.archived)"بازگردانی" else "بایگانی",onYes={
                confirmArchive=null
                try {store.setArchived(c.id,!c.archived);note="تغییر وضعیت بایگانی انجام شد";showing=null;tick++}
                catch(e:Exception){note=e.message?:"عملیات ممکن نشد"}
            },onNo={confirmArchive=null}) else confirmArchive=null
    }
    if(confirmRemove!=null) {
        val c=store.campaign(confirmRemove!!)
        if(c!=null) Confirm(if(c.removed)"بازگردانی کمپین" else "حذف کمپین از فهرست",
            "کمپین «${c.title}» از فهرست اصلی پنهان می‌شود، اما سوابق ارسال و محدودیت‌های ضدتکرار برای حفاظت از گیرندگان در بانک نگهداری می‌شود. حذف دائمی سوابق ارسال انجام نمی‌شود.",
            yes=if(c.removed)"بازگردانی" else "حذف از فهرست",onYes={
                confirmRemove=null
                try {if(c.removed)store.undoRemoveCampaign(c.id) else store.removeCampaign(c.id);note="فهرست کمپین‌ها به‌روزرسانی شد";showing=null;tick++}
                catch(e:Exception){note=e.message?:"عملیات ممکن نشد"}
            },onNo={confirmRemove=null}) else confirmRemove=null
    }
    if(approveSend!=null) {
        val c=store.campaign(approveSend!!)
        if(c!=null) Confirm("تأیید ارسال واقعی پیامک",
            "کمپین «${c.title}» · ${c.waiting} گیرنده در صف. امروز حداکثر ${c.dailyLimit} گیرنده و سقف ۹۰۰ بخش SMS برای این سیم‌کارت. تنظیم ارسال مجدد ۳ روزه: ${if(store.allowResendWithinThreeDays()) "فعال" else "غیرفعال"}. ارسال از سیم‌کارت گوشی انجام می‌شود و ممکن است هزینه داشته باشد. فقط گیرندگان دارای رضایت را انتخاب کنید.",
            yes="شروع ارسال واقعی",onYes={
                val id=c.id; approveSend=null
                try {
                    if(SendService.isRunning) note="سرویس قبلی هنوز فعال است؛ پس از توقف کامل دوباره شروع کنید."
                    else { SendService.start(ctx,id);note="شروع ارسال درخواست شد؛ وضعیت به‌صورت خودکار تغییر می‌کند." }
                    tick++
                }
                catch(e:Exception){note="شروع ارسال ممکن نشد: ${e.message}"}
            },onNo={approveSend=null})
        else approveSend=null
    }
}
