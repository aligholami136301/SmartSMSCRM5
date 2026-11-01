package ir.smartsmscrm.v4.ui

import android.net.Uri
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.smartsmscrm.v4.data.*
import ir.smartsmscrm.v4.util.VCardParser
import ir.smartsmscrm.v4.util.PhoneContactsImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable fun ListsPage(store:Store) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var tick by remember {mutableIntStateOf(0)}
    val groups=remember(tick){store.lists()}
    var current by remember {mutableStateOf<Long?>(null)}
    var groupQuery by remember {mutableStateOf("")}
    var rootTab by remember {mutableStateOf("all")}
    var allQuery by remember {mutableStateOf("")}
    var allPage by remember {mutableIntStateOf(0)}
    var newName by remember {mutableStateOf("")}
    var filter by remember {mutableStateOf("")}
    var paste by remember {mutableStateOf("")}
    var result by remember {mutableStateOf("")}
    var selected by remember {mutableStateOf(setOf<Long>())}
    var editing by remember {mutableStateOf<Recipient?>(null)}
    var rename by remember {mutableStateOf(false)}
    var deleteMode by remember {mutableIntStateOf(0)}
    var deleteSelected by remember {mutableStateOf(false)}
    var moveSelected by remember {mutableStateOf(false)}
    var targetGroup by remember {mutableStateOf<Long?>(null)}
    var changedName by remember {mutableStateOf("")}
    var editPhone by remember {mutableStateOf("")}
    var editProduct by remember {mutableStateOf("")}
    var editNotes by remember {mutableStateOf("")}
    var editCompany by remember {mutableStateOf("")}
    var editAddress by remember {mutableStateOf("")}
    var adding by remember {mutableStateOf(false)}
    var addPhone by remember {mutableStateOf("")}
    var importExpanded by remember {mutableStateOf(false)}
    var importError by remember {mutableStateOf("")}
    var showListCreate by remember {mutableStateOf(false)}
    var importPhoneHelp by remember {mutableStateOf(false)}
    var moreExpanded by remember {mutableStateOf(false)}
    var busy by remember {mutableStateOf(false)}
    val chosen=groups.firstOrNull {it.id==current}
    var recipientPage by remember {mutableIntStateOf(0)}
    val recipientCount=remember(tick,current,filter){current?.let{store.recipientCount(it,filter)}?:0}
    val pageMax=if(recipientCount==0)0 else (recipientCount-1)/100
    val page=recipientPage.coerceIn(0,pageMax)
    val recipients=remember(tick,current,filter,page){current?.let{store.recipientPage(it,filter,100,page*100)}?:emptyList()}
    val allCount=remember(tick,allQuery){store.globalRecipientCount(allQuery)}
    val allMaxPage=if(allCount==0)0 else (allCount-1)/100
    val selectedAllPage=allPage.coerceIn(0,allMaxPage)
    val allRows=remember(tick,allQuery,selectedAllPage){store.globalRecipientPage(allQuery,100,selectedAllPage*100)}
    val syncPhone: () -> Unit = {
        scope.launch {
            busy=true; result="در حال خواندن دفترچه مخاطبین گوشی…"
            try {
                val r=withContext(Dispatchers.IO){PhoneContactsImporter.importAll(context,store)}
                result="گروه «مخاطبین گوشی» آماده شد: ${r.added} شماره جدید، ${r.duplicates} تکراری و ${r.invalid} شماره نامعتبر/غیرموبایل."
                tick++
            } catch(e:Exception) { result="خواندن مخاطبین گوشی انجام نشد: ${e.message.orEmpty()}" }
            finally {busy=false}
        }
    }
    val contactsPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {granted->
        if(granted) syncPhone() else result="دسترسی مخاطبین داده نشد. همچنان می‌توانید با VCF مخاطبین را وارد کنید."
    }
    val vcfPicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {uri:Uri? ->
        val id=current
        if(uri!=null && id!=null) scope.launch {
            busy=true;result="در حال خواندن فایل..."
            try {
                val parsed=withContext(Dispatchers.IO) {
                    val people=mutableListOf<ir.smartsmscrm.v4.util.ImportedPerson>()
                    context.contentResolver.openInputStream(uri)?.use {input->VCardParser.parseEach(input){p->people.add(p)}}
                    store.importPeople(id,people)
                }
                result="${parsed.added} شماره جدید · ${parsed.duplicates} تکراری · ${parsed.invalid} نامعتبر"
                tick++
            }catch(e:Exception){result="خطا در ورود VCF: ${e.message}"}
            finally {busy=false}
        }
    }
    Column(Modifier.fillMaxSize().padding(horizontal=14.dp,vertical=8.dp)) {
        if(current==null) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                FilterChip(selected=rootTab=="all",onClick={rootTab="all";selected=emptySet();result=""},label={Text("همه مخاطبین")})
                FilterChip(selected=rootTab=="groups",onClick={rootTab="groups";selected=emptySet();result=""},label={Text("گروه‌ها")})
            }
            if(rootTab=="all") {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("همه مخاطبین",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
                        Muted("$allCount مخاطب از تمام گروه‌ها · جستجوی سراسری")
                    }
                    FilledIconButton(onClick={adding=true;addPhone="";changedName="";editProduct="";editNotes="";editCompany="";editAddress=""}) {
                        Icon(Icons.Outlined.PersonAdd,contentDescription="ثبت مخاطب")
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,
                    horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick={
                        if(ContextCompat.checkSelfPermission(context,Manifest.permission.READ_CONTACTS)==PackageManager.PERMISSION_GRANTED)
                            syncPhone() else contactsPermission.launch(Manifest.permission.READ_CONTACTS)
                    },modifier=Modifier.weight(1f),enabled=!busy) {
                        Icon(Icons.Outlined.PhoneAndroid,null); Spacer(Modifier.width(5.dp))
                        Text(if(busy)"در حال ورود…" else "مخاطبین گوشی")
                    }
                    IconButton(onClick={importPhoneHelp=true}) {Icon(Icons.Outlined.Info,contentDescription="راهنمای ورود مخاطبین گوشی")}
                }
                OutlinedTextField(allQuery,{allQuery=it;allPage=0;selected=emptySet()},
                    label={Text("جستجوی همه: نام، شماره، آدرس، شرکت، محصول، گروه")},
                    leadingIcon={Icon(Icons.Outlined.Search,null)},singleLine=true,modifier=Modifier.fillMaxWidth())
                if(result.isNotBlank()) { Spacer(Modifier.height(6.dp));Muted(result) }
                if(selected.isNotEmpty()) {
                    CardPanel {
                        Text("${selected.size} مخاطب انتخاب شده",fontWeight=FontWeight.SemiBold)
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick={moveSelected=true;targetGroup=null},modifier=Modifier.weight(1f)){Text("انتقال گروهی")}
                            OutlinedButton(onClick={deleteSelected=true},modifier=Modifier.weight(1f)){Text("حذف گروهی")}
                            TextButton(onClick={selected=emptySet()}){Text("لغو")}
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    TextButton(onClick={selected=store.globalRecipientIds(allQuery).toSet()},
                        enabled=allCount in 1..20000 && !busy,modifier=Modifier.weight(1f)) {
                        Text(if(allCount>20000)"برای انتخاب همه، ابتدا فیلتر کنید" else "انتخاب همه نتایج ($allCount)")
                    }
                    Text("${selectedAllPage+1}/${allMaxPage+1}",style=MaterialTheme.typography.labelMedium)
                    IconButton(onClick={allPage=(selectedAllPage-1).coerceAtLeast(0)},enabled=selectedAllPage>0) {
                        Icon(Icons.Outlined.ChevronRight,contentDescription="صفحه قبل")
                    }
                    IconButton(onClick={allPage=(selectedAllPage+1).coerceAtMost(allMaxPage)},enabled=selectedAllPage<allMaxPage) {
                        Icon(Icons.Outlined.ChevronLeft,contentDescription="صفحه بعد")
                    }
                }
                if(allRows.isEmpty()) EmptyView(
                    message="مخاطبی پیدا نشد. از ورود مخاطبین گوشی یا گروه‌ها استفاده کنید.",
                    icon=Icons.Outlined.PeopleOutline,
                    actionLabel="ورود مخاطبین گوشی",
                    onAction={
                        if(ContextCompat.checkSelfPermission(context,Manifest.permission.READ_CONTACTS)==PackageManager.PERMISSION_GRANTED)
                            syncPhone() else contactsPermission.launch(Manifest.permission.READ_CONTACTS)
                    }
                )
                LazyColumn(verticalArrangement=Arrangement.spacedBy(4.dp),contentPadding=PaddingValues(bottom=20.dp)) {
                    items(allRows,key={it.id}) { r -> CardPanel {
                        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                            Checkbox(checked=r.id in selected,onCheckedChange={v->selected=if(v)selected+r.id else selected-r.id})
                            Column(Modifier.weight(1f)) {
                                Text(r.name.ifBlank{r.phone},fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis)
                                Muted(r.phone+" · "+(groups.firstOrNull{it.id==r.listId}?.name?:"بدون گروه"))
                                if(r.address.isNotBlank()) Muted("آدرس: ${r.address}")
                                else if(r.product.isNotBlank()) Muted("محصول: ${r.product}")
                            }
                            IconButton(onClick={
                                editing=r;editPhone=r.phone;changedName=r.name;editCompany=r.company;
                                editAddress=r.address;editProduct=r.product;editNotes=r.notes;result=""
                            }) {Icon(Icons.Outlined.Edit,contentDescription="ویرایش")}
                        }
                    } }
                }
            } else {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("گروه‌های مخاطبین",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
                    Muted("هر گروه را باز کنید تا شماره‌ها و ابزارهای آن را ببینید")
                }
                FilledIconButton(onClick={newName="";showListCreate=true}){
                    Icon(Icons.Outlined.Add,contentDescription="ساخت گروه جدید")
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(groupQuery,{groupQuery=it},label={Text("جستجوی نام گروه")},
                leadingIcon={Icon(Icons.Outlined.Search,null)},singleLine=true,modifier=Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            val visibleGroups=groups.filter{it.name.contains(groupQuery,ignoreCase=true)}
            if(result.isNotBlank()) { Muted(result); Spacer(Modifier.height(6.dp)) }
            if(visibleGroups.isEmpty()) EmptyView(message="گروهی پیدا نشد. برای ساخت گروه روی + بزنید.", icon=Icons.Outlined.Folder)
            LazyColumn(verticalArrangement=Arrangement.spacedBy(6.dp),contentPadding=PaddingValues(bottom=20.dp)) {
                items(visibleGroups,key={it.id}) {g ->
                    CardPanel {
                        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Groups,null,tint=MaterialTheme.colorScheme.secondary,modifier=Modifier.size(27.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(g.name,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis)
                                Muted("${g.count} مخاطب")
                            }
                            TextButton(onClick={current=g.id;selected=emptySet();filter="";recipientPage=0;result=""}) {
                                Text("بازکردن")
                                Icon(Icons.Outlined.ChevronLeft,contentDescription=null)
                            }
                        }
                    }
                }
            }
            }
        } else {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                IconButton(onClick={current=null;selected=emptySet();filter="";result="";tick++}) {
                    Icon(Icons.Outlined.ArrowForward,contentDescription="بازگشت به گروه‌ها")
                }
                Column(Modifier.weight(1f)) {
                    Text(chosen?.name?:"فهرست",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold,maxLines=1)
                    Muted("$recipientCount مخاطب · صفحه ${page+1} از ${pageMax+1}")
                }
                Box {
                    IconButton(onClick={moreExpanded=true}){ Icon(Icons.Outlined.MoreVert,contentDescription="عملیات گروه") }
                    DropdownMenu(expanded=moreExpanded,onDismissRequest={moreExpanded=false}) {
                        DropdownMenuItem(text={Text("ورود سریع شماره‌ها")},leadingIcon={Icon(Icons.Outlined.ContentPaste,null)},onClick={moreExpanded=false;importError="";importExpanded=true})
                        DropdownMenuItem(text={Text("ورود فایل VCF")},leadingIcon={Icon(Icons.Outlined.FileUpload,null)},onClick={moreExpanded=false;vcfPicker.launch(arrayOf("text/x-vcard","text/vcard","*/*"))})
                        if(current!=1L) DropdownMenuItem(text={Text("تغییر نام گروه")},leadingIcon={Icon(Icons.Outlined.Edit,null)},onClick={moreExpanded=false;changedName=chosen?.name.orEmpty();rename=true})
                        if(current!=1L) DropdownMenuItem(text={Text("حذف گروه")},leadingIcon={Icon(Icons.Outlined.DeleteOutline,null)},onClick={moreExpanded=false;deleteMode=1})
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Button(onClick={adding=true;addPhone="";changedName="";editProduct="";editNotes="";editCompany="";editAddress=""},modifier=Modifier.weight(1f)) {
                    Icon(Icons.Outlined.PersonAdd,null);Spacer(Modifier.width(6.dp));Text("مخاطب جدید")
                }
                OutlinedButton(onClick={importError="";importExpanded=true},modifier=Modifier.weight(1f)){
                    Icon(Icons.Outlined.FileUpload,null);Spacer(Modifier.width(5.dp));Text("ورود شماره")
                }
            }
            // The quick-import editor is rendered in a dialog below, not in the group page.
            // A long pasted list must never push the save button off-screen.
            if(result.isNotBlank()) {Spacer(Modifier.height(6.dp));Muted(result)}
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(filter,{filter=it;recipientPage=0;selected=emptySet()},
                label={Text("جستجوی نام، شماره، آدرس، شرکت یا محصول")},leadingIcon={Icon(Icons.Outlined.Search,null)},
                singleLine=true,modifier=Modifier.fillMaxWidth())
            if(selected.isNotEmpty()) CardPanel {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text("${selected.size} مخاطب انتخاب شده",modifier=Modifier.weight(1f),fontWeight=FontWeight.SemiBold)
                    TextButton(onClick={selected=emptySet()}){Text("لغو")}
                }
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick={targetGroup=null;moveSelected=true},modifier=Modifier.weight(1f)){Text("انتقال گروهی")}
                    OutlinedButton(onClick={deleteSelected=true},modifier=Modifier.weight(1f)){Text("حذف گروهی")}
                }
            }
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                TextButton(onClick={
                    val list=current
                    if(list!=null) {selected=store.recipientIds(list,filter).toSet();result="${selected.size} مخاطب از تمام صفحات انتخاب شد"}
                },enabled=recipientCount>0 && !busy,modifier=Modifier.weight(1f)){Text("انتخاب همه ($recipientCount)")}
                Text("${page+1}/${pageMax+1}",style=MaterialTheme.typography.labelMedium)
                IconButton(onClick={recipientPage=(page-1).coerceAtLeast(0)},enabled=page>0) {
                    Icon(Icons.Outlined.ChevronRight,contentDescription="صفحه قبل")
                }
                IconButton(onClick={recipientPage=(page+1).coerceAtMost(pageMax)},enabled=page<pageMax) {
                    Icon(Icons.Outlined.ChevronLeft,contentDescription="صفحه بعد")
                }
            }
            if(recipients.isEmpty()) EmptyView(message="مخاطبی در این گروه پیدا نشد.", icon=Icons.Outlined.Person)
            LazyColumn(verticalArrangement=Arrangement.spacedBy(3.dp),contentPadding=PaddingValues(bottom=24.dp)) {
                items(recipients,key={it.id}) {r ->
                    CardPanel {
                        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                            Checkbox(checked=r.id in selected,onCheckedChange={v->selected=if(v)selected+r.id else selected-r.id})
                            Column(Modifier.weight(1f)) {
                                Text(r.name.ifBlank {r.phone},fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis)
                                if(r.name.isNotBlank()) Muted(r.phone)
                                if(r.product.isNotBlank()) Muted("محصول: ${r.product}")
                            }
                            IconButton(onClick={
                                editing=r;editPhone=r.phone;changedName=r.name;editCompany=r.company;
                                editAddress=r.address;editProduct=r.product;editNotes=r.notes;result=""
                            }) {Icon(Icons.Outlined.Edit,contentDescription="ویرایش مخاطب")}
                        }
                    }
                }
            }
        }
    }
    if(importExpanded && current!=null) AlertDialog(
        onDismissRequest={ if(!busy) importExpanded=false },
        title={Text("ورود سریع شماره‌ها")},
        text={
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("هر شماره در یک خط وارد شود. حتی با چند هزار شماره، کادر اسکرول می‌شود و دکمه ثبت در پایین پنجره باقی می‌ماند.",
                    style=MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value=paste,
                    onValueChange={paste=it},
                    label={Text("شماره‌ها")},
                    placeholder={Text("0912...\n0913...،نام،محصول تولیدی")},
                    modifier=Modifier.fillMaxWidth(),
                    minLines=3,
                    maxLines=5,
                    enabled=!busy
                )
                if(importError.isNotBlank()) {
                    Text(importError, color=MaterialTheme.colorScheme.error,
                        style=MaterialTheme.typography.bodySmall)
                }
                if(busy) {
                    LinearProgressIndicator(modifier=Modifier.fillMaxWidth())
                    Text("در حال ثبت شماره‌ها؛ لطفاً عملیات را تکرار نکنید.",
                        style=MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton={
            Button(onClick={
                val groupId=current ?: return@Button
                val numbers=paste
                importError=""
                scope.launch {
                    busy=true
                    try {
                        val r=withContext(Dispatchers.IO){store.importNumbers(groupId,numbers)}
                        result="${r.added} جدید، ${r.duplicates} تکراری، ${r.invalid} نامعتبر"
                        paste=""
                        importExpanded=false
                        recipientPage=0
                        tick++
                    } catch(e:Exception) {
                        importError="خطا در ورود شماره‌ها: ${e.message ?: "خطای نامشخص"}"
                        result=importError
                    } finally { busy=false }
                }
            },enabled=paste.isNotBlank() && !busy) {Text(if(busy)"در حال ثبت…" else "ثبت شماره‌ها")}
        },
        dismissButton={
            Row(verticalAlignment=Alignment.CenterVertically) {
                TextButton(onClick={
                    importExpanded=false
                    vcfPicker.launch(arrayOf("text/x-vcard","text/vcard","*/*"))
                },enabled=!busy){Text("فایل VCF")}
                TextButton(onClick={importExpanded=false},enabled=!busy){Text("انصراف")}
            }
        }
    )
    if(importPhoneHelp) AlertDialog(onDismissRequest={importPhoneHelp=false},
        title={Text("ورود مخاطبین گوشی")},
        text={Text("با اجازه شما، شماره‌های موبایل از دفترچه تلفن سامسونگ در یک گروه مستقل به نام «مخاطبین گوشی» کپی می‌شوند. شماره‌های تکراری در همان گروه دوباره ثبت نمی‌شوند و دفترچه اصلی گوشی تغییر نمی‌کند.")},
        confirmButton={TextButton(onClick={importPhoneHelp=false}){Text("متوجه شدم")}})
    if(showListCreate) AlertDialog(onDismissRequest={showListCreate=false},title={Text("ساخت گروه جدید")},
        text={OutlinedTextField(newName,{newName=it},label={Text("نام گروه")},singleLine=true)},
        confirmButton={TextButton(onClick={
            try {val id=store.createList(newName);current=id;newName="";showListCreate=false;tick++}
            catch(e:Exception){result="نام نامعتبر یا تکراری: ${e.message}"}
        },enabled=newName.isNotBlank()){Text("ساخت")}},dismissButton={TextButton(onClick={showListCreate=false}){Text("انصراف")}})
    if(rename && current!=null) AlertDialog(onDismissRequest={rename=false},title={Text("تغییر نام گروه")},
        text={OutlinedTextField(changedName,{changedName=it})},
        confirmButton={TextButton(onClick={try{store.renameList(current!!,changedName);tick++;rename=false}catch(e:Exception){result=e.message.orEmpty()}}){Text("ذخیره")}},
        dismissButton={TextButton(onClick={rename=false}){Text("انصراف")}})
    if(deleteSelected) Confirm("حذف گروهی","${selected.size} شماره به سطل زباله منتقل می‌شوند. صف کمپین‌های قبلی تغییر نخواهد کرد.","انتقال به سطل زباله",
        onYes={store.deleteRecipients(selected.toList());selected=emptySet();deleteSelected=false;tick++},onNo={deleteSelected=false})
    if(moveSelected) AlertDialog(onDismissRequest={moveSelected=false},
        title={Text("انتقال ${selected.size} مخاطب")},text={
            Column(Modifier.heightIn(max=290.dp).verticalScroll(rememberScrollState())) {
                Muted("گروه مقصد را انتخاب کنید. شماره‌های تکراری در مقصد، در گروه فعلی باقی می‌مانند.")
                groups.filter{it.id!=current}.forEach {g->
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        RadioButton(selected=targetGroup==g.id,onClick={targetGroup=g.id})
                        Text(g.name)
                    }
                }
            }
        },confirmButton={TextButton(onClick={
            val dest=targetGroup
            if(dest!=null) scope.launch {
                moveSelected=false;busy=true
                try {
                    val (moved,skipped)=withContext(Dispatchers.IO){store.moveRecipients(selected.toList(),dest)}
                    result="$moved مخاطب منتقل شد · $skipped مورد تکراری یا بدون تغییر";selected=emptySet();tick++
                }catch(e:Exception){result="خطا در انتقال: ${e.message}"}
                finally {busy=false}
            }
        },enabled=targetGroup!=null && !busy){Text("انتقال")}},dismissButton={TextButton(onClick={moveSelected=false}){Text("انصراف")}})
    if(deleteMode==1) AlertDialog(onDismissRequest={deleteMode=0},title={Text("حذف گروه")},
        text={Text("دو روش وجود دارد. حذف گروه به‌تنهایی شماره‌ها را به «بدون گروه» می‌برد. حذف همراه اعضا، شماره‌ها را به سطل زباله منتقل می‌کند.")},
        confirmButton={Column {
            TextButton(onClick={try{store.deleteList(current!!,true);current=null;deleteMode=0;tick++}catch(e:Exception){result=e.message?:"خطا"}}){Text("فقط حذف گروه")}
            TextButton(onClick={deleteMode=2}){Text("حذف همراه مخاطبین")}
        }},dismissButton={TextButton(onClick={deleteMode=0}){Text("انصراف")}})
    if(deleteMode==2) Confirm("تأیید حذف گروهی","تمام شماره‌های این گروه به سطل زباله منتقل می‌شوند؛ کمپین‌های قبلی مستقل هستند.","حذف گروه و شماره‌ها",
        onYes={store.deleteList(current!!,false);current=null;deleteMode=0;tick++},onNo={deleteMode=0})
    if(editing!=null) AlertDialog(onDismissRequest={editing=null},title={Text("ویرایش مخاطب")},text={
        Column(Modifier.heightIn(max=440.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(7.dp)) {
            OutlinedTextField(editPhone,{editPhone=it},label={Text("شماره موبایل (قابل ویرایش)")},modifier=Modifier.fillMaxWidth(),singleLine=true)
            OutlinedTextField(changedName,{changedName=it},label={Text("نام و نام خانوادگی")},modifier=Modifier.fillMaxWidth())
            OutlinedTextField(editCompany,{editCompany=it},label={Text("شرکت")},modifier=Modifier.fillMaxWidth())
            OutlinedTextField(editAddress,{editAddress=it},label={Text("آدرس")},modifier=Modifier.fillMaxWidth())
            OutlinedTextField(editProduct,{editProduct=it},label={Text("محصول تولیدی")},modifier=Modifier.fillMaxWidth())
            OutlinedTextField(editNotes,{editNotes=it},label={Text("توضیحات")},modifier=Modifier.fillMaxWidth())
            if(result.isNotBlank()) Muted(result)
        }
    },confirmButton={TextButton(onClick={
        try {store.updateRecipient(editing!!.id,editPhone,changedName,editProduct,editNotes,editCompany,editAddress);editing=null;result="شماره و مشخصات ذخیره شدند. صف کمپین‌های قبلی تغییر نکرد.";tick++}
        catch(e:Exception){result=e.message?:"خطا در ویرایش"}
    }){Text("ذخیره")}},dismissButton={TextButton(onClick={editing=null}){Text("انصراف")}})
    if(adding) AlertDialog(onDismissRequest={adding=false},title={Text("مخاطب جدید")},text={
        Column(Modifier.heightIn(max=440.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(7.dp)) {
            OutlinedTextField(addPhone,{addPhone=it},label={Text("شماره موبایل")},modifier=Modifier.fillMaxWidth())
            OutlinedTextField(changedName,{changedName=it},label={Text("نام و نام خانوادگی")},modifier=Modifier.fillMaxWidth())
            OutlinedTextField(editCompany,{editCompany=it},label={Text("شرکت")},modifier=Modifier.fillMaxWidth())
            OutlinedTextField(editAddress,{editAddress=it},label={Text("آدرس")},modifier=Modifier.fillMaxWidth())
            OutlinedTextField(editProduct,{editProduct=it},label={Text("محصول تولیدی")},modifier=Modifier.fillMaxWidth())
            OutlinedTextField(editNotes,{editNotes=it},label={Text("توضیحات")},modifier=Modifier.fillMaxWidth())
            if(result.isNotBlank()) Muted(result)
        }
    },confirmButton={TextButton(onClick={
        try {val ok=store.addRecipient(current?:1L,addPhone,changedName,editCompany,editAddress,editProduct,editNotes)
            result=if(ok)"مخاطب جدید ثبت شد" else "این شماره قبلاً در همین گروه ثبت شده است"
            if(ok){adding=false;tick++}
        }catch(e:Exception){result=e.message?:"شماره نامعتبر"}
    }){Text("ذخیره")}},dismissButton={TextButton(onClick={adding=false}){Text("انصراف")}})
}
