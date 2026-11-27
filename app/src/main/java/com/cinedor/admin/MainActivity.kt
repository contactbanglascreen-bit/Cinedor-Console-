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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.layout.onSizeChanged
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
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
                        .auroraBackground()
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
            .shadow(14.dp, RoundedCornerShape((size / 3.2f).dp), ambientColor = AdminColors.Red, spotColor = AdminColors.Red)
            .clip(RoundedCornerShape((size / 3.2f).dp))
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

/**
 * Floating capsule bottom bar: the active tab becomes a glowing red-violet pill with its icon and
 * name; the others show just their icon.
 */
@Composable
private fun AdminBottomBar(current: AdminTab, tabs: List<AdminTab>, onSelect: (AdminTab) -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .shadow(24.dp, CircleShape, ambientColor = Color.Black, spotColor = Color.Black)
                .liquidGlass(CircleShape)
                .padding(6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEach { t ->
                val sel = t == current
                Row(
                    Modifier
                        .weight(if (sel) 1.9f else 1f)
                        .animateContentSize()
                        .height(48.dp)
                        .then(if (sel) Modifier.glassAccent(CircleShape) else Modifier.clip(CircleShape))
                        .clickable { onSelect(t) },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (sel) t.icon else t.outline,
                        contentDescription = t.label,
                        tint = if (sel) Color.White else AdminColors.Muted,
                        modifier = Modifier.size(22.dp)
                    )
                    if (sel) {
                        Spacer(Modifier.width(6.dp))
                        Text(t.label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                    }
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

    val haze = remember { HazeState() }
    val density = androidx.compose.ui.platform.LocalDensity.current
    var topBarHeight by remember { mutableStateOf(96.dp) }
    CompositionLocalProvider(AdminGlass.LocalHaze provides haze) {
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().padding(top = topBarHeight).hazeSource(haze)) {
            when (tab) {
                AdminTab.DASHBOARD -> DashboardScreen(store, api, cs, cloud, onGo = { tab = it })
                AdminTab.FEEDBACK -> FeedbackScreen(cloud, store, api)
                AdminTab.NOTIFY -> NotifyScreen(store, api, cloud, cs, repo)
                AdminTab.POPUPS -> PopupsScreen(cs, store, team)
                AdminTab.SYSTEM -> SystemScreen(store, api, cs, team, onLogout = { confirmLogout = true })
            }

            // Floating "publish changes" bar whenever settings were edited
            SlideUp(visible = cs.dirty, modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 72.dp)) {
                Row(
                    Modifier
                        .padding(12.dp)
                        .fillMaxWidth()
                        .shadow(20.dp, RoundedCornerShape(24.dp), ambientColor = AdminColors.Red, spotColor = AdminColors.Red)
                        .liquidGlass(RoundedCornerShape(24.dp))
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
        Box(Modifier.align(Alignment.TopCenter)) {
        // Top bar: logo, page name and round glass buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .liquidGlass(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
                .statusBarsPadding()
                .onSizeChanged { topBarHeight = with(density) { it.height.toDp() } }
                .padding(start = 18.dp, end = 14.dp, top = 10.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LogoMark(42)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (store.isAdmin) "CINEDOR ADMIN" else "MODERATOR",
                    color = AdminColors.RedBright, fontWeight = FontWeight.Bold, fontSize = 10.5.sp, letterSpacing = 1.6.sp
                )
                Text(tab.label, color = AdminColors.Text, fontWeight = FontWeight.Black, fontSize = 22.sp)
            }
            TopRoundButton(Icons.Rounded.Refresh, "Reload") { load() }
            Spacer(Modifier.width(8.dp))
            TopRoundButton(Icons.Rounded.Logout, "Log out") { confirmLogout = true }
        }

        }
        Box(Modifier.align(Alignment.BottomCenter)) { AdminBottomBar(tab, tabs) { tab = it } }
    }
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

@Composable
private fun TopRoundButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).glassBubble(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, contentDescription = label, tint = AdminColors.Text, modifier = Modifier.size(19.dp)) }
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
                .shadow(26.dp, RoundedCornerShape(30.dp), ambientColor = AdminColors.Red, spotColor = AdminColors.Violet)
                .clip(RoundedCornerShape(30.dp))
                .background(Brush.linearGradient(listOf(Color(0xFFFF3D5E), Color(0xFFD9137A), Color(0xFF6D28D9))))
                .drawBehind {
                    // Decorative light circles
                    drawCircle(Color.White.copy(alpha = 0.10f), radius = size.height * 0.75f, center = androidx.compose.ui.geometry.Offset(size.width * 0.95f, size.height * 0.05f))
                    drawCircle(Color.White.copy(alpha = 0.07f), radius = size.height * 0.45f, center = androidx.compose.ui.geometry.Offset(size.width * 0.75f, size.height * 1.05f))
                }
                .glassSheen(1.2f)
                .glassRim(RoundedCornerShape(30.dp), 0.8f)
                .padding(22.dp)
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

/** Channels offered as one-tap choices for Live TV notifications (any name can be typed too). */
private val TV_CHANNEL_SUGGESTIONS = listOf(
    "T Sports", "Gazi TV", "Ekhon TV", "DBC News", "ATN Bangla", "NTV",
    "RTV", "Maasranga TV", "Bangla Vision", "Boishakhi TV", "Duronto TV", "Channel S",
    "Ekushey TV", "Green TV", "Ananda TV", "Nexus TV", "Zee Bangla", "Star Jalsha HD",
    "Sky Sports Premier League", "TNT Sports 1", "BBC News", "Sony Aath", "Colors Bangla", "ATN Bangla UK"
)

private data class AppPage(val key: String, val label: String, val title: String, val body: String)

/** App pages a notification can open (cinedor://open?page=…). */
private val APP_PAGES = listOf(
    AppPage("home", "🏠 Home", "🔥 আজকের সেরা মুভি", "Cinedor খুলে দেখুন নতুন কী এসেছে।"),
    AppPage("explore", "🧭 Explore", "🧭 নতুন কিছু খুঁজুন", "হাজারো মুভি আর সিরিজ — ক্যাটাগরি ধরে দেখুন।"),
    AppPage("tv", "📡 Live TV", "📡 Live TV এখন Cinedor-এ", "বাংলাদেশ, ভারত আর খেলার চ্যানেল — 24/7 ফ্রি।"),
    AppPage("shorts", "▶ Shorts", "▶ নতুন Shorts", "ছোট ছোট মজার ক্লিপ দেখুন।"),
    AppPage("mylist", "🔖 My List", "🔖 আপনার লিস্ট", "সেভ করা মুভিগুলো দেখা বাকি আছে!"),
    AppPage("ai", "✨ Cinedor Ai", "✨ মুভি খুঁজে পাচ্ছেন না?", "Cinedor Ai-কে গল্পটা বলুন, সে খুঁজে দেবে।"),
    AppPage("history", "🕘 History", "▶ যেখানে থেমেছিলেন", "আপনার দেখা মুভিটা শেষ করুন।"),
    AppPage("downloads", "⬇ Downloads", "⬇ ডাউনলোড করুন, অফলাইনে দেখুন", "নেট ছাড়াও মুভি দেখুন।"),
    AppPage("premium", "👑 Premium", "👑 Cinedor Premium", "বিজ্ঞাপন ছাড়া দেখুন — এখনই Premium নিন।"),
    AppPage("settings", "⚙ Settings", "⚙ নতুন সেটিংস", "অ্যাপটা নিজের মতো সাজিয়ে নিন।"),
    AppPage("feedback", "💬 Feedback", "💬 আপনার মতামত দিন", "কোন মুভি চান? আমাদের জানান।"),
    AppPage("me", "👤 Me", "👤 আপনার প্রোফাইল", "Cinedor-এ আপনার সব কিছু এক জায়গায়।")
)

@Composable
private fun NotifyScreen(store: AdminStore, api: WorkerApi, cloud: CloudAdmin, cs: ConfigState, repo: ConfigRepo) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var feed by remember { mutableStateOf("trending") }
    var popupConfirm by remember { mutableStateOf(false) }
    var collection by remember { mutableStateOf(false) }
    // What a tap on the notification opens: a title, a TV channel, an app page, search or a web page.
    var target by remember { mutableStateOf("title") }
    var server by remember { mutableIntStateOf(1) }
    var targetText by remember { mutableStateOf("") }
    var picks by remember { mutableStateOf<List<PosterItem>>(emptyList()) }
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
    LaunchedEffect(query, feed, server) {
        loadingPosters = true
        val s2 = cs.config?.optString("server2Url").orEmpty().split(',').first().trim()
        posters = if (server == 2) {
            if (query.isNotBlank()) delay(400)
            Server2Posters.load(s2, query)
        } else if (query.isBlank()) {
            val list = if (feed == "new") CinedorCatalog.latest() else CinedorCatalog.trending()
            list.ifEmpty { PosterSource.trending() }
        } else {
            delay(400)
            CinedorCatalog.search(query).ifEmpty { PosterSource.search(query) }
        }
        loadingPosters = false
    }

    fun pick(p: PosterItem) {
        if (collection) {
            // Collection: up to ten posters in one notification (3 in a row, 10 in a grid when opened)
            picks = if (picks.any { it.id == p.id }) picks.filterNot { it.id == p.id } else (picks + p).take(10)
            if (title.isBlank() || title.startsWith("🎬") || title.startsWith("📺")) title = "🔥 Free Movie & Hot TV Series"
            body = picks.take(3).joinToString(" · ") { it.title } + if (picks.size > 3) " +${picks.size - 3} more" else ""
            image = ""
            link = ""
            return
        }
        selected = p
        title = (if (p.type == "Series") "📺 " else "🎬 ") + p.title + (if (p.year.isNotBlank()) " (${p.year})" else "")
        body = p.overview.take(140).let { if (p.overview.length > 140) it.trimEnd() + "…" else it }.ifBlank { "Now streaming on Cinedor. Tap to watch!" }
        image = if (wide) p.backdrop else p.poster
        link = p.appLink()
    }

    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        SectionTitle("0 · Tap opens", "Anything in the app: a title, a live channel, a page, search or a website")
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("🎬 Movie / Series", target == "title") { target = "title"; link = selected?.appLink().orEmpty() }
                Chip("📡 Live TV", target == "tv") { target = "tv"; collection = false; picks = emptyList(); selected = null; image = ""; link = "" }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("📱 App page", target == "page") { target = "page"; collection = false; picks = emptyList(); selected = null; image = ""; link = "" }
                Chip("🔍 Search", target == "search") { target = "search"; collection = false; picks = emptyList(); selected = null; link = "" }
                Chip("🌐 Website", target == "web") { target = "web"; collection = false; picks = emptyList(); selected = null; link = "" }
            }
        }
        fun enc(v: String) = java.net.URLEncoder.encode(v, "UTF-8")
        when (target) {
            "title" -> {
            SectionTitle("1 · Pick a poster", if (collection) "Tap up to 10 titles — 3 show in a row, all 10 when opened" else "Tap any title — the notification fills itself")
            Row(Modifier.padding(horizontal = 16.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("One title", !collection) { collection = false; picks = emptyList() }
                Chip("Collection · up to 10", collection) { collection = true; selected = null; picks = emptyList() }
            }
            Row(Modifier.padding(horizontal = 16.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("Server 1", server == 1) { server = 1 }
                Chip("Server 2", server == 2) { server = 2 }
            }
            Box(Modifier.padding(horizontal = 16.dp)) {
                AdminField("Search movies & series", query, { query = it }, leading = Icons.Rounded.Search)
            }
            Spacer(Modifier.height(6.dp))
            if (loadingPosters && posters.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AdminColors.Red, strokeWidth = 2.dp)
                }
            } else {
                if (query.isBlank() && server == 1) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip("🔥 Trending in app", feed == "trending") { feed = "trending" }
                        Chip("🆕 New in 24h", feed == "new") { feed = "new" }
                    }
                } else if (query.isNotBlank()) {
                    Text(
                        "Results for “$query”",
                        color = AdminColors.Muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp)
                    )
                }
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(posters, key = { it.type + it.id }) { p ->
                        val sel = if (collection) picks.any { it.id == p.id } else selected?.id == p.id
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
            }
            "tv" -> {
                SectionTitle("1 · Pick a channel", "Tapping the notification starts this channel")
                GlassCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                    AdminField("Channel name", targetText, { v ->
                        targetText = v
                        link = if (v.isBlank()) "" else "cinedor://tv?t=" + enc(v.trim())
                    }, leading = Icons.Rounded.Search)
                    Spacer(Modifier.height(6.dp))
                    TV_CHANNEL_SUGGESTIONS.chunked(3).forEach { row ->
                        Row(Modifier.padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { name ->
                                Chip(name, targetText == name) {
                                    targetText = name
                                    link = "cinedor://tv?t=" + enc(name)
                                    title = "📡 $name এখন লাইভ"
                                    body = "Cinedor-এ এখনই $name দেখুন — ফ্রি, 24/7।"
                                }
                            }
                        }
                    }
                }
            }
            "page" -> {
                SectionTitle("1 · Pick a page", "Tapping the notification opens this part of the app")
                GlassCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                    APP_PAGES.chunked(3).forEach { row ->
                        Row(Modifier.padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { pg ->
                                Chip(pg.label, targetText == pg.key) {
                                    targetText = pg.key
                                    link = "cinedor://open?page=" + pg.key
                                    title = pg.title
                                    body = pg.body
                                }
                            }
                        }
                    }
                }
            }
            "search" -> {
                SectionTitle("1 · What to search", "Tapping the notification shows these search results")
                GlassCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                    AdminField("Search words (e.g. Gopal Bhar)", targetText, { v ->
                        targetText = v
                        link = if (v.isBlank()) "" else "cinedor://search?q=" + enc(v.trim())
                        if (v.isNotBlank()) { title = "🔍 ${v.trim()} — সব পর্ব এখন Cinedor-এ"; body = "ট্যাপ করে সব দেখুন।" }
                    }, leading = Icons.Rounded.Search)
                }
            }
            "web" -> {
                SectionTitle("1 · Website link", "Tapping the notification opens this page in the browser")
                GlassCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                    AdminField("https://…", targetText, { v ->
                        targetText = v.trim()
                        link = if (v.trim().startsWith("http")) v.trim() else ""
                    })
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
            AdminField(
                when (target) {
                    "title" -> "When tapped, open (filled from the poster)"
                    "web" -> "Website link"
                    else -> "App link (filled for you)"
                },
                link, { link = it.trim() }
            )
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
                if (collection && picks.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    // Same look as the opened notification: rows of five, the tenth marked "More"
                    picks.chunked(5).forEachIndexed { row, chunk ->
                        if (row > 0) Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            for (i in 0 until 5) {
                                val pk = chunk.getOrNull(i)
                                Box(Modifier.weight(1f).aspectRatio(2f / 3f)) {
                                    if (pk != null) {
                                        AsyncImage(
                                            model = pk.poster,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp))
                                        )
                                        if (row == 1 && i == 4 && picks.size >= 10) {
                                            Box(
                                                Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)).background(Color(0x99000000)),
                                                contentAlignment = Alignment.Center
                                            ) { Text("More ›", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (!collection && image.startsWith("https://")) {
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
                        runCatching {
                            api.send(
                                title.trim(), body.trim(), if (collection) "" else (selected?.poster ?: image), link.trim(),
                                images = if (collection) picks.map { it.poster } else emptyList()
                            )
                        }
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
                // Server 2: link of the extra content worker (worker/server2-worker.js)
                var s2 by remember { mutableStateOf(root.optString("server2Url", "")) }
                GlassCard(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(Icons.Rounded.SystemUpdate, Color(0xFF34D399), 44.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Server 2", color = AdminColors.Text, fontWeight = FontWeight.ExtraBold)
                            Text("Worker links, separated by commas — the first is used, the rest are backups", color = AdminColors.Muted, fontSize = 12.sp)
                        }
                    }
                    AdminField("https://main.workers.dev, https://backup.workers.dev", s2, {
                        s2 = it.trim()
                        root.put("server2Url", s2)
                        cs.changed()
                    })
                }
                // Live TV: link of an .m3u playlist that replaces the channel list inside the app
                var tvUrl by remember { mutableStateOf(root.optString("tvPlaylistUrl", "")) }
                GlassCard(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(Icons.Rounded.SystemUpdate, Color(0xFFF87171), 44.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Live TV channels", color = AdminColors.Text, fontWeight = FontWeight.ExtraBold)
                            Text(".m3u playlist link (leave empty = channels inside the app)", color = AdminColors.Muted, fontSize = 12.sp)
                        }
                    }
                    AdminField("https://…/channels.m3u", tvUrl, {
                        tvUrl = it.trim()
                        root.put("tvPlaylistUrl", tvUrl)
                        cs.changed()
                    })
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
