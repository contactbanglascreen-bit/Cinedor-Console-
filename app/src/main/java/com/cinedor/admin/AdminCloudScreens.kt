package com.cinedor.admin

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

private fun parseIso(s: String): Long = runCatching {
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        .parse(s.take(19))?.time ?: 0L
}.getOrDefault(0L)

/** Country code → flag emoji (BD → 🇧🇩). */
private fun flag(code: String): String {
    if (code.length != 2 || !code.all { it.isLetter() }) return "🌐"
    val base = 0x1F1E6 - 'A'.code
    return String(Character.toChars(base + code[0].uppercaseChar().code)) + String(Character.toChars(base + code[1].uppercaseChar().code))
}

/** Users section on the dashboard: totals, countries and phones. */
@Composable
fun UsersSection(cloud: CloudAdmin) {
    var devices by remember { mutableStateOf<List<DeviceInfo>?>(null) }
    var error by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        runCatching { cloud.devices() }.onSuccess { devices = it }.onFailure { error = it.message ?: "Couldn't load users" }
    }
    SectionTitle("Users", "Where Cinedor is used and on which phones")
    val list = devices
    if (list == null) {
        GlassCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
            if (error.isBlank()) Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AdminColors.Red, strokeWidth = 2.dp)
            } else {
                Text("Users can't be shown yet", color = AdminColors.Text, fontWeight = FontWeight.Bold)
                Text(error, color = AdminColors.Muted, fontSize = 12.5.sp)
            }
        }
        return
    }
    val now = System.currentTimeMillis()
    val active24 = list.count { now - parseIso(it.lastSeen) < 24 * 3600_000L }
    val active7 = list.count { now - parseIso(it.lastSeen) < 7 * 24 * 3600_000L }
    Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatTile(Icons.Rounded.Groups, "Total phones", list.size.toString(), Color(0xFFA78BFA), Modifier.weight(1f))
        StatTile(Icons.Rounded.Whatshot, "Active today", active24.toString(), AdminColors.Green, Modifier.weight(1f))
        StatTile(Icons.Rounded.Public, "This week", active7.toString(), Color(0xFF60A5FA), Modifier.weight(1f))
    }
    Spacer(Modifier.height(10.dp))
    if (list.isEmpty()) {
        GlassCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
            Text("No phones yet. Numbers appear after people open the new Cinedor version.", color = AdminColors.Muted, fontSize = 13.sp)
        }
        return
    }
    BarCard("🌍 Top countries", list.groupingBy { it.country.ifBlank { "??" } }.eachCount().entries.sortedByDescending { it.value }.take(8)
        .map { (cc, n) -> Triple(flag(cc) + "  " + (list.firstOrNull { it.country == cc }?.countryName?.ifBlank { cc } ?: cc), n, list.size) })
    Spacer(Modifier.height(10.dp))
    BarCard("📱 Phone brands", list.groupingBy { it.brand.ifBlank { "Unknown" } }.eachCount().entries.sortedByDescending { it.value }.take(6)
        .map { (b, n) -> Triple(b, n, list.size) })
    Spacer(Modifier.height(10.dp))
    BarCard("📲 Top models", list.groupingBy { (it.brand + " " + it.model).trim() }.eachCount().entries.sortedByDescending { it.value }.take(6)
        .map { (m, n) -> Triple(m, n, list.size) })
    Spacer(Modifier.height(10.dp))
    BarCard("🤖 Android versions", list.groupingBy { "Android " + it.android.ifBlank { "?" } }.eachCount().entries.sortedByDescending { it.value }.take(6)
        .map { (a, n) -> Triple(a, n, list.size) })
}

@Composable
private fun BarCard(title: String, rows: List<Triple<String, Int, Int>>) {
    GlassCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
        Text(title, color = AdminColors.Text, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
        Spacer(Modifier.height(8.dp))
        rows.forEach { (label, n, total) ->
            val frac = if (total > 0) n.toFloat() / total else 0f
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(label, color = AdminColors.Text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(0.45f))
                Box(Modifier.weight(0.4f).height(10.dp).clip(CircleShape).background(AdminColors.Field)) {
                    Box(Modifier.fillMaxWidth(frac.coerceIn(0.03f, 1f)).height(10.dp).clip(CircleShape).background(AdminColors.redBrush))
                }
                Text(
                    "$n · ${(frac * 100).toInt()}%",
                    color = AdminColors.Muted, fontSize = 11.5.sp,
                    modifier = Modifier.weight(0.15f).padding(start = 8.dp), maxLines = 1
                )
            }
        }
    }
}

