package com.cinedor.admin

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SpaceDashboard as FSpaceDashboard
import androidx.compose.material.icons.outlined.SpaceDashboard as OSpaceDashboard
import androidx.compose.material.icons.filled.NotificationsActive as FNotificationsActive
import androidx.compose.material.icons.outlined.NotificationsActive as ONotificationsActive
import androidx.compose.material.icons.filled.Forum as FForum
import androidx.compose.material.icons.outlined.Forum as OForum
import androidx.compose.material.icons.filled.AutoAwesomeMosaic as FAutoAwesomeMosaic
import androidx.compose.material.icons.outlined.AutoAwesomeMosaic as OAutoAwesomeMosaic
import androidx.compose.material.icons.filled.Tune as FTune
import androidx.compose.material.icons.outlined.Tune as OTune
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Feedback
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.DateFormat
import java.util.Calendar
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
        val repo = ConfigRepo(store, api, auth)
        val cloud = CloudAdmin(auth)
        val team = TeamAdmin(store, auth)
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
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(listOf(Color(0xFF1A0B0E), AdminColors.Bg, AdminColors.Bg)))
                ) {
                    var loggedIn by remember { mutableStateOf(auth.isLoggedIn) }
                    if (loggedIn) {
                        AdminHome(store, auth, api, repo, cloud, team, onLogout = { auth.logout(); loggedIn = false })
                    } else {
                        LoginScreen(store, auth, onDone = { loggedIn = true })
                    }
                    AdminUpdatePrompt()
                }
            }
        }
    }
}

// =============================================================== login

@Composable
private fun LogoMark(size: Int = 44) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape((size / 3.4f).dp))
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        Image(painterResource(R.drawable.cinedor_logo), contentDescription = null, modifier = Modifier.size((size * 0.74f).dp))
    }
}

