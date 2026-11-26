package com.cinedor.admin

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private fun copy(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("Cinedor", text))
    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
}

// =============================================================== Team (admin only)

@Composable
fun TeamSection(team: TeamAdmin) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var list by remember { mutableStateOf<List<StaffMember>?>(null) }
    var error by remember { mutableStateOf("") }
    var reload by remember { mutableIntStateOf(0) }
    var adding by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<StaffMember?>(null) }
    LaunchedEffect(reload) {
        error = ""
        runCatching { team.staff() }.onSuccess { list = it }.onFailure { error = it.message ?: "Couldn't load the team" }
    }

    SectionTitle("Team", "Moderators can sign in to this app with limited access")
    GlassCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Rounded.AdminPanelSettings, Color(0xFFFF4D5A), 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("You · Admin", color = AdminColors.Text, fontWeight = FontWeight.Bold)
                Text("Full access", color = AdminColors.Muted, fontSize = 12.sp)
            }
        }
        val l = list
        when {
            error.isNotBlank() -> Text(error, color = AdminColors.Muted, fontSize = 12.5.sp, modifier = Modifier.padding(top = 10.dp))
            l == null -> Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AdminColors.Red, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
            }
            else -> l.forEach { m ->
                var active by remember(m.uid) { mutableStateOf(m.active) }
                var perms by remember(m.uid) { mutableStateOf(m.perms) }
                Spacer(Modifier.height(12.dp))
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(AdminColors.Field)
                        .border(1.dp, AdminColors.Line, RoundedCornerShape(16.dp)).padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(Icons.Rounded.Group, Color(0xFF60A5FA), 36.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(m.name.ifBlank { "Moderator" }, color = AdminColors.Text, fontWeight = FontWeight.Bold)
                            Text(m.email, color = AdminColors.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        StatusChip(active, "Active", "Off")
                    }
                    STAFF_PERMS.forEach { (key, label) ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable {
                            perms = if (key in perms) perms - key else perms + key
                            scope.launch { runCatching { team.update(m.copy(perms = perms, active = active)) }.onFailure { Toast.makeText(context, it.message, Toast.LENGTH_LONG).show() } }
                        }) {
                            Checkbox(
                                checked = key in perms, onCheckedChange = null,
                                colors = CheckboxDefaults.colors(checkedColor = AdminColors.Red, uncheckedColor = AdminColors.Muted)
                            )
                            Text(label, color = AdminColors.Text, fontSize = 13.5.sp)
                        }
                    }
                    ToggleRow("Can sign in", null, active) {
                        active = it
                        scope.launch { runCatching { team.update(m.copy(perms = perms, active = it)) }.onFailure { e -> Toast.makeText(context, e.message, Toast.LENGTH_LONG).show() } }
                    }
                    SecondaryButton("Remove from team", Modifier.fillMaxWidth(), icon = Icons.Rounded.Delete) { removing = m }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        PrimaryButton("Add moderator", Modifier.fillMaxWidth(), icon = Icons.Rounded.PersonAdd) { adding = true }
    }

    if (adding) {
        var name by remember { mutableStateOf("") }
        var email by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var perms by remember { mutableStateOf(setOf("feedback", "notify")) }
        var busy by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { if (!busy) adding = false },
            title = { Text("Add moderator") },
            text = {
                Column {
                    Text("This creates their login. Share the email and password with them.", color = AdminColors.Muted, fontSize = 12.5.sp)
                    AdminField("Name", name, { name = it })
                    AdminField("Email", email, { email = it.trim() })
                    AdminField("Password (min 6)", password, { password = it })
                    Text("They can:", color = AdminColors.Text, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
                    STAFF_PERMS.forEach { (key, label) ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable {
                            perms = if (key in perms) perms - key else perms + key
                        }) {
                            Checkbox(checked = key in perms, onCheckedChange = null,
                                colors = CheckboxDefaults.colors(checkedColor = AdminColors.Red, uncheckedColor = AdminColors.Muted))
                            Text(label, color = AdminColors.Text, fontSize = 13.5.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        runCatching { team.addModerator(name, email, password, perms) }
                            .onSuccess {
                                copy(context, "Cinedor Admin login\nEmail: ${email}\nPassword: ${password}")
                                Toast.makeText(context, "Moderator added ✓ — login copied", Toast.LENGTH_LONG).show()
                                adding = false
                                reload++
                            }
                            .onFailure { Toast.makeText(context, it.message, Toast.LENGTH_LONG).show() }
                        busy = false
                    }
                }) { Text(if (busy) "Adding…" else "Add", color = AdminColors.RedBright, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { adding = false }) { Text("Cancel") } },
            containerColor = AdminColors.Card
        )
    }
    removing?.let { m ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Remove ${m.name.ifBlank { m.email }}?") },
            text = { Text("They won't be able to open Cinedor Admin anymore.") },
            confirmButton = {
                TextButton(onClick = {
                    removing = null
                    scope.launch {
                        runCatching { team.remove(m.uid) }.onSuccess { reload++ }
                            .onFailure { Toast.makeText(context, it.message, Toast.LENGTH_LONG).show() }
                    }
                }) { Text("Remove", color = AdminColors.RedBright) }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancel") } },
            containerColor = AdminColors.Card
        )
    }
}