/** Feedback inbox: read problems, reply and mark fixed (the user gets told). */
@Composable
fun FeedbackScreen(cloud: CloudAdmin, store: AdminStore, api: WorkerApi) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<FeedbackEntry>?>(null) }
    var error by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("open") }
    var reload by remember { mutableIntStateOf(0) }
    var replying by remember { mutableStateOf<FeedbackEntry?>(null) }
    var deleting by remember { mutableStateOf<FeedbackEntry?>(null) }

    LaunchedEffect(reload) {
        error = ""
        runCatching { cloud.feedback() }.onSuccess { items = it }.onFailure { error = it.message ?: "Couldn't load feedback" }
    }

    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(bottom = 30.dp)) {
        val all = items.orEmpty()
        val openCount = all.count { !it.isFixed }
        SectionTitle("Feedback inbox", "$openCount waiting · ${all.size - openCount} fixed")
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("🕒 Waiting ($openCount)", filter == "open") { filter = "open" }
            Chip("✅ Fixed", filter == "fixed") { filter = "fixed" }
            Chip("All", filter == "all") { filter = "all" }
        }
        Spacer(Modifier.height(8.dp))
        when {
            items == null && error.isBlank() -> Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AdminColors.Red, strokeWidth = 2.dp)
            }
            error.isNotBlank() -> GlassCard(Modifier.padding(16.dp).fillMaxWidth()) {
                Text("Feedback can't be shown yet", color = AdminColors.Text, fontWeight = FontWeight.Bold)
                Text(error, color = AdminColors.Muted, fontSize = 12.5.sp)
                Spacer(Modifier.height(10.dp))
                SecondaryButton("Try again", Modifier.fillMaxWidth()) { reload++ }
            }
            else -> {
                val shown = all.filter { filter == "all" || (filter == "fixed") == it.isFixed }
                if (shown.isEmpty()) {
                    GlassCard(Modifier.padding(16.dp).fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconBadge(Icons.Rounded.Inbox, AdminColors.Muted, 40.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(if (filter == "open") "No waiting feedback 🎉" else "Nothing here yet", color = AdminColors.Muted)
                        }
                    }
                }
                shown.forEach { f ->
                    GlassCard(Modifier.padding(horizontal = 16.dp, vertical = 5.dp).fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(f.category.ifBlank { "Feedback" }, color = AdminColors.Text, fontWeight = FontWeight.ExtraBold, fontSize = 14.5.sp, modifier = Modifier.weight(1f))
                            StatusChip(f.isFixed, onText = "Fixed", offText = "Waiting")
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(f.message, color = AdminColors.Text, fontSize = 14.sp)
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Phone, contentDescription = null, tint = AdminColors.Muted, modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "${flag(f.country)} ${f.device} · v${f.appVersion.removePrefix("1.0.")} · ${f.createdAt.take(10)}",
                                color = AdminColors.Muted, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (f.contact.isNotBlank()) Text("Contact: ${f.contact}", color = AdminColors.RedBright, fontSize = 12.sp)
                        if (f.reply.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(AdminColors.Field).padding(10.dp)) {
                                Text("Your reply", color = AdminColors.Green, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text(f.reply, color = AdminColors.Text, fontSize = 13.sp)
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!f.isFixed) {
                                PrimaryButton("Mark fixed", Modifier.weight(1f), icon = Icons.Rounded.CheckCircle) { replying = f }
                            } else {
                                SecondaryButton("Reopen", Modifier.weight(1f), icon = Icons.Rounded.Undo) {
                                    scope.launch {
                                        runCatching { cloud.setStatus(f.id, false, f.reply) }
                                            .onSuccess { reload++ }
                                            .onFailure { Toast.makeText(context, it.message, Toast.LENGTH_LONG).show() }
                                    }
                                }
                            }
                            Box(
                                Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).border(1.dp, AdminColors.Line, RoundedCornerShape(16.dp))
                                    .clickable { deleting = f },
                                contentAlignment = Alignment.Center
                            ) { Icon(Icons.Rounded.Delete, contentDescription = "Delete", tint = AdminColors.Muted) }
                        }
                    }
                }
            }
        }
    }

    replying?.let { f ->
        var reply by remember(f.id) { mutableStateOf("Fixed in the latest update — thanks for telling us! 🙏") }
        var busy by remember(f.id) { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { if (!busy) replying = null },
            icon = { Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = AdminColors.Green) },
            title = { Text("Mark as fixed") },
            text = {
                Column {
                    Text("The user will see this message and get a \"fixed\" notification.", color = AdminColors.Muted, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    AdminField("Reply to the user", reply, { reply = it.take(300) }, singleLine = false)
                }
            },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        runCatching { cloud.setStatus(f.id, true, reply.trim()) }
                            .onSuccess {
                                // Instant push to that phone when the notification server is set up.
                                if (store.workerUrl.isNotBlank() && f.uid.isNotBlank()) {
                                    runCatching { api.send("✅ Your problem is fixed", reply.trim(), "", "", topic = "u_" + f.uid) }
                                }
                                Toast.makeText(context, "Marked fixed ✓ — the user will be told", Toast.LENGTH_SHORT).show()
                                replying = null
                                reload++
                            }
                            .onFailure { Toast.makeText(context, it.message, Toast.LENGTH_LONG).show() }
                        busy = false
                    }
                }) { Text(if (busy) "Saving…" else "Mark fixed", color = AdminColors.Green, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { replying = null }) { Text("Cancel") } },
            containerColor = AdminColors.Card
        )
    }

    deleting?.let { f ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete this feedback?") },
            text = { Text(f.message.take(120)) },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    scope.launch {
                        runCatching { cloud.delete(f.id) }
                            .onSuccess { reload++ }
                            .onFailure { Toast.makeText(context, it.message, Toast.LENGTH_LONG).show() }
                    }
                }) { Text("Delete", color = AdminColors.RedBright) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
            containerColor = AdminColors.Card
        )
    }
}
