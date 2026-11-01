package ir.smartsmscrm.v4.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.List
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import kotlinx.coroutines.delay
import ir.smartsmscrm.v4.sms.SendService
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.smartsmscrm.v4.data.Store

@Composable
fun ReportsPage(store: Store) {
    var tick by remember { mutableIntStateOf(0) }
    var filter by remember { mutableStateOf("") }
    var timeFilter by remember { mutableStateOf("all") }

    LaunchedEffect(Unit) {
        while (true) {
            delay(5000L)
            if (SendService.isRunning) tick++
        }
    }

    val counts = remember(tick) { store.allCounts() }
    val campaigns = remember(tick) { store.campaigns().filter { !it.removed } }
    val primary = MaterialTheme.colorScheme.primary
    val green = MaterialTheme.colorScheme.tertiary

    val visible = remember(campaigns, filter, timeFilter) {
        campaigns.filter { it.title.contains(filter, true) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "گزارش‌ها",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Muted("وضعیت ارسال و تحویل به تفکیک کمپین")
            }
            IconButton(onClick = { tick++ }) {
                Icon(Icons.Outlined.Refresh, contentDescription = "به‌روزرسانی گزارش")
            }
        }

        // Summary metrics
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricTile("شماره‌های فعال", "${counts["numbers"] ?: 0}", primary, Modifier.weight(1f))
            MetricTile("کمپین‌ها", "${counts["campaigns"] ?: 0}", MaterialTheme.colorScheme.secondary, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricTile("ارسال گوشی", "${counts["sent"] ?: 0}", green, Modifier.weight(1f))
            MetricTile("نامشخص", "${counts["unknown"] ?: 0}", MaterialTheme.colorScheme.error, Modifier.weight(1f))
        }

        Muted("«ارسال گوشی» لزوماً به معنی دریافت توسط مخاطب نیست. تحویل تأییدشده جدا نمایش داده می‌شود.")

        Section("گزارش هر کمپین")

        OutlinedTextField(
            filter,
            { filter = it },
            label = { Text("جستجوی کمپین در گزارش") },
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium
        )

        // Time filter chips
        FilterChipRow(
            options = listOf(
                "all" to "همه",
                "running" to "فعال",
                "done" to "تکمیل‌شده",
                "other" to "سایر"
            ),
            selected = timeFilter,
            onSelect = { timeFilter = it }
        )

        val finalList = when (timeFilter) {
            "running" -> visible.filter { it.state in setOf("RUNNING", "PAUSED", "DAILY_LIMIT") }
            "done" -> visible.filter { it.state == "DONE" }
            "other" -> visible.filter { it.state !in setOf("RUNNING", "PAUSED", "DAILY_LIMIT", "DONE") }
            else -> visible
        }

        if (finalList.isEmpty()) {
            EmptyView(
                message = "کمپینی مطابق فیلتر پیدا نشد.",
                icon = Icons.Outlined.List
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(finalList, key = { it.id }) { c ->
                    CardPanel {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                c.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                                maxLines = 1
                            )
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
                            Muted("تحویل شبکه: ${c.delivered}")
                            Muted("ناموفق: ${c.failed}")
                        }
                        Muted("باقی‌مانده: ${c.waiting} · نامشخص: ${c.unknown}")
                    }
                }
            }
        }
    }
}
