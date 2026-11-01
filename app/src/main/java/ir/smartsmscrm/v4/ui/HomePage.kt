package ir.smartsmscrm.v4.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.smartsmscrm.v4.data.Store
import ir.smartsmscrm.v4.sms.SendService
import kotlinx.coroutines.delay

@Composable
fun HomePage(
    store: Store,
    goCampaigns: () -> Unit,
    goLists: () -> Unit,
    openCampaign: (Long) -> Unit
) {
    var revision by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(4000L)
            revision++
        }
    }
    val stats = remember(revision) { store.allCounts() }
    val attention = remember(revision) {
        store.campaigns().filter {
            !it.archived && !it.removed && it.state in setOf("RUNNING", "PAUSED", "DAILY_LIMIT")
        }
    }
    val primary = MaterialTheme.colorScheme.primary
    val purple = MaterialTheme.colorScheme.secondary
    val green = MaterialTheme.colorScheme.tertiary

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "مدیریت پیامک‌ها",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Muted("کمپین‌ها، مخاطبین و ارسال‌های امروز در یک نگاه")

        // Active campaign card (priority)
        if (attention.isNotEmpty()) {
            val c = attention.firstOrNull { it.state == "RUNNING" } ?: attention.first()
            val isLive = c.state == "RUNNING" || SendService.isRunning

            CardPanel {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "کمپین نیازمند توجه",
                                style = MaterialTheme.typography.labelMedium,
                                color = primary
                            )
                            if (isLive) {
                                Spacer(Modifier.width(8.dp))
                                LiveBadge()
                            }
                        }
                        Text(
                            c.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2
                        )
                    }
                    StatusPillWithIcon(
                        stateFa(c.state),
                        stateIcon(c.state),
                        stateColor(c.state)
                    )
                }

                ProgressSection(sent = c.sent, total = c.total)

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "${c.sent} ارسال‌شده",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "${c.waiting} باقی‌مانده",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (c.waiting > 0) primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { openCampaign(c.id) },
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Icon(
                            if (c.state == "RUNNING") Icons.Outlined.Visibility else Icons.Outlined.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (c.state == "RUNNING") "مشاهده زنده"
                            else "بازکردن و ادامه"
                        )
                    }
                }
            }

            // Other active campaigns
            if (attention.size > 1) {
                Section("دیگر کمپین‌های فعال")
                attention.drop(1).take(4).forEach { camp ->
                    CardPanel {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(camp.title, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                Muted("${camp.waiting} باقی‌مانده از ${camp.total}")
                            }
                            StatusPill(stateFa(camp.state), stateColor(camp.state))
                            Spacer(Modifier.width(6.dp))
                            TextButton(onClick = { openCampaign(camp.id) }) {
                                Text("بازکردن")
                            }
                        }
                    }
                }
            }
        } else {
            CardPanel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        tint = green,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("کمپین فعالی ندارید", fontWeight = FontWeight.SemiBold)
                        Muted("از فهرست کمپین‌ها می‌توانید پیش‌نویس بسازید یا ارسال را شروع کنید.")
                    }
                }
                OutlinedButton(
                    onClick = goCampaigns,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Outlined.Campaign, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("رفتن به کمپین‌ها")
                }
            }
        }

        // Today's summary
        Section("خلاصه امروز")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricTile(
                "شماره فعال",
                "${stats["numbers"] ?: 0}",
                primary,
                Modifier.weight(1f)
            )
            MetricTile(
                "تعداد کمپین",
                "${stats["campaigns"] ?: 0}",
                purple,
                Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricTile(
                "ارسال گوشی",
                "${stats["sent"] ?: 0}",
                green,
                Modifier.weight(1f)
            )
            MetricTile(
                "نتیجه نامشخص",
                "${stats["unknown"] ?: 0}",
                MaterialTheme.colorScheme.error,
                Modifier.weight(1f)
            )
        }

        // Quick access
        Section("دسترسی سریع")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = goCampaigns,
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(Icons.Outlined.Campaign, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("کمپین‌ها")
            }
            OutlinedButton(
                onClick = goLists,
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(Icons.Outlined.PeopleOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("مخاطبین")
            }
        }

        Muted("نمایش «ارسال‌شده توسط گوشی» تضمین تحویل به گیرنده نیست. تأیید تحویل در گزارش کمپین جدا ثبت می‌شود.")
    }
}