@Composable
private fun LoginScreen(store: AdminStore, auth: AdminAuth, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf(store.email) }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(56.dp))
        Box(contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(AdminColors.Red.copy(alpha = 0.45f), Color.Transparent)))
            )
            LogoMark(78)
        }
        Spacer(Modifier.height(18.dp))
        Text("CINEDOR", color = AdminColors.Text, fontWeight = FontWeight.Black, letterSpacing = 6.sp, fontSize = 24.sp)
        Text("Admin Console", color = AdminColors.RedBright, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(Modifier.height(30.dp))
        GlassCard(Modifier.fillMaxWidth(), padding = 20.dp) {
            Text("Welcome back", color = AdminColors.Text, fontWeight = FontWeight.Black, fontSize = 22.sp)
            Text("Sign in with the Cinedor admin account", color = AdminColors.Muted, fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
            AdminField("Email", email, { email = it.trim() }, leading = Icons.Rounded.Email)
            AdminField("Password", password, { password = it }, password = true, leading = Icons.Rounded.Lock)
            if (error.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(error, color = Color(0xFFFF8A8F), fontSize = 13.sp)
            }
            Spacer(Modifier.height(14.dp))
            PrimaryButton("Sign in", Modifier.fillMaxWidth(), busy = busy) {
                error = ""
                busy = true
                scope.launch {
                    runCatching { auth.login(email, password) }
                        .onSuccess { onDone() }
                        .onFailure { error = it.message ?: "Login failed" }
                    busy = false
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Text("🔒 Only the Cinedor owner account can sign in", color = AdminColors.Muted, fontSize = 12.sp)
    }
}

@Composable
private fun ConnectionFields(store: AdminStore) {
    val context = LocalContext.current
    var worker by remember { mutableStateOf(store.workerUrl) }
    var key by remember { mutableStateOf(store.apiKey) }
    var token by remember { mutableStateOf(store.githubToken) }
    AdminField("Worker address (optional, for notifications)", worker, { worker = it })
    AdminField("GitHub token (publish settings without Worker)", token, { token = it.trim() }, password = true, leading = Icons.Rounded.Key)
    AdminField("Firebase Web API key", key, { key = it })
    Spacer(Modifier.height(8.dp))
    PrimaryButton("Save connection", Modifier.fillMaxWidth()) {
        store.workerUrl = worker
        store.apiKey = key
        store.githubToken = token
        Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
    }
}

// =============================================================== home shell

private enum class AdminTab(val label: String, val icon: ImageVector, val outline: ImageVector) {
    DASHBOARD("Home", Icons.Filled.FSpaceDashboard, Icons.Outlined.OSpaceDashboard),
    NOTIFY("Notify", Icons.Filled.FNotificationsActive, Icons.Outlined.ONotificationsActive),
    FEEDBACK("Feedback", Icons.Filled.FForum, Icons.Outlined.OForum),
    POPUPS("Popups", Icons.Filled.FAutoAwesomeMosaic, Icons.Outlined.OAutoAwesomeMosaic),
    SYSTEM("System", Icons.Filled.FTune, Icons.Outlined.OTune)
}

/** Floating glass bottom bar: the active tab grows into a red pill with a filled icon and its name. */
@Composable
private fun AdminBottomBar(current: AdminTab, tabs: List<AdminTab>, onSelect: (AdminTab) -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF221A1D), Color(0xFF161113))))
                .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.12f), Color.White.copy(alpha = 0.03f))), RoundedCornerShape(26.dp))
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEach { t ->
                val sel = t == current
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(18.dp))
                        .clickable { onSelect(t) }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (sel) AdminColors.redBrush else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)))
                            .animateContentSize()
                            .padding(horizontal = if (sel) 18.dp else 10.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (sel) t.icon else t.outline,
                            contentDescription = t.label,
                            tint = if (sel) Color.White else AdminColors.Muted,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(
                        t.label,
                        color = if (sel) Color.White else AdminColors.Muted,
                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/** Shared state for the settings file so every tab edits the same copy. */
private class ConfigState {
    var config by mutableStateOf<JSONObject?>(null)
    var ref by mutableStateOf("")
    var error by mutableStateOf("")
    var loading by mutableStateOf(true)
    var saving by mutableStateOf(false)
    var dirty by mutableStateOf(false)
    var tick by mutableIntStateOf(0)

    fun section(key: String): JSONObject {
        val c = config ?: JSONObject()
        if (key.isBlank()) return c
        return c.optJSONObject(key) ?: JSONObject().also { c.put(key, it) }
    }

    fun changed() { dirty = true }
    fun enabledCount(): Int = listOf("spotlight", "premium", "announcement", "community", "sharePrompt")
        .count { config?.optJSONObject(it)?.optBoolean("enabled") == true }
}

@Composable
private fun AdminHome(store: AdminStore, auth: AdminAuth, api: WorkerApi, repo: ConfigRepo, cloud: CloudAdmin, team: TeamAdmin, onLogout: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(AdminTab.DASHBOARD) }
    var confirmLogout by remember { mutableStateOf(false) }
    var confirmPublish by remember { mutableStateOf(false) }
    val cs = remember { ConfigState() }

    fun load() {
        cs.loading = true
        cs.error = ""
        scope.launch {
            runCatching { repo.load() }
                .onSuccess { (r, text) -> cs.ref = r; cs.config = JSONObject(text); cs.dirty = false; cs.tick++ }
                .onFailure { cs.error = it.message ?: "Couldn't load settings" }
            cs.loading = false
        }
    }
    LaunchedEffect(Unit) {
        load()
        runCatching { auth.checkSession() }.onFailure { if (it is AdminException) onLogout() }
    }

    Column(Modifier.fillMaxSize()) {
        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(start = 18.dp, end = 8.dp, top = 8.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LogoMark(38)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(tab.label, color = AdminColors.Text, fontWeight = FontWeight.Black, fontSize = 19.sp)
                Text(if (store.isAdmin) "Cinedor Admin" else "Moderator · " + store.displayName.ifBlank { store.email }, color = AdminColors.Muted, fontSize = 11.5.sp)
            }
            IconButton(onClick = { load() }) { Icon(Icons.Rounded.Refresh, contentDescription = "Reload", tint = AdminColors.Muted) }
            IconButton(onClick = { confirmLogout = true }) { Icon(Icons.Rounded.Logout, contentDescription = "Log out", tint = AdminColors.Muted) }
        }

        Box(Modifier.weight(1f)) {
            when (tab) {
                AdminTab.DASHBOARD -> DashboardScreen(store, api, cs, cloud, onGo = { tab = it })
                AdminTab.FEEDBACK -> FeedbackScreen(cloud, store, api)
                AdminTab.NOTIFY -> NotifyScreen(store, api, cloud, cs, repo)
                AdminTab.POPUPS -> PopupsScreen(cs, store, team)
                AdminTab.SYSTEM -> SystemScreen(store, api, cs, team, onLogout = { confirmLogout = true })
            }

            // Floating "publish changes" bar whenever settings were edited
            SlideUp(visible = cs.dirty, modifier = Modifier.align(Alignment.BottomCenter)) {
                Row(
                    Modifier
                        .padding(12.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF241A1D))
                        .border(1.dp, AdminColors.Red.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                        .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Unsaved changes", color = AdminColors.Text, fontWeight = FontWeight.Bold)
                        Text("Publish to update every phone", color = AdminColors.Muted, fontSize = 12.sp)
                    }
                    PrimaryButton("Publish", Modifier.width(130.dp), busy = cs.saving, icon = Icons.Rounded.CloudUpload) {
                        confirmPublish = true
                    }
                }
            }
        }

        val tabs = AdminTab.entries.filter {
            when (it) {
                AdminTab.DASHBOARD -> true
                AdminTab.NOTIFY -> store.can("notify")
                AdminTab.FEEDBACK -> store.can("feedback")
                AdminTab.POPUPS -> store.can("popups") || store.can("ads") || store.can("premium")
                AdminTab.SYSTEM -> store.isAdmin
            }
        }
        AdminBottomBar(tab, tabs) { tab = it }
    }

    if (confirmPublish) {
        AlertDialog(
            onDismissRequest = { confirmPublish = false },
            icon = { Icon(Icons.Rounded.CloudUpload, contentDescription = null, tint = AdminColors.RedBright) },
            title = { Text("Publish settings?") },
            text = { Text("Every phone gets these changes the next time Cinedor opens.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmPublish = false
                    val text = cs.config?.toString(2)
                    if (text != null) {
                        cs.saving = true
                        scope.launch {
                            runCatching { repo.save(text) }
                                .onSuccess { cs.dirty = false; Toast.makeText(context, "Published ✓", Toast.LENGTH_SHORT).show() }
                                .onFailure { Toast.makeText(context, it.message ?: "Failed", Toast.LENGTH_LONG).show() }
                            cs.saving = false
                        }
                    }
                }) { Text("Publish", color = AdminColors.RedBright, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { confirmPublish = false }) { Text("Cancel") } },
            containerColor = AdminColors.Card
        )
    }
    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("Log out?") },
            confirmButton = { TextButton(onClick = { confirmLogout = false; onLogout() }) { Text("Log out", color = AdminColors.RedBright) } },
            dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("Cancel") } },
            containerColor = AdminColors.Card
        )
    }
}

