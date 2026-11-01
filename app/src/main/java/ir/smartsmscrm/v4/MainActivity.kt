package ir.smartsmscrm.v4

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ir.smartsmscrm.v4.data.Store
import ir.smartsmscrm.v4.sms.SendService
import ir.smartsmscrm.v4.ui.*
import kotlinx.coroutines.launch

class MainActivity:ComponentActivity() {
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        val store=Store(this)
        if(!SendService.isRunning) store.recoverIdleAfterRestart()
        setContent { CampaignApp(store) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun CampaignApp(store:Store) {
    val palette=lightColorScheme(
        primary=Color(0xFF2C67C4),onPrimary=Color.White,
        secondary=Color(0xFF7451B8),tertiary=Color(0xFF17896B),
        background=Color(0xFFF5F7FC),surface=Color.White,
        surfaceVariant=Color(0xFFEDF1F8),onSurface=Color(0xFF202C41),
        onSurfaceVariant=Color(0xFF61708A),outline=Color(0xFFD3DBE8),error=Color(0xFFC14250)
    )
    MaterialTheme(colorScheme=palette,shapes=Shapes(
        small=RoundedCornerShape(10.dp),medium=RoundedCornerShape(14.dp),large=RoundedCornerShape(20.dp)
    )) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            var page by rememberSaveable {mutableStateOf("home")}
            var requestedCampaign by remember {mutableStateOf<Long?>(null)}
            val drawer=rememberDrawerState(DrawerValue.Closed)
            val scope=rememberCoroutineScope()
            val items=listOf(
                "home" to "خانه", "lists" to "مخاطبین و گروه‌ها",
                "campaigns" to "کمپین‌ها", "templates" to "قالب‌های پیامک",
                "reports" to "گزارش‌ها", "tools" to "تنظیمات و ابزارها"
            )
            @Composable fun DrawerIcon(id:String) {
                when(id) {
                    "home" -> Icon(Icons.Outlined.Home,contentDescription=null)
                    "lists" -> Icon(Icons.Outlined.PeopleOutline,contentDescription=null)
                    "campaigns" -> Icon(Icons.Outlined.Campaign,contentDescription=null)
                    "templates" -> Icon(Icons.Outlined.Sms,contentDescription=null)
                    "reports" -> Icon(Icons.Outlined.BarChart,contentDescription=null)
                    else -> Icon(Icons.Outlined.Settings,contentDescription=null)
                }
            }
            ModalNavigationDrawer(drawerState=drawer,drawerContent={
                ModalDrawerSheet(drawerContainerColor=MaterialTheme.colorScheme.surface) {
                    Row(Modifier.padding(18.dp),verticalAlignment=Alignment.CenterVertically) {
                        Image(painter=painterResource(id=R.drawable.ic_crm),
                            contentDescription="لوگوی Smart SMS CRM",modifier=Modifier.size(52.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Smart SMS CRM",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
                            Text("نسخه ۴.۵ · مدیریت هوشمند پیامک",style=MaterialTheme.typography.labelSmall,
                                color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    HorizontalDivider(Modifier.padding(horizontal=15.dp))
                    Spacer(Modifier.height(10.dp))
                    items.forEach {(id,label) ->
                        NavigationDrawerItem(
                            label={Text(label,fontWeight=if(page==id)FontWeight.SemiBold else FontWeight.Normal)},
                            icon={DrawerIcon(id)},selected=page==id,
                            onClick={page=id;scope.launch{drawer.close()}},
                            modifier=Modifier.padding(horizontal=11.dp,vertical=2.dp),
                            shape=RoundedCornerShape(12.dp)
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    HorizontalDivider(Modifier.padding(horizontal=15.dp))
                    Text("ارسال پیامک فقط با تأیید شما شروع می‌شود",style=MaterialTheme.typography.labelSmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(20.dp))
                }
            }) {
                Scaffold(containerColor=MaterialTheme.colorScheme.background,
                    topBar={TopAppBar(
                        title={Text(items.first{it.first==page}.second,maxLines=1,overflow=TextOverflow.Ellipsis,fontWeight=FontWeight.SemiBold)},
                        navigationIcon={IconButton(onClick={scope.launch{drawer.open()}}){Icon(Icons.Outlined.Menu,contentDescription="بازکردن منوی سمت راست")}},
                        colors=TopAppBarDefaults.topAppBarColors(containerColor=MaterialTheme.colorScheme.surface)
                    )}) { insets ->
                    Box(Modifier.fillMaxSize().padding(insets)) {
                        when(page) {
                            "home" -> HomePage(store,goCampaigns={page="campaigns"},goLists={page="lists"},
                                openCampaign={id->requestedCampaign=id;page="campaigns"})
                            "lists" -> ListsPage(store)
                            "campaigns" -> CampaignsPage(store,requestedCampaignId=requestedCampaign,consumeRequest={requestedCampaign=null})
                            "templates" -> TemplatesPage(store)
                            "reports" -> ReportsPage(store)
                            "tools" -> ToolsPage(store)
                        }
                    }
                }
            }
        }
    }
}
