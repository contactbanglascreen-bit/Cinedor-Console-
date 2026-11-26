package com.cinedor.admin

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cable
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Keep admin screens out of screenshots / recent-apps previews.
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        val store = AdminStore(applicationContext)
        val auth = AdminAuth(store)
        val api = WorkerApi(store, auth)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = AdminColors.Red,
                    background = AdminColors.Bg,
                    surface = AdminColors.Card,
                    onPrimary = Color.White,
                    onBackground = AdminColors.Text,
                    onSurface = AdminColors.Text
                )
            ) {
                Box(Modifier.fillMaxSize().background(AdminColors.Bg)) {
                    var loggedIn by remember { mutableStateOf(auth.isLoggedIn) }
                    if (loggedIn) {
                        AdminHome(store, auth, api, onLogout = { auth.logout(); loggedIn = false })
                    } else {
                        LoginScreen(store, auth, onDone = { loggedIn = true })
                    }
                    AdminUpdatePrompt()
                }
            }
        }
    }
}

// ---------------------------------------------------------------- shared UI bits

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = AdminColors.Red,
    unfocusedBorderColor = AdminColors.Line,
    focusedContainerColor = AdminColors.Field,
    unfocusedContainerColor = AdminColors.Field,
    focusedLabelColor = AdminColors.RedBright,
    unfocusedLabelColor = AdminColors.Muted,
    cursorColor = AdminColors.Red,
    focusedTextColor = AdminColors.Text,
    unfocusedTextColor = AdminColors.Text
)

@Composable
private fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    singleLine: Boolean = true,
    password: Boolean = false,
    number: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = when {
                password -> KeyboardType.Password
                number -> KeyboardType.Number
                else -> KeyboardType.Text
            }
        ),
        colors = fieldColors(),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)
    )
}

@Composable
private fun RedButton(text: String, busy: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(52.dp)
            .clip(CircleShape)
            .background(AdminColors.redBrush)
            .clickable(enabled = !busy, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (busy) CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
        else Text(text, color = Color.White, fontWeight = FontWeight.Black, fontSize = 15.sp)
    }
}

@Composable
private fun Card(title: String? = null, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(AdminColors.Card)
            .border(1.dp, AdminColors.Line, RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        if (title != null) {
            Text(title, color = AdminColors.Text, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            Spacer(Modifier.height(6.dp))
        }
        content()
    }
}

@Composable
private fun Brand(subtitle: String = "Admin") {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            Image(painterResource(R.drawable.cinedor_logo), contentDescription = null, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text("CINEDOR", color = AdminColors.Text, fontWeight = FontWeight.Black, letterSpacing = 3.sp, fontSize = 17.sp)
            Text(subtitle, color = AdminColors.Muted, fontSize = 12.sp)
        }
    }
}

// ---------------------------------------------------------------- login

@Composable
private fun LoginScreen(store: AdminStore, auth: AdminAuth, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf(store.email) }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var showConn by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(Modifier.height(40.dp))
        Brand("Admin panel")
        Spacer(Modifier.height(28.dp))
        Text("Log in", color = AdminColors.Text, fontWeight = FontWeight.Black, fontSize = 26.sp)
        Text("Only the Cinedor admin account can sign in.", color = AdminColors.Muted, fontSize = 13.sp)
        Spacer(Modifier.height(16.dp))
        Field("Email", email, { email = it.trim() })
        Field("Password", password, { password = it }, password = true)
        if (error.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(error, color = Color(0xFFFF8A8F), fontSize = 13.sp)
        }
        Spacer(Modifier.height(14.dp))
        RedButton("Log in", busy = busy, modifier = Modifier.fillMaxWidth()) {
            error = ""
            busy = true
            scope.launch {
                runCatching { auth.login(email, password) }
                    .onSuccess { onDone() }
                    .onFailure { error = it.message ?: "Login failed" }
                busy = false
            }
        }
        Spacer(Modifier.height(10.dp))
        TextButton(onClick = { showConn = !showConn }) {
            Text(if (showConn) "Hide connection settings" else "Connection settings", color = AdminColors.Muted)
        }
        if (showConn) ConnectionFields(store)
    }
}