/** Slide-up animation called outside any Row/Column scope. */
@Composable
private fun SlideUp(visible: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = modifier
    ) { content() }
}

// =============================================================== dashboard

@Composable
private fun DashboardScreen(store: AdminStore, api: WorkerApi, cs: ConfigState, cloud: CloudAdmin, onGo: (AdminTab) -> Unit) {
    var release by remember { mutableStateOf<ReleaseInfo?>(null) }
    var workerOnline by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(Unit) {
        release = CinedorReleases.latest()
        workerOnline = if (store.workerUrl.isBlank()) null else api.health()
    }
    val history = remember { runCatching { JSONArray(store.historyJson()) }.getOrDefault(JSONArray()) }
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greet = when (hour) { in 5..11 -> "Good morning"; in 12..16 -> "Good afternoon"; else -> "Good evening" }
    val maintenance = cs.config?.optJSONObject("maintenance")?.optBoolean("enabled") == true

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 90.dp)) {
        // Hero
        Box(
            Modifier
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(Brush.linearGradient(listOf(Color(0xFFFF3B45), Color(0xFFB0101A), Color(0xFF4A060B))))
                .padding(20.dp)
        ) {
            Column {
                Text(greet + " 👋", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
                Text(store.email.substringBefore('@').ifBlank { "Admin" }, color = Color.White, fontWeight = FontWeight.Black, fontSize = 24.sp)
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusChip(!maintenance, onText = "App is live", offText = "Maintenance mode")
                    Spacer(Modifier.width(8.dp))
                    Text(DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date()), color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White)
                            .clickable { onGo(AdminTab.NOTIFY) }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Send, contentDescription = null, tint = AdminColors.Red, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Send notification", color = AdminColors.Red, fontWeight = FontWeight.Black, fontSize = 13.sp)
                    }
                }
            }
        }

        // Stats
        SectionTitle("Overview")
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(Icons.Rounded.RocketLaunch, "Latest app build", release?.name?.substringAfterLast(' ') ?: "—", AdminColors.RedBright, Modifier.weight(1f))
            StatTile(Icons.Rounded.Download, "APK downloads", release?.downloads?.toString() ?: "—", Color(0xFF60A5FA), Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(Icons.Rounded.Widgets, "Active popups", if (cs.config == null) "—" else cs.enabledCount().toString(), Color(0xFFF5C451), Modifier.weight(1f))
            StatTile(Icons.Rounded.Notifications, "Notifications sent", history.length().toString(), AdminColors.Green, Modifier.weight(1f))
        }

        // Users: countries & phones
        if (store.can("users")) UsersSection(cloud)

        // Quick actions
        SectionTitle("Quick actions")
        val actions = listOf(
            Triple(Icons.Rounded.NotificationsActive, "Notify", AdminTab.NOTIFY),
            Triple(Icons.Rounded.WorkspacePremium, "Premium", AdminTab.POPUPS),
            Triple(Icons.Rounded.Groups, "Join us", AdminTab.POPUPS),
            Triple(Icons.Rounded.Share, "App share", AdminTab.POPUPS),
            Triple(Icons.Rounded.Feedback, "Feedback", AdminTab.FEEDBACK),
            Triple(Icons.Rounded.Build, "Maintenance", AdminTab.SYSTEM),
            Triple(Icons.Rounded.SystemUpdate, "Force update", AdminTab.SYSTEM),
            Triple(Icons.Rounded.Settings, "Settings", AdminTab.SYSTEM)
        ).filter { (_, label, target) ->
            when (target) {
                AdminTab.NOTIFY -> store.can("notify")
                AdminTab.FEEDBACK -> store.can("feedback")
                AdminTab.POPUPS -> if (label == "Premium") store.can("premium") || store.can("popups") else store.can("popups")
                AdminTab.SYSTEM -> store.isAdmin
                AdminTab.DASHBOARD -> true
            }
        }
        if (actions.isEmpty()) SectionTitle("", "")
        actions.chunked(3).forEach { row ->
            Row(Modifier.padding(horizontal = 16.dp, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (icon, label, target) ->
                    GlassCard(Modifier.weight(1f), padding = 14.dp, onClick = { onGo(target) }) {
                        val tint = when (label) {
                            "Notify" -> Color(0xFFFF4D5A)
                            "Premium" -> Color(0xFFF5B83D)
                            "Join us" -> Color(0xFF2AABEE)
                            "App share" -> Color(0xFF22C55E)
                            "Feedback" -> Color(0xFFA78BFA)
                            "Maintenance" -> Color(0xFFFB923C)
                            "Force update" -> Color(0xFF60A5FA)
                            else -> Color(0xFF94A3B8)
                        }
                        IconBadge(icon, tint, 42.dp)
                        Spacer(Modifier.height(10.dp))
                        Text(label, color = AdminColors.Text, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                    }
                }
            }
        }

        // System status
        SectionTitle("System status")
        GlassCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
            StatusLine(Icons.Rounded.CheckCircle, "App settings", if (cs.config != null) "Saved in Firebase · ready" else cs.error.ifBlank { "Loading…" }, cs.config != null)
            StatusLine(
                if (workerOnline == true) Icons.Rounded.CloudDone else Icons.Rounded.Notifications,
                "Push notifications",
                when (workerOnline) {
                    true -> "Send from this app ✓"
                    false -> "Server not reachable · use Firebase Console"
                    null -> "Send from Firebase Console (topic: all)"
                },
                workerOnline == true
            )
        }

        if (history.length() > 0) {
            SectionTitle("Recent notifications")
            GlassCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                for (i in 0 until minOf(history.length(), 4)) {
                    val h = history.optJSONObject(i) ?: continue
                    HistoryRow(h)
                }
            }
        }
    }
}