// =============================================================== Ads

/** Ads stored in the app settings ("ads" section). Banner on Home, full-screen before videos. */
@Composable
fun AdsEditor(adsObj: JSONObject, onChanged: () -> Unit) {
    var enabled by remember { mutableStateOf(adsObj.optBoolean("enabled", false)) }
    var every by remember { mutableStateOf(adsObj.optInt("fullscreenEveryMinutes", 10).toString()) }
    var skip by remember { mutableStateOf(adsObj.optInt("skipAfterSeconds", 5).toString()) }
    var tick by remember { mutableIntStateOf(0) }
    val items = adsObj.optJSONArray("items") ?: JSONArray().also { adsObj.put("items", it) }

    GlassCard(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Rounded.Campaign, Color(0xFF22C55E), 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Ads", color = AdminColors.Text, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                Text("Banners on Home · full-screen before videos · Premium users see none", color = AdminColors.Muted, fontSize = 12.sp)
            }
            StatusChip(enabled)
        }
        ToggleRow("Show ads in the app", null, enabled) { enabled = it; adsObj.put("enabled", it); onChanged() }
        AdminField("Full-screen ad at most every (minutes)", every, {
            every = it.filter { c -> c.isDigit() }; adsObj.put("fullscreenEveryMinutes", every.toIntOrNull() ?: 10); onChanged()
        }, number = true)
        AdminField("Skip button after (seconds)", skip, {
            skip = it.filter { c -> c.isDigit() }; adsObj.put("skipAfterSeconds", skip.toIntOrNull() ?: 5); onChanged()
        }, number = true)

        androidx.compose.runtime.key(tick) {
            for (i in 0 until items.length()) {
                val ad = items.optJSONObject(i) ?: continue
                var type by remember { mutableStateOf(ad.optString("type", "banner")) }
                var title by remember { mutableStateOf(ad.optString("title")) }
                var image by remember { mutableStateOf(ad.optString("image")) }
                var link by remember { mutableStateOf(ad.optString("link")) }
                var on by remember { mutableStateOf(ad.optBoolean("enabled", true)) }
                Spacer(Modifier.height(10.dp))
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(AdminColors.Field)
                        .border(1.dp, AdminColors.Line, RoundedCornerShape(16.dp)).padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Ad ${i + 1}", color = AdminColors.Text, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Box(
                            Modifier.size(36.dp).clip(CircleShape).clickable {
                                items.remove(i); onChanged(); tick++
                            },
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Rounded.Delete, contentDescription = "Delete ad", tint = AdminColors.Muted) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip("Banner (Home)", type == "banner") { type = "banner"; ad.put("type", "banner"); onChanged() }
                        Chip("Full-screen", type == "fullscreen") { type = "fullscreen"; ad.put("type", "fullscreen"); onChanged() }
                    }
                    AdminField("Name (only for you)", title, { title = it; ad.put("title", it); onChanged() })
                    AdminField(if (type == "banner") "Image link (wide, about 3:1)" else "Image link (tall, phone size)", image, {
                        image = it.trim(); ad.put("image", image); onChanged()
                    })
                    if (image.startsWith("http")) {
                        AsyncImage(
                            model = image, contentDescription = null, contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().aspectRatio(if (type == "banner") 3.2f else 1.2f).clip(RoundedCornerShape(12.dp))
                        )
                    }
                    AdminField("When tapped, open (website or WhatsApp link)", link, { link = it.trim(); ad.put("link", link); onChanged() })
                    ToggleRow("Running", null, on) { on = it; ad.put("enabled", it); onChanged() }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        SecondaryButton("Add an ad", Modifier.fillMaxWidth(), icon = Icons.Rounded.Add) {
            items.put(JSONObject().put("id", "ad" + System.currentTimeMillis()).put("type", "banner").put("title", "").put("image", "").put("link", "").put("enabled", true))
            onChanged(); tick++
        }
    }
}

