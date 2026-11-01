package ir.smartsmscrm.v4.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ir.smartsmscrm.v4.data.Recipient
import ir.smartsmscrm.v4.data.Store
import ir.smartsmscrm.v4.sms.SendService
import ir.smartsmscrm.v4.util.SecureBackup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable fun ToolsPage(store:Store) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var tick by remember {mutableIntStateOf(0)}
    var password by remember {mutableStateOf("")}
    var message by remember {mutableStateOf("")}
    var importUri by remember {mutableStateOf<Uri?>(null)}
    var restorePrompt by remember {mutableStateOf(false)}
    var restoreSelection by remember {mutableStateOf(setOf<Long>())}
    var blockedNumber by remember {mutableStateOf("")}
    var allowRepeat by remember {mutableStateOf(store.allowResendWithinThreeDays())}
    var confirmRepeat by remember {mutableStateOf(false)}
    var trashPage by remember {mutableIntStateOf(0)}
    var confirmPurge by remember {mutableStateOf(false)}
    var confirmEmpty by remember {mutableStateOf(false)}
    var section by remember {mutableStateOf("sending")}
    val trashCount=remember(tick){store.trashCount()}
    val maxTrashPage=if(trashCount==0)0 else (trashCount-1)/100
    val page=trashPage.coerceIn(0,maxTrashPage)
    val trash=remember(tick,page){store.trashPage(100,page*100)}
    val blocked=remember(tick){store.blockedPhones()}
    val exporter=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) {uri ->
        if(uri!=null) scope.launch {
            message="در حال ساخت فایل رمزگذاری‌شده..."
            try {
                withContext(Dispatchers.IO) {
                    val encrypted=SecureBackup.encrypt(store.exportSnapshot(),password)
                    context.contentResolver.openOutputStream(uri)?.use {it.write(encrypted)} ?: error("دسترسی به فایل ممکن نشد")
                }
                message="فایل پشتیبان با موفقیت ذخیره شد. رمز را در جای امن نگه دارید."
            }catch(e:Exception){message="پشتیبان‌گیری ناموفق بود: ${e.message}"}
        }
    }
    val importer=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {uri:Uri?->
        if(uri!=null) {importUri=uri;restorePrompt=true}
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),verticalArrangement=Arrangement.spacedBy(9.dp)) {
        Text("تنظیمات و ابزارها",style=MaterialTheme.typography.titleLarge)
        Muted("برای دسترسی سریع، بخش موردنظر را انتخاب کنید")
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(7.dp)) {
            listOf("sending" to "تنظیمات ارسال","backup" to "پشتیبان","blocked" to "عدم ارسال","trash" to "سطل زباله").forEach{(id,title)->
                FilterChip(selected=section==id,onClick={section=id},label={Text(title)})
            }
        }
        if(message.isNotBlank()) Text(message,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.secondary)
        if(section=="sending") {
        Section("تنظیمات ارسال مجدد پیامک")
        CardPanel {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("اجازه ارسال مجدد در فاصله ۳ روز")
                    Muted(if(allowRepeat) "فعال: در کمپین جدید، شماره‌ای که اخیراً پیامک گرفته می‌تواند دوباره انتخاب شود."
                        else "غیرفعال (پیشنهادی): شماره‌های ارسال‌شده تا ۳ روز دوباره پیامک نمی‌گیرند.")
                }
                Switch(checked=allowRepeat,enabled=!SendService.isRunning,onCheckedChange={checked ->
                    if(checked) confirmRepeat=true
                    else {
                        store.setAllowResendWithinThreeDays(false)
                        allowRepeat=false
                        message="محدودیت سه‌روزه فعال شد؛ تغییر از شروع بعدی کمپین اعمال می‌شود."
                        tick++
                    }
                })
            }
            Muted("این تنظیم برای تمام کمپین‌هاست و بعد از بستن برنامه هم باقی می‌ماند. فقط ارسال مجدد پس از ارسال موفق را کنترل می‌کند؛ ارسال نامشخص، شماره‌های انصراف‌داده، سهمیه روزانه و ارسال‌های قبلی همان کمپین همچنان محافظت می‌شوند.")
            if(SendService.isRunning) Muted("برای تغییر تنظیم، ابتدا ارسال جاری را متوقف کنید.")
        }
        }
        if(section=="backup") {
        Section("پشتیبان‌گیری رمزگذاری‌شده")
        Muted("اطلاعات این نسخه از V3 مستقل است. پشتیبان شامل گروه‌ها، مخاطبین V4، کمپین‌ها، قالب‌ها و سوابق ارسال است.")
        OutlinedTextField(password,{password=it},label={Text("رمز پشتیبان (حداقل ۱۰ کاراکتر)")},modifier=Modifier.fillMaxWidth(),singleLine=true,visualTransformation=PasswordVisualTransformation())
        ButtonRow("ساخت فایل پشتیبان رمزگذاری‌شده",{
            if(SendService.isRunning) message="ابتدا ارسال پیامک را متوقف کنید."
            else if(password.length<10) message="رمز باید حداقل ۱۰ کاراکتر داشته باشد."
            else exporter.launch("SmartSMSCRM-V4-backup.smscrm4")
        })
        OutlinedButton(onClick={
            if(SendService.isRunning) message="ابتدا ارسال پیامک را متوقف کنید."
            else if(password.length<10) message="ابتدا رمز فایل را وارد کنید."
            else importer.launch(arrayOf("*/*"))
        },modifier=Modifier.fillMaxWidth()) {Text("انتخاب فایل برای بازیابی و جایگزینی")}
        }
        if(section=="blocked") {
        Section("فهرست عدم ارسال پیامک")
        Muted("اگر مخاطبی نمی‌خواهد پیامک بگیرد، شماره او را ثبت کن. این شماره در کمپین‌های بعدی یا باقی‌مانده کمپین جاری ارسال نمی‌شود.")
        OutlinedTextField(blockedNumber,{blockedNumber=it},label={Text("موبایل انصراف از پیامک")},modifier=Modifier.fillMaxWidth(),singleLine=true)
        ButtonRow("ثبت شماره در فهرست عدم ارسال",{
            try {store.blockPhone(blockedNumber);blockedNumber="";message="شماره به فهرست عدم ارسال اضافه شد";tick++}
            catch(e:Exception){message=e.message?:"شماره نامعتبر"}
        },enabled=blockedNumber.isNotBlank())
        blocked.forEach {phone ->
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                Text(phone,modifier=Modifier.padding(top=12.dp))
                TextButton(onClick={store.unblockPhone(phone);tick++}){Text("برداشتن منع ارسال")}
            }
        }
        }
        if(section=="trash") {
        Section("سطل زباله مخاطبین · $trashCount شماره")
        Muted("حذف گروهی باعث حذف سابقه ارسال کمپین‌ها نمی‌شود.")
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            TextButton(onClick={restoreSelection=trash.map {it.id}.toSet()}){Text("انتخاب همه (${trash.size})")}
            TextButton(onClick={restoreSelection=emptySet()}){Text("لغو")}
        }
        ButtonRow("بازیابی ${restoreSelection.size} شماره انتخاب‌شده",{
            try {store.restoreRecipients(restoreSelection.toList());restoreSelection=emptySet();message="شماره‌ها بازیابی شدند";tick++}
            catch(e:Exception){message=e.message?:"بازیابی ممکن نشد"}
        },enabled=restoreSelection.isNotEmpty())
        OutlinedButton(onClick={confirmPurge=true},enabled=restoreSelection.isNotEmpty(),modifier=Modifier.fillMaxWidth()) {
            Text("حذف دائمی ${restoreSelection.size} شماره انتخاب‌شده")
        }
        OutlinedButton(onClick={confirmEmpty=true},enabled=trashCount>0,modifier=Modifier.fillMaxWidth()) {
            Text("خالی‌کردن کامل سطل زباله ($trashCount شماره)")
        }
        if(trashCount>0) Muted("نمایش ${page*100+1} تا ${minOf((page+1)*100,trashCount)} از $trashCount · صفحه ${page+1} از ${maxTrashPage+1}")
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            OutlinedButton(onClick={trashPage=page-1;restoreSelection=emptySet()},enabled=page>0){Text("صفحه قبل")}
            OutlinedButton(onClick={trashPage=page+1;restoreSelection=emptySet()},enabled=page<maxTrashPage){Text("صفحه بعد")}
        }
        if(trash.isEmpty()) EmptyView("سطل زباله خالی است.")
        LazyColumn(Modifier.heightIn(max=440.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            items(trash,key={it.id}) {r->
                CardPanel {
                    Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(r.name.ifBlank {r.phone})
                            Muted(r.phone)
                        }
                        Checkbox(checked=r.id in restoreSelection,onCheckedChange={v->restoreSelection=if(v)restoreSelection+r.id else restoreSelection-r.id})
                    }
                }
            }
        }
        }
    }
    if(confirmRepeat) Confirm("تأیید اجازه ارسال مجدد",
        "با فعال‌سازی، در کمپین‌های دیگر امکان پیامک مجدد به شماره‌ای که کمتر از ۳ روز قبل پیامک گرفته فراهم می‌شود و ممکن است هزینه داشته باشد. فقط برای دریافت‌کنندگان دارای رضایت استفاده کنید. شماره‌های با نتیجه نامشخص یا انصراف‌داده همچنان مسدود می‌مانند. ادامه ارسال همچنان نیازمند تأیید دستی شماست.",
        yes="فعال‌کردن ارسال مجدد",onYes={
            confirmRepeat=false
            if(!SendService.isRunning) {
                store.setAllowResendWithinThreeDays(true)
                allowRepeat=true
                message="ارسال مجدد فعال شد؛ در شروع دستی بعدی کمپین اعمال می‌شود."
                tick++
            } else message="ابتدا ارسال جاری را متوقف کنید."
        },onNo={confirmRepeat=false})
    if(confirmPurge) Confirm("حذف دائمی شماره‌های انتخاب‌شده",
        "${restoreSelection.size} شماره برای همیشه از بانک مخاطبین V4 حذف می‌شوند. امکان بازیابی آن‌ها از سطل زباله وجود ندارد؛ صف و سابقه کمپین‌های قدیمی محفوظ می‌ماند.",
        yes="حذف دائمی",onYes={
            try {val n=store.permanentlyDeleteTrash(restoreSelection.toList());message="$n شماره برای همیشه حذف شد";restoreSelection=emptySet();tick++}
            catch(e:Exception){message=e.message?:"حذف ناموفق"}
            confirmPurge=false
        },onNo={confirmPurge=false})
    if(confirmEmpty) Confirm("خالی‌کردن کامل سطل زباله",
        "تمام $trashCount مخاطب داخل سطل زباله به‌طور برگشت‌ناپذیر حذف می‌شوند. آیا از فایل پشتیبان خود مطمئن هستید؟",
        yes="خالی‌کردن سطل زباله",onYes={
            try {val n=store.emptyTrash();message="$n مخاطب حذف دائمی شد";restoreSelection=emptySet();trashPage=0;tick++}
            catch(e:Exception){message=e.message?:"حذف ناموفق"}
            confirmEmpty=false
        },onNo={confirmEmpty=false})
    if(restorePrompt && importUri!=null) Confirm("هشدار: جایگزینی کامل اطلاعات",
        "تمام اطلاعات فعلی V4 با اطلاعات فایل پشتیبان جایگزین می‌شود. این عملیات برگشت‌پذیر نیست، مگر اینکه از اطلاعات فعلی نسخه پشتیبان جداگانه داشته باشید. آیا از اطلاعات فعلی پشتیبان گرفته‌اید؟",
        yes="بله، بازیابی کن",onYes={
            val uri=importUri!!;restorePrompt=false;importUri=null
            scope.launch {
                message="در حال بررسی و بازیابی فایل..."
                try {
                    withContext(Dispatchers.IO) {
                        val bytes=context.contentResolver.openInputStream(uri)?.use {it.readBytes()}?:error("دسترسی به فایل ممکن نشد")
                        val text=SecureBackup.decrypt(bytes,password)
                        store.restoreSnapshot(text)
                    }
                    allowRepeat=store.allowResendWithinThreeDays()
                    message="بازیابی کامل شد. گزارش‌ها و فهرست‌ها را بازبینی کنید.";tick++
                }catch(e:Exception){message="بازیابی انجام نشد: ${e.message?:"رمز اشتباه یا فایل خراب است"}"}
            }
        },onNo={restorePrompt=false;importUri=null})
}