@Composable
private fun StatusLine(icon: ImageVector, title: String, value: String, ok: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBadge(icon, if (ok) AdminColors.Green else AdminColors.Muted, 34.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = AdminColors.Text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(value, color = if (ok) AdminColors.Green else AdminColors.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun HistoryRow(h: JSONObject) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (h.optString("image").isNotBlank()) {
            AsyncImage(
                model = h.optString("image"),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(width = 54.dp, height = 40.dp).clip(RoundedCornerShape(10.dp))
            )
        } else {
            IconBadge(Icons.Rounded.Notifications, AdminColors.RedBright, 40.dp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(h.optString("title"), color = AdminColors.Text, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(h.optLong("at"))), color = AdminColors.Muted, fontSize = 11.5.sp)
        }
    }
}

// =============================================================== notifications

@Composable
private fun NotifyScreen(store: AdminStore, api: WorkerApi, cloud: CloudAdmin, cs: ConfigState, repo: ConfigRepo) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var feed by remember { mutableStateOf("trending") }
    var popupConfirm by remember { mutableStateOf(false) }
    var popupBusy by remember { mutableStateOf(false) }
    var posters by remember { mutableStateOf<List<PosterItem>>(emptyList()) }
    var loadingPosters by remember { mutableStateOf(true) }
    var selected by remember { mutableStateOf<PosterItem?>(null) }
    var wide by remember { mutableStateOf(true) }
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var image by remember { mutableStateOf("") }
    var link by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    var noServer by remember { mutableStateOf(false) }
    var historyTick by remember { mutableIntStateOf(0) }
    val history = remember(historyTick) { runCatching { JSONArray(store.historyJson()) }.getOrDefault(JSONArray()) }

    // Titles from the Cinedor app itself (falls back to TMDB only if the app's server gives nothing).
    LaunchedEffect(query, feed) {
        loadingPosters = true
        posters = if (query.isBlank()) {
            val list = if (feed == "new") CinedorCatalog.latest() else CinedorCatalog.trending()
            list.ifEmpty { PosterSource.trending() }
        } else {
            delay(400)
            CinedorCatalog.search(query).ifEmpty { PosterSource.search(query) }
        }
        loadingPosters = false
    }

    fun pick(p: PosterItem) {
        selected = p
        title = (if (p.type == "Series") "📺 " else "🎬 ") + p.title + (if (p.year.isNotBlank()) " (${p.year})" else "")
        body = p.overview.take(140).let { if (p.overview.length > 140) it.trimEnd() + "…" else it }.ifBlank { "Now streaming on Cinedor. Tap to watch!" }
        image = if (wide) p.backdrop else p.poster
        link = p.appLink()
    }

    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        SectionTitle("1 · Pick a poster", "Tap any title — the notification fills itself")
        Box(Modifier.padding(horizontal = 16.dp)) {
            AdminField("Search movies & series", query, { query = it }, leading = Icons.Rounded.Search)
        }
        Spacer(Modifier.height(6.dp))
        if (loadingPosters && posters.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AdminColors.Red, strokeWidth = 2.dp)
            }
        } else {
            if (query.isBlank()) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("🔥 Trending in app", feed == "trending") { feed = "trending" }
                    Chip("🆕 New in 24h", feed == "new") { feed = "new" }
                }
            } else {
                Text(
                    "Results for “$query”",
                    color = AdminColors.Muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp)
                )
            }
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(posters, key = { it.type + it.id }) { p ->
                    val sel = selected?.id == p.id
                    Column(Modifier.width(116.dp).clickable { pick(p) }) {
                        Box {
                            AsyncImage(
                                model = p.poster,
                                contentDescription = p.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(2f / 3f)
                                    .clip(RoundedCornerShape(16.dp))
                                    .border(if (sel) 3.dp else 1.dp, if (sel) AdminColors.Red else Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                            )
                            if (p.rating > 0) {
                                Row(
                                    Modifier.padding(6.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Rounded.Star, contentDescription = null, tint = Color(0xFFF5C451), modifier = Modifier.size(11.dp))
                                    Text(" %.1f".format(p.rating), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            if (sel) {
                                Box(
                                    Modifier.align(Alignment.Center).size(38.dp).clip(CircleShape).background(AdminColors.Red),
                                    contentAlignment = Alignment.Center
                                ) { Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = Color.White) }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(p.title, color = AdminColors.Text, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${p.type} · ${p.year}", color = AdminColors.Muted, fontSize = 11.sp)
                    }
                }
            }
        }

        SectionTitle("2 · Message")
        GlassCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("🎬 New movie") { title = "🎬 New movie on Cinedor"; body = "Watch it now in HD — free." }
                Chip("📺 Episode") { title = "📺 New episode is here"; body = "The latest episode just dropped. Tap to watch." }
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("🚀 Update") { title = "🚀 Cinedor update"; body = "A new version is ready. Open the app to update." }
                Chip("⚽ Live") { title = "⚽ Live now"; body = "The match has started. Watch live on Cinedor." }
            }
            Spacer(Modifier.height(6.dp))
            AdminField("Title", title, { title = it.take(80) })
            AdminField("Message", body, { body = it.take(300) }, singleLine = false)
            AdminField("Picture link (filled from the poster)", image, { image = it.trim() })
            if (selected != null) {
                ToggleRow("Wide picture", "Landscape scene looks best in notifications", wide) {
                    wide = it
                    selected?.let { p -> image = if (it) p.backdrop else p.poster }
                }
            }
            AdminField("When tapped, open (search in app)", link, { link = it })
        }

        SectionTitle("3 · Preview")
        Box(
            Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF2A1D22), Color(0xFF0F0B0D))))
                .padding(14.dp)
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFFF4F0F1))
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(20.dp).clip(RoundedCornerShape(6.dp)).background(Color.White), contentAlignment = Alignment.Center) {
                        Image(painterResource(R.drawable.cinedor_logo), contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                    Text("Cinedor · now", color = Color(0xFF555555), fontSize = 12.sp)
                }
                Spacer(Modifier.height(6.dp))
                Text(title.ifBlank { "Title" }, color = Color(0xFF1B1B1B), fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                Text(body.ifBlank { "Message" }, color = Color(0xFF333333), fontSize = 13.5.sp)
                if (image.startsWith("https://")) {
                    Spacer(Modifier.height(8.dp))
                    AsyncImage(
                        model = image,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().aspectRatio(if (wide || selected == null) 16f / 9f else 2f / 3f).clip(RoundedCornerShape(12.dp))
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        selected?.let { p ->
            Row(
                Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusChip(p.inCinedor, onText = "In Cinedor · opens its page", offText = "Not in Cinedor · opens search")
            }
        }
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton("Notification", Modifier.weight(1f), busy = busy, icon = Icons.Rounded.Send) {
                when {
                    title.isBlank() -> Toast.makeText(context, "Pick a poster or write a title", Toast.LENGTH_SHORT).show()
                    store.workerUrl.isBlank() -> noServer = true
                    else -> confirm = true
                }
            }
            SecondaryButton("Popup in app", Modifier.weight(1f), icon = Icons.Rounded.Widgets) {
                when {
                    title.isBlank() -> Toast.makeText(context, "Pick a poster or write a title", Toast.LENGTH_SHORT).show()
                    cs.config == null -> Toast.makeText(context, "Settings are still loading", Toast.LENGTH_SHORT).show()
                    else -> popupConfirm = true
                }
            }
        }
        Text(
            "Notification goes to every phone now · Popup shows inside the app when people open it",
            color = AdminColors.Muted, fontSize = 11.5.sp,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp)
        )

        if (history.length() > 0) {
            SectionTitle("Sent from this phone")
            GlassCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                for (i in 0 until history.length()) {
                    val h = history.optJSONObject(i) ?: continue
                    HistoryRow(h)
                }
            }
        }
    }

    if (popupConfirm) {
        AlertDialog(
            onDismissRequest = { if (!popupBusy) popupConfirm = false },
            icon = { Icon(Icons.Rounded.Widgets, contentDescription = null, tint = AdminColors.RedBright) },
            title = { Text("Show as popup?") },
            text = { Text("\"$title\" will pop up for everyone the next time they open Cinedor (again every 24 hours until you turn it off in Popups).") },
            confirmButton = {
                TextButton(enabled = !popupBusy, onClick = {
                    val sp = cs.section("spotlight")
                    sp.put("enabled", true)
                    sp.put("id", "spot-" + System.currentTimeMillis())
                    sp.put("title", title.trim())
                    sp.put("message", body.trim())
                    sp.put("image", selected?.poster ?: image)
                    sp.put("backdrop", selected?.backdrop ?: image)
                    sp.put("buttonText", "Watch now")
                    sp.put("buttonUrl", link.trim())
                    sp.put("showEveryHours", 24)
                    val text = cs.config?.toString(2).orEmpty()
                    popupBusy = text.isNotBlank()
                    if (text.isNotBlank()) scope.launch {
                        runCatching { repo.save(text) }
                            .onSuccess {
                                cs.dirty = false
                                cs.tick++
                                Toast.makeText(context, "Popup is live ✓", Toast.LENGTH_SHORT).show()
                                popupConfirm = false
                            }
                            .onFailure { Toast.makeText(context, it.message ?: "Failed", Toast.LENGTH_LONG).show() }
                        popupBusy = false
                    }
                }) { Text(if (popupBusy) "Publishing…" else "Show popup", color = AdminColors.RedBright, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { popupConfirm = false }) { Text("Cancel") } },
            containerColor = AdminColors.Card
        )
    }

    if (noServer) {
        AlertDialog(
            onDismissRequest = { noServer = false },
            icon = { Icon(Icons.Rounded.Notifications, contentDescription = null, tint = AdminColors.RedBright) },
            title = { Text("Send it from Firebase") },
            text = {
                Text(
                    "Your message is ready. Open Firebase → Messaging → New campaign, paste it, choose Topic \"all\" and Publish. " +
                        "The title and message are copied for you.",
                    color = AdminColors.Muted
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    noServer = false
                    val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("Cinedor notification", title + "\n" + body + (if (image.isNotBlank()) "\n" + image else "")))
                    runCatching {
                        context.startActivity(
                            android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse("https://console.firebase.google.com/project/cinedor-60aac/messaging")
                            ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }) { Text("Copy & open Firebase", color = AdminColors.RedBright, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { noServer = false }) { Text("Close") } },
            containerColor = AdminColors.Card
        )
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            icon = { Icon(Icons.Rounded.Campaign, contentDescription = null, tint = AdminColors.RedBright) },
            title = { Text("Send to everyone?") },
            text = { Text("\"$title\" will be sent to every Cinedor user.") },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    busy = true
                    scope.launch {
                        runCatching { api.send(title.trim(), body.trim(), image, link.trim()) }
                            .onSuccess {
                                // The Worker saves a copy for the app's 🔔 inbox.
                                val arr = runCatching { JSONArray(store.historyJson()) }.getOrDefault(JSONArray())
                                val next = JSONArray().put(
                                    JSONObject().put("title", title).put("body", body).put("image", image).put("at", System.currentTimeMillis())
                                )
                                for (i in 0 until minOf(arr.length(), 29)) next.put(arr.get(i))
                                store.saveHistory(next.toString())
                                historyTick++
                                Toast.makeText(context, "Sent to all phones ✓", Toast.LENGTH_SHORT).show()
                            }
                            .onFailure { Toast.makeText(context, it.message ?: "Failed", Toast.LENGTH_LONG).show() }
                        busy = false
                    }
                }) { Text("Send", color = AdminColors.RedBright, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
            containerColor = AdminColors.Card
        )
    }
}