@Composable
private fun ConnectionFields(store: AdminStore) {
    val context = LocalContext.current
    var worker by remember { mutableStateOf(store.workerUrl) }
    var key by remember { mutableStateOf(store.apiKey) }
    Field("Worker address (https://…workers.dev)", worker, { worker = it })
    Field("Firebase Web API key", key, { key = it })
    Spacer(Modifier.height(8.dp))
    RedButton("Save", modifier = Modifier.fillMaxWidth()) {
        store.workerUrl = worker
        store.apiKey = key
        Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
    }
}

// ---------------------------------------------------------------- home with tabs

private enum class AdminTab(val label: String) { NOTIFY("Notify"), SETTINGS("App settings"), CONNECTION("Connection") }

@Composable
private fun AdminHome(store: AdminStore, auth: AdminAuth, api: WorkerApi, onLogout: () -> Unit) {
    var tab by remember { mutableStateOf(AdminTab.NOTIFY) }
    var confirmLogout by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        // Session check: if the saved login no longer works, go back to the login screen.
        runCatching { auth.checkSession() }.onFailure { if (it is AdminException) onLogout() }
    }
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Brand(store.email.ifBlank { "Admin" })
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { confirmLogout = true }) {
                Icon(Icons.Rounded.Logout, contentDescription = "Log out", tint = AdminColors.Muted)
            }
        }
        Box(Modifier.weight(1f)) {
            when (tab) {
                AdminTab.NOTIFY -> NotifyScreen(store, api)
                AdminTab.SETTINGS -> SettingsScreen(api)
                AdminTab.CONNECTION -> ConnectionScreen(store, api)
            }
        }
        NavigationBar(containerColor = AdminColors.Card, modifier = Modifier.navigationBarsPadding()) {
            AdminTab.entries.forEach { t ->
                NavigationBarItem(
                    selected = tab == t,
                    onClick = { tab = t },
                    icon = {
                        Icon(
                            when (t) {
                                AdminTab.NOTIFY -> Icons.Rounded.Notifications
                                AdminTab.SETTINGS -> Icons.Rounded.Tune
                                AdminTab.CONNECTION -> Icons.Rounded.Cable
                            },
                            contentDescription = null
                        )
                    },
                    label = { Text(t.label) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.White,
                        indicatorColor = AdminColors.Red,
                        unselectedIconColor = AdminColors.Muted,
                        selectedTextColor = AdminColors.Text,
                        unselectedTextColor = AdminColors.Muted
                    )
                )
            }
        }
    }
    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("Log out?") },
            confirmButton = { TextButton(onClick = { confirmLogout = false; onLogout() }) { Text("Log out", color = AdminColors.Red) } },
            dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("Cancel") } },
            containerColor = AdminColors.Card
        )
    }
}

// ---------------------------------------------------------------- notifications

private val templates = listOf(
    "🎬 New movie" to ("🎬 New movie on Cinedor" to "Watch it now in HD — free."),
    "📺 New episode" to ("📺 New episode is here" to "The latest episode just dropped. Tap to watch."),
    "🚀 Update" to ("🚀 Cinedor update" to "A new version is ready. Open the app to update."),
    "⚽ Live" to ("⚽ Live now" to "The match has started. Watch live on Cinedor.")
)