// =============================================================== Premium codes

@Composable
fun PremiumCodesSection(team: TeamAdmin) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var days by remember { mutableIntStateOf(30) }
    var count by remember { mutableIntStateOf(1) }
    var note by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var made by remember { mutableStateOf<List<String>>(emptyList()) }
    var list by remember { mutableStateOf<List<PremiumCode>?>(null) }
    var error by remember { mutableStateOf("") }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(reload) {
        runCatching { team.codes() }.onSuccess { list = it; error = "" }.onFailure { error = it.message ?: "" }
    }

    GlassCard(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Rounded.WorkspacePremium, Color(0xFFF5B83D), 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Premium codes", color = AdminColors.Text, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                Text("Sell or gift Premium: the user enters the code in Me › Go Premium", color = AdminColors.Muted, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("How long", color = AdminColors.Muted, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 6.dp)) {
            listOf(7 to "7 days", 30 to "1 month", 90 to "3 months", 365 to "1 year").forEach { (d, l) ->
                Chip(l, days == d) { days = d }
            }
        }
        Text("How many codes", color = AdminColors.Muted, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 6.dp)) {
            listOf(1, 5, 10).forEach { n -> Chip("$n", count == n) { count = n } }
        }
        AdminField("Note (customer name, payment…)", note, { note = it })
        Spacer(Modifier.height(8.dp))
        PrimaryButton("Make codes", Modifier.fillMaxWidth(), busy = busy, icon = Icons.Rounded.Add) {
            busy = true
            scope.launch {
                runCatching { team.makeCodes(days, count, note.trim()) }
                    .onSuccess { made = it; reload++ }
                    .onFailure { Toast.makeText(context, it.message, Toast.LENGTH_LONG).show() }
                busy = false
            }
        }
        if (made.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFF2B1B05)).padding(12.dp)) {
                Text("New codes · $days days", color = Color(0xFFF5C451), fontWeight = FontWeight.Bold)
                made.forEach { c -> Text(c, color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp, modifier = Modifier.padding(vertical = 2.dp)) }
                Spacer(Modifier.height(8.dp))
                val text = "Your Cinedor Premium code (${days} days):\n" + made.joinToString("\n") + "\n\nOpen Cinedor › Me › Go Premium › enter the code."
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryButton("Copy", Modifier.weight(1f), icon = Icons.Rounded.ContentCopy) { copy(context, text) }
                    SecondaryButton("Share", Modifier.weight(1f), icon = Icons.Rounded.Share) {
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Send code"))
                    }
                }
            }
        }
        val l = list
        if (error.isNotBlank()) Text(error, color = AdminColors.Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        if (!l.isNullOrEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("Recent codes", color = AdminColors.Text, fontWeight = FontWeight.Bold)
            l.take(25).forEach { c ->
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).clickable { copy(context, c.code) }) {
                        Text(c.code, color = AdminColors.Text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("${c.days} days" + (if (c.note.isNotBlank()) " · ${c.note}" else ""), color = AdminColors.Muted, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    StatusChip(!c.used, "Unused", "Used")
                    Box(
                        Modifier.size(34.dp).clip(CircleShape).clickable {
                            scope.launch { runCatching { team.deleteCode(c.code) }.onSuccess { reload++ } }
                        },
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Rounded.Delete, contentDescription = "Delete code", tint = AdminColors.Muted, modifier = Modifier.size(18.dp)) }
                }
            }
        }
    }
}