// =============================================================== popups

private enum class FType { SWITCH, TEXT, AREA, NUMBER, LIST }
private data class PField(val key: String, val type: FType, val label: String, val default: Boolean = false)
private data class PopupDef(val key: String, val title: String, val desc: String, val icon: ImageVector, val tint: Color, val fields: List<PField>)

private val POPUPS = listOf(
    PopupDef("spotlight", "Featured title popup", "Movie popup with poster & Watch now", Icons.Rounded.Star, Color(0xFFFF4D5A), listOf(
        PField("title", FType.TEXT, "Title"), PField("message", FType.AREA, "Message"),
        PField("image", FType.TEXT, "Picture link"), PField("buttonText", FType.TEXT, "Button text"),
        PField("showEveryHours", FType.NUMBER, "Show again every (hours)"), PField("id", FType.TEXT, "Popup ID (change to show again)")
    )),
    PopupDef("premium", "Premium popup", "Gold popup with perks, price and a button", Icons.Rounded.WorkspacePremium, Color(0xFFF5C451), listOf(
        PField("title", FType.TEXT, "Title"), PField("message", FType.AREA, "Message"),
        PField("perks", FType.LIST, "Perks (comma separated)"), PField("price", FType.TEXT, "Price"),
        PField("buttonText", FType.TEXT, "Button text"), PField("buttonUrl", FType.TEXT, "Button link"),
        PField("showEveryHours", FType.NUMBER, "Show again every (hours)"), PField("id", FType.TEXT, "Popup ID (change to show again)")
    )),
    PopupDef("community", "Join us popup", "Telegram & Facebook buttons", Icons.Rounded.Groups, Color(0xFF2AABEE), listOf(
        PField("title", FType.TEXT, "Title"), PField("message", FType.AREA, "Message"),
        PField("buttonText", FType.TEXT, "Telegram button text"), PField("buttonUrl", FType.TEXT, "Telegram link"),
        PField("secondaryText", FType.TEXT, "Facebook button text"), PField("secondaryUrl", FType.TEXT, "Facebook link"),
        PField("showEveryHours", FType.NUMBER, "Show again every (hours)"), PField("id", FType.TEXT, "Popup ID")
    )),
    PopupDef("sharePrompt", "App share popup", "\"Enjoying the movie?\" — WhatsApp & share", Icons.Rounded.Share, Color(0xFF25D366), listOf(
        PField("afterMinutes", FType.NUMBER, "Show after watching (minutes)"), PField("title", FType.TEXT, "Title"),
        PField("message", FType.AREA, "Message (empty = automatic)"), PField("perks", FType.LIST, "Perks (max 3)"),
        PField("shareMessage", FType.AREA, "Text that gets shared"), PField("buttonUrl", FType.TEXT, "App download link"),
        PField("showEveryHours", FType.NUMBER, "Ask again every (hours)"), PField("id", FType.TEXT, "Popup ID")
    )),
    PopupDef("announcement", "Announcement", "One-time notice when the app opens", Icons.Rounded.Campaign, AdminColors.RedBright, listOf(
        PField("title", FType.TEXT, "Title"), PField("message", FType.AREA, "Message"),
        PField("buttonText", FType.TEXT, "Button text"), PField("buttonUrl", FType.TEXT, "Button link"),
        PField("id", FType.TEXT, "Announcement ID")
    )),
    PopupDef("audioDubs", "Audio & Dubs section", "Language cards on every title page", Icons.Rounded.Headphones, Color(0xFFA78BFA), listOf(
        PField("title", FType.TEXT, "Section title")
    ))
)