@Composable
private fun NotifyScreen(store: AdminStore, api: WorkerApi) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var image by remember { mutableStateOf("") }
    var link by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    var historyTick by remember { mutableIntStateOf(0) }
    val history = remember(historyTick) { runCatching { JSONArray(store.historyJson()) }.getOrDefault(JSONArray()) }

    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState())) {
        Card("Send a notification") {
            Text("Goes to every phone with Cinedor. Add a poster link for a big picture.", color = AdminColors.Muted, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                templates.forEach { (label, tpl) ->
                    Text(
                        label,
                        color = AdminColors.Text,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(AdminColors.Field)
                            .border(1.dp, AdminColors.Line, CircleShape)
                            .clickable { title = tpl.first; body = tpl.second }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Field("Title", title, { title = it.take(80) })
            Field("Message", body, { body = it.take(300) }, singleLine = false)
            Field("Poster image link (https://, optional)", image, { image = it.trim() })
            Field("Open in app: title to search (optional)", link, { link = it })
            Spacer(Modifier.height(10.dp))
            RedButton("Send to all phones", busy = busy, modifier = Modifier.fillMaxWidth()) {
                if (title.isBlank()) Toast.makeText(context, "Write a title first", Toast.LENGTH_SHORT).show()
                else confirm = true
            }
        }

        // Live preview like an Android notification
        Card("Preview") {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFFF2EEEF))
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(18.dp).clip(RoundedCornerShape(5.dp)).background(AdminColors.Red),
                        contentAlignment = Alignment.Center
                    ) { Text("C", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black) }
                    Spacer(Modifier.width(6.dp))
                    Text("Cinedor · now", color = Color(0xFF555555), fontSize = 12.sp)
                }
                Spacer(Modifier.height(6.dp))
                Text(title.ifBlank { "Title" }, color = Color(0xFF1B1B1B), fontWeight = FontWeight.ExtraBold)
                Text(body.ifBlank { "Message" }, color = Color(0xFF333333), fontSize = 14.sp)
                if (image.startsWith("https://")) {
                    Spacer(Modifier.height(8.dp))
                    AsyncImage(
                        model = image,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(12.dp))
                    )
                }
            }
        }

        Card("Sent from this phone") {
            if (history.length() == 0) Text("Nothing sent yet", color = AdminColors.Muted, fontSize = 13.sp)
            for (i in 0 until history.length()) {
                val h = history.optJSONObject(i) ?: continue
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (h.optString("image").isNotBlank()) {
                        AsyncImage(
                            model = h.optString("image"),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(width = 40.dp, height = 56.dp).clip(RoundedCornerShape(8.dp))
                        )
                        Spacer(Modifier.width(10.dp))
                    }
                    Column {
                        Text(h.optString("title"), color = AdminColors.Text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        if (h.optString("body").isNotBlank()) Text(h.optString("body"), color = AdminColors.Muted, fontSize = 12.sp, maxLines = 2)
                        Text(DateFormat.getDateTimeInstance().format(Date(h.optLong("at"))), color = AdminColors.Muted, fontSize = 11.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Send to everyone?") },
            text = { Text("\"$title\" will be sent to every Cinedor user.") },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    busy = true
                    scope.launch {
                        runCatching { api.send(title.trim(), body.trim(), image, link.trim()) }
                            .onSuccess {
                                val arr = runCatching { JSONArray(store.historyJson()) }.getOrDefault(JSONArray())
                                val next = JSONArray().put(
                                    JSONObject().put("title", title).put("body", body).put("image", image).put("at", System.currentTimeMillis())
                                )
                                for (i in 0 until minOf(arr.length(), 19)) next.put(arr.get(i))
                                store.saveHistory(next.toString())
                                historyTick++
                                Toast.makeText(context, "Sent to all phones ✓", Toast.LENGTH_SHORT).show()
                            }
                            .onFailure { Toast.makeText(context, it.message ?: "Failed", Toast.LENGTH_LONG).show() }
                        busy = false
                    }
                }) { Text("Send", color = AdminColors.Red, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
            containerColor = AdminColors.Card
        )
    }
}

// ---------------------------------------------------------------- app settings (console/app-config.json)

private enum class FieldType { SWITCH, TEXT, AREA, NUMBER, LIST }
private data class SField(val key: String, val type: FieldType, val label: String, val default: Boolean = false)
private data class Section(val key: String, val title: String, val fields: List<SField>)

private val SCHEMA = listOf(
    Section("premium", "💎 Premium popup", listOf(
        SField("enabled", FieldType.SWITCH, "Show Premium popup"), SField("title", FieldType.TEXT, "Title"),
        SField("message", FieldType.AREA, "Message"), SField("perks", FieldType.LIST, "Perks (comma separated)"),
        SField("price", FieldType.TEXT, "Price"), SField("buttonText", FieldType.TEXT, "Button text"),
        SField("buttonUrl", FieldType.TEXT, "Button link"), SField("showEveryHours", FieldType.NUMBER, "Show again every (hours)"),
        SField("id", FieldType.TEXT, "Popup ID (change to show again)")
    )),
    Section("announcement", "📢 Announcement", listOf(
        SField("enabled", FieldType.SWITCH, "Show announcement"), SField("title", FieldType.TEXT, "Title"),
        SField("message", FieldType.AREA, "Message"), SField("buttonText", FieldType.TEXT, "Button text"),
        SField("buttonUrl", FieldType.TEXT, "Button link"), SField("id", FieldType.TEXT, "Announcement ID")
    )),
    Section("community", "🎉 Join us popup", listOf(
        SField("enabled", FieldType.SWITCH, "Show Join us popup"), SField("title", FieldType.TEXT, "Title"),
        SField("message", FieldType.AREA, "Message"), SField("buttonText", FieldType.TEXT, "Button 1 text"),
        SField("buttonUrl", FieldType.TEXT, "Button 1 link (Telegram)"), SField("secondaryText", FieldType.TEXT, "Button 2 text"),
        SField("secondaryUrl", FieldType.TEXT, "Button 2 link (Facebook)"), SField("showEveryHours", FieldType.NUMBER, "Show again every (hours)"),
        SField("id", FieldType.TEXT, "Popup ID")
    )),
    Section("sharePrompt", "🍿 Share popup", listOf(
        SField("enabled", FieldType.SWITCH, "Ask viewers to share"), SField("afterMinutes", FieldType.NUMBER, "Show after watching (minutes)"),
        SField("title", FieldType.TEXT, "Title"), SField("message", FieldType.AREA, "Message (empty = automatic)"),
        SField("perks", FieldType.LIST, "Perks (max 3)"), SField("shareMessage", FieldType.AREA, "Text that gets shared"),
        SField("buttonUrl", FieldType.TEXT, "App download link"), SField("showEveryHours", FieldType.NUMBER, "Ask again every (hours)"),
        SField("id", FieldType.TEXT, "Popup ID")
    )),
    Section("audioDubs", "🎧 Audio & Dubs", listOf(
        SField("enabled", FieldType.SWITCH, "Show on title pages", default = true), SField("title", FieldType.TEXT, "Section title")
    )),
    Section("maintenance", "🛠️ Maintenance", listOf(
        SField("enabled", FieldType.SWITCH, "Maintenance mode (blocks the app)"), SField("message", FieldType.AREA, "Message")
    )),
    Section("", "⬆️ Force update", listOf(SField("minVersionCode", FieldType.NUMBER, "Force update below version (0 = off)")))
)

@Composable
private fun SettingsScreen(api: WorkerApi) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var config by remember { mutableStateOf<JSONObject?>(null) }
    var ref by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }
    var confirm by remember { mutableStateOf(false) }

    fun load() {
        loading = true
        error = ""
        scope.launch {
            runCatching { api.loadConfig() }
                .onSuccess { (r, text) -> ref = r; config = JSONObject(text); tick++ }
                .onFailure { error = it.message ?: "Couldn't load" }
            loading = false
        }
    }
    LaunchedEffect(Unit) { load() }

    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState())) {
        Card {
            Text("App settings", color = AdminColors.Text, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            Text(
                when {
                    loading -> "Loading…"
                    error.isNotBlank() -> error
                    else -> "Settings file on “$ref”. Phones get changes the next time the app opens."
                },
                color = if (error.isNotBlank()) Color(0xFFFF8A8F) else AdminColors.Muted,
                fontSize = 13.sp
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.height(52.dp).weight(1f).clip(CircleShape).border(1.dp, AdminColors.Line, CircleShape).clickable { load() },
                    contentAlignment = Alignment.Center
                ) { Text("Reload", color = AdminColors.Text, fontWeight = FontWeight.Bold) }
                RedButton("Publish", busy = saving, modifier = Modifier.weight(1.4f)) {
                    if (config != null) confirm = true
                }
            }
        }
        val cfg = config
        if (cfg != null) {
            androidx.compose.runtime.key(tick) {
                SCHEMA.forEach { section ->
                    val obj = if (section.key.isBlank()) cfg else (cfg.optJSONObject(section.key) ?: JSONObject().also { cfg.put(section.key, it) })
                    Card(section.title) {
                        section.fields.forEach { f -> ConfigField(obj, f) }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Publish settings?") },
            text = { Text("Every phone gets these settings the next time Cinedor opens.") },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    val text = config?.toString(2)
                    if (text != null) {
                    saving = true
                    scope.launch {
                        runCatching { api.saveConfig(text) }
                            .onSuccess { Toast.makeText(context, "Published ✓", Toast.LENGTH_SHORT).show() }
                            .onFailure { Toast.makeText(context, it.message ?: "Failed", Toast.LENGTH_LONG).show() }
                        saving = false
                    }
                    }
                }) { Text("Publish", color = AdminColors.Red, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
            containerColor = AdminColors.Card
        )
    }
}

@Composable
private fun ConfigField(obj: JSONObject, f: SField) {
    when (f.type) {
        FieldType.SWITCH -> {
            var checked by remember { mutableStateOf(obj.optBoolean(f.key, f.default)) }
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(f.label, color = AdminColors.Text, modifier = Modifier.weight(1f), fontSize = 14.sp)
                Switch(
                    checked = checked,
                    onCheckedChange = { checked = it; obj.put(f.key, it) },
                    colors = SwitchDefaults.colors(checkedTrackColor = AdminColors.Red, checkedThumbColor = Color.White)
                )
            }
        }
        FieldType.LIST -> {
            val initial = obj.optJSONArray(f.key)?.let { a -> List(a.length()) { a.optString(it) }.joinToString(", ") } ?: ""
            var text by remember { mutableStateOf(initial) }
            Field(f.label, text, {
                text = it
                obj.put(f.key, JSONArray(it.split(",").map { s -> s.trim() }.filter { s -> s.isNotBlank() }))
            })
        }
        FieldType.NUMBER -> {
            var text by remember { mutableStateOf(if (obj.has(f.key)) obj.optInt(f.key).toString() else "") }
            Field(f.label, text, {
                text = it.filter { c -> c.isDigit() }
                obj.put(f.key, text.toIntOrNull() ?: 0)
            }, number = true)
        }
        FieldType.TEXT, FieldType.AREA -> {
            var text by remember { mutableStateOf(obj.optString(f.key)) }
            Field(f.label, text, { text = it; obj.put(f.key, it) }, singleLine = f.type == FieldType.TEXT)
        }
    }
}

// ---------------------------------------------------------------- connection / about

@Composable
private fun ConnectionScreen(store: AdminStore, api: WorkerApi) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState())) {
        Card("Connection") {
            Text(
                "The Worker keeps the Firebase private key and GitHub token safe. This app never sees them.",
                color = AdminColors.Muted, fontSize = 13.sp
            )
            Spacer(Modifier.height(6.dp))
            ConnectionFields(store)
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier.fillMaxWidth().height(50.dp).clip(CircleShape).border(1.dp, AdminColors.Line, CircleShape).clickable {
                    scope.launch {
                        val ok = api.health()
                        Toast.makeText(context, if (ok) "Worker is online ✓" else "Can't reach the worker", Toast.LENGTH_SHORT).show()
                    }
                },
                contentAlignment = Alignment.Center
            ) { Text("Test worker", color = AdminColors.Text, fontWeight = FontWeight.Bold) }
        }
        Card("About") {
            Text("Cinedor Admin ${AdminUpdater.currentVersionName}", color = AdminColors.Text, fontWeight = FontWeight.Bold)
            Text("Updates arrive automatically when a new build is published.", color = AdminColors.Muted, fontSize = 13.sp)
        }
    }
}
