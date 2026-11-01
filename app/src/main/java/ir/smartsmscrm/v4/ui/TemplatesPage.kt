package ir.smartsmscrm.v4.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.smartsmscrm.v4.data.MessageTemplate
import ir.smartsmscrm.v4.data.Store

@Composable
fun TemplatesPage(store: Store) {
    var revision by remember { mutableIntStateOf(0) }
    val templates = remember(revision) { store.templates() }
    var search by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<MessageTemplate?>(null) }
    var editor by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<MessageTemplate?>(null) }
    var expandedId by remember { mutableStateOf<Long?>(null) }
    var viewing by remember { mutableStateOf<MessageTemplate?>(null) }
    var sortedByTitle by remember { mutableStateOf(false) }

    val filtered = remember(templates, search, sortedByTitle) {
        val base = if (search.isBlank()) templates
        else templates.filter {
            it.title.contains(search, true) || it.body.contains(search, true)
        }
        if (sortedByTitle) base.sortedBy { it.title }
        else base.sortedByDescending { it.id }
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
                    "قالب‌های آماده",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Muted("${templates.size} قالب · قابل جستجو و ویرایش")
            }
            FilledIconButton(onClick = {
                editing = null
                title = ""
                body = ""
                error = ""
                editor = true
            }) {
                Icon(Icons.Outlined.Add, contentDescription = "قالب جدید")
            }
        }

        OutlinedTextField(
            search,
            { search = it },
            label = { Text("جستجوی عنوان یا متن قالب") },
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium
        )

        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Muted(if (filtered.isEmpty()) "نتیجه‌ای یافت نشد" else "${filtered.size} قالب")
            TextButton(onClick = { sortedByTitle = !sortedByTitle }) {
                Icon(Icons.Outlined.Sort, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (sortedByTitle) "بر اساس عنوان" else "جدیدترین")
            }
        }

        if (filtered.isEmpty()) {
            EmptyView(
                message = if (search.isBlank()) "هنوز قالبی ساخته نشده است"
                else "قالبی مطابق جستجو پیدا نشد",
                icon = Icons.Outlined.Description,
                actionLabel = if (search.isBlank()) "ساخت قالب جدید" else null,
                onAction = if (search.isBlank()) {
                    {
                        editing = null
                        title = ""
                        body = ""
                        error = ""
                        editor = true
                    }
                } else null
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(filtered, key = { it.id }) { t ->
                    CardPanel {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Outlined.Description,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                t.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Box {
                                IconButton(onClick = { expandedId = t.id }) {
                                    Icon(Icons.Outlined.MoreVert, contentDescription = "عملیات قالب")
                                }
                                DropdownMenu(
                                    expanded = expandedId == t.id,
                                    onDismissRequest = { expandedId = null }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("مشاهده متن") },
                                        onClick = { viewing = t; expandedId = null }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("ویرایش") },
                                        onClick = {
                                            editing = t
                                            title = t.title
                                            body = t.body
                                            error = ""
                                            editor = true
                                            expandedId = null
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("حذف") },
                                        onClick = { deleting = t; expandedId = null }
                                    )
                                }
                            }
                        }
                        Text(
                            t.body,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { viewing = t }) {
                                Text("مشاهده متن کامل")
                            }
                        }
                    }
                }
            }
        }
    }

    if (viewing != null) {
        AlertDialog(
            onDismissRequest = { viewing = null },
            title = { Text(viewing!!.title) },
            text = { Text(viewing!!.body) },
            confirmButton = {
                TextButton(onClick = { viewing = null }) { Text("بستن") }
            }
        )
    }

    if (editor) {
        AlertDialog(
            onDismissRequest = { editor = false },
            title = { Text(if (editing == null) "قالب جدید" else "ویرایش قالب") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        title,
                        { title = it },
                        label = { Text("عنوان قالب") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        body,
                        { body = it },
                        label = { Text("متن پیامک") },
                        minLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (error.isNotBlank()) {
                        Text(error, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        try {
                            store.saveTemplate(editing?.id ?: 0, title, body)
                            revision++
                            editor = false
                            error = ""
                        } catch (e: Exception) {
                            error = e.message ?: "خطای ذخیره"
                        }
                    },
                    enabled = title.isNotBlank() && body.isNotBlank()
                ) { Text("ذخیره") }
            },
            dismissButton = {
                TextButton(onClick = { editor = false }) { Text("انصراف") }
            }
        )
    }

    deleting?.let { t ->
        Confirm(
            "حذف قالب",
            "قالب «${t.title}» حذف شود؟ کمپین‌های قبلی تغییر نمی‌کنند.",
            "حذف",
            onYes = {
                store.removeTemplate(t.id)
                deleting = null
                revision++
            },
            onNo = { deleting = null }
        )
    }
}