@Composable
private fun PopupsScreen(cs: ConfigState, store: AdminStore, team: TeamAdmin) {
    var open by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(bottom = 100.dp)) {
        SectionTitle("Popups & features", if (cs.ref == "new") "First time: turn on what you want and tap Publish" else "Turn on, edit, then Publish")
        when {
            cs.config == null && cs.loading -> Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AdminColors.Red, strokeWidth = 2.dp)
            }
            cs.config == null -> GlassCard(Modifier.padding(16.dp).fillMaxWidth()) {
                Text("Couldn't load settings", color = AdminColors.Text, fontWeight = FontWeight.Bold)
                Text(cs.error, color = AdminColors.Muted, fontSize = 13.sp)
            }
            else -> androidx.compose.runtime.key(cs.tick) {
                if (store.can("ads")) AdsEditor(cs.section("ads")) { cs.changed() }
                if (store.can("premium")) PremiumCodesSection(team)
                if (store.can("popups")) POPUPS.forEach { def ->
                    val obj = cs.section(def.key)
                    var enabled by remember { mutableStateOf(obj.optBoolean("enabled", def.key == "audioDubs")) }
                    val expanded = open == def.key
                    GlassCard(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { open = if (expanded) null else def.key }) {
                            IconBadge(def.icon, def.tint, 44.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(def.title, color = AdminColors.Text, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                                    Spacer(Modifier.width(8.dp))
                                    StatusChip(enabled)
                                }
                                Text(def.desc, color = AdminColors.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Icon(
                                if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                                contentDescription = null, tint = AdminColors.Muted
                            )
                        }
                        ToggleRow(if (enabled) "Showing to users" else "Hidden", null, enabled) {
                            enabled = it; obj.put("enabled", it); cs.changed()
                        }
                        if (expanded) {
                            Column {
                                def.fields.forEach { f -> PopupField(obj, f) { cs.changed() } }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PopupField(obj: JSONObject, f: PField, onChanged: () -> Unit) {
    when (f.type) {
        FType.SWITCH -> {
            var checked by remember { mutableStateOf(obj.optBoolean(f.key, f.default)) }
            ToggleRow(f.label, null, checked) { checked = it; obj.put(f.key, it); onChanged() }
        }
        FType.LIST -> {
            val initial = obj.optJSONArray(f.key)?.let { a -> List(a.length()) { a.optString(it) }.joinToString(", ") } ?: ""
            var text by remember { mutableStateOf(initial) }
            AdminField(f.label, text, {
                text = it
                obj.put(f.key, JSONArray(it.split(",").map { s -> s.trim() }.filter { s -> s.isNotBlank() }))
                onChanged()
            })
        }
        FType.NUMBER -> {
            var text by remember { mutableStateOf(if (obj.has(f.key)) obj.optInt(f.key).toString() else "") }
            AdminField(f.label, text, {
                text = it.filter { c -> c.isDigit() }
                obj.put(f.key, text.toIntOrNull() ?: 0)
                onChanged()
            }, number = true)
        }
        FType.TEXT, FType.AREA -> {
            var text by remember { mutableStateOf(obj.optString(f.key)) }
            AdminField(f.label, text, { text = it; obj.put(f.key, it); onChanged() }, singleLine = f.type == FType.TEXT)
        }
    }
}

// =============================================================== system

@Composable
private fun SystemScreen(store: AdminStore, api: WorkerApi, cs: ConfigState, team: TeamAdmin, onLogout: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(bottom = 100.dp)) {
        SectionTitle("App control")
        if (cs.config != null) {
            androidx.compose.runtime.key(cs.tick) {
                val m = cs.section("maintenance")
                var mOn by remember { mutableStateOf(m.optBoolean("enabled")) }
                var mMsg by remember { mutableStateOf(m.optString("message")) }
                GlassCard(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(Icons.Rounded.Build, Color(0xFFFB923C), 44.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Maintenance mode", color = AdminColors.Text, fontWeight = FontWeight.ExtraBold)
                            Text("Blocks the app with a message", color = AdminColors.Muted, fontSize = 12.sp)
                        }
                        StatusChip(mOn)
                    }
                    ToggleRow("Turn on maintenance", null, mOn) { mOn = it; m.put("enabled", it); cs.changed() }
                    AdminField("Message", mMsg, { mMsg = it; m.put("message", it); cs.changed() }, singleLine = false)
                }
                val root = cs.section("")
                var minVer by remember { mutableStateOf(root.optInt("minVersionCode", 0).toString()) }
                GlassCard(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(Icons.Rounded.SystemUpdate, Color(0xFF60A5FA), 44.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Force update", color = AdminColors.Text, fontWeight = FontWeight.ExtraBold)
                            Text("Phones below this build must update (0 = off)", color = AdminColors.Muted, fontSize = 12.sp)
                        }
                    }
                    AdminField("Minimum build number", minVer, {
                        minVer = it.filter { c -> c.isDigit() }
                        root.put("minVersionCode", minVer.toIntOrNull() ?: 0)
                        cs.changed()
                    }, number = true)
                }
            }
        } else {
            GlassCard(Modifier.padding(16.dp).fillMaxWidth()) {
                Text(if (cs.loading) "Loading settings…" else cs.error, color = AdminColors.Muted)
            }
        }

        var advanced by remember { mutableStateOf(false) }
        SectionTitle("Advanced (optional)", "Not needed — everything works without this")
        GlassCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), onClick = if (advanced) null else ({ advanced = true })) {
            if (!advanced) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Rounded.Settings, Color(0xFF94A3B8), 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Notification server", color = AdminColors.Text, fontWeight = FontWeight.Bold)
                        Text("Only if you set up the Cloudflare Worker to send pushes from this app", color = AdminColors.Muted, fontSize = 12.sp)
                    }
                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, tint = AdminColors.Muted)
                }
            } else {
            ConnectionFields(store)
            Spacer(Modifier.height(10.dp))
            SecondaryButton("Test notification server", Modifier.fillMaxWidth(), icon = Icons.Rounded.CloudDone) {
                scope.launch {
                    val ok = api.health()
                    Toast.makeText(context, if (ok) "Worker is online ✓" else "Can't reach the worker", Toast.LENGTH_SHORT).show()
                }
            }
            }
        }

        if (store.isAdmin) TeamSection(team)

        SectionTitle("Account")
        GlassCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LogoMark(44)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(store.email.ifBlank { "Admin" }, color = AdminColors.Text, fontWeight = FontWeight.Bold)
                    Text("Cinedor Admin ${AdminUpdater.currentVersionName} · updates itself", color = AdminColors.Muted, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(12.dp))
            SecondaryButton("Log out", Modifier.fillMaxWidth(), icon = Icons.Rounded.Logout) { onLogout() }
        }
    }
}
