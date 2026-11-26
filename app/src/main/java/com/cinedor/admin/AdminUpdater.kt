package com.cinedor.admin

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class AdminUpdate(val versionCode: Int, val versionName: String, val apkUrl: String, val notes: String)

/** Checks this repo's GitHub Releases (tag "admin-N") and installs newer builds in place. */
object AdminUpdater {
    private const val LATEST_URL =
        "https://api.github.com/repos/contactbanglascreen-bit/Cinedor-Console-/releases/latest"

    val currentVersionCode: Int get() = BuildConfig.VERSION_CODE
    val currentVersionName: String get() = BuildConfig.VERSION_NAME

    suspend fun fetchLatest(): AdminUpdate? = withContext(Dispatchers.IO) {
        runCatching {
            val c = (URL(LATEST_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "Cinedor-Admin")
            }
            try {
                if (c.responseCode !in 200..299) return@runCatching null
                val json = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
                val code = Regex("(\\d+)").findAll(json.optString("tag_name")).lastOrNull()?.value?.toIntOrNull()
                    ?: return@runCatching null
                val assets = json.optJSONArray("assets") ?: return@runCatching null
                var apk: String? = null
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    if (a.optString("name").endsWith(".apk", true)) { apk = a.optString("browser_download_url"); break }
                }
                if (apk.isNullOrBlank()) return@runCatching null
                AdminUpdate(code, json.optString("name").ifBlank { "Build $code" }, apk, json.optString("body"))
            } finally {
                c.disconnect()
            }
        }.getOrNull()
    }

    fun startDownload(context: Context, u: AdminUpdate): Long? = runCatching {
        val dir = File(context.getExternalFilesDir(null), "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val req = DownloadManager.Request(Uri.parse(u.apkUrl))
            .setTitle("Cinedor Admin ${u.versionName}")
            .setMimeType("application/vnd.android.package-archive")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestinationUri(Uri.fromFile(File(dir, "CinedorAdmin-${u.versionCode}.apk")))
        (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
    }.getOrNull()

    fun progress(context: Context, id: Long): Float {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        dm.query(DownloadManager.Query().setFilterById(id))?.use { c ->
            if (!c.moveToFirst()) return -1f
            val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            if (status == DownloadManager.STATUS_SUCCESSFUL) return 1f
            if (status == DownloadManager.STATUS_FAILED) return -1f
            val done = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            return if (total > 0) (done.toFloat() / total).coerceIn(0f, 0.99f) else 0f
        }
        return -1f
    }

    fun install(context: Context, id: Long): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            return false
        }
        val uri = (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).getUriForDownloadedFile(id)
            ?: return false
        return runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        }.getOrDefault(false)
    }
}

/** Asks to update when a newer admin build is published; downloads and opens the installer. */
@Composable
fun AdminUpdatePrompt() {
    val context = LocalContext.current
    var info by remember { mutableStateOf<AdminUpdate?>(null) }
    var dismissed by remember { mutableStateOf(false) }
    var downloadId by remember { mutableLongStateOf(-1L) }
    var progress by remember { mutableFloatStateOf(0f) }
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val latest = AdminUpdater.fetchLatest()
        if (latest != null && latest.versionCode > AdminUpdater.currentVersionCode) info = latest
    }
    LaunchedEffect(downloadId) {
        if (downloadId < 0) return@LaunchedEffect
        while (true) {
            val p = AdminUpdater.progress(context, downloadId)
            if (p < 0f) { failed = true; break }
            progress = p
            if (p >= 1f) { AdminUpdater.install(context, downloadId); break }
            delay(400)
        }
    }

    val u = info ?: return
    if (dismissed) return
    Dialog(onDismissRequest = { if (downloadId < 0) dismissed = true }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(AdminColors.Card)
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .size(84.dp)
                    .clip(CircleShape)
                    .background(Color.White),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
            ) {
                Image(painterResource(R.drawable.cinedor_logo), contentDescription = null, modifier = Modifier.size(60.dp))
            }
            Spacer(Modifier.height(14.dp))
            Text("Admin update available", color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp)
            Spacer(Modifier.height(4.dp))
            Text("${AdminUpdater.currentVersionName}  →  ${u.versionName.substringAfterLast(' ')}", color = AdminColors.Muted)
            if (u.notes.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(u.notes.take(400), color = AdminColors.Muted, fontSize = 13.sp)
            }
            if (downloadId >= 0 && !failed) {
                Spacer(Modifier.height(14.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    color = AdminColors.Red,
                    trackColor = AdminColors.Line,
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape)
                )
                Spacer(Modifier.height(6.dp))
                Text(if (progress >= 1f) "Opening installer…" else "${(progress * 100).toInt()}%", color = AdminColors.Muted)
            }
            if (failed) {
                Spacer(Modifier.height(10.dp))
                Text("Download failed. Try again.", color = AdminColors.Red)
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    when {
                        downloadId < 0 || failed -> {
                            failed = false; progress = 0f
                            downloadId = AdminUpdater.startDownload(context, u) ?: -1L
                            if (downloadId < 0) failed = true
                        }
                        progress >= 1f -> AdminUpdater.install(context, downloadId)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = AdminColors.Red),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text(
                    when {
                        downloadId < 0 -> "Update now"
                        failed -> "Retry"
                        progress >= 1f -> "Install"
                        else -> "Downloading…"
                    },
                    fontWeight = FontWeight.Black
                )
            }
            if (downloadId < 0) {
                TextButton(onClick = { dismissed = true }) { Text("Later", color = AdminColors.Muted) }
            }
        }
    }
}

object AdminColors {
    val Bg = Color(0xFF0B0809)
    val Card = Color(0xFF161113)
    val Field = Color(0xFF1E171A)
    val Line = Color(0xFF2E2327)
    val Text = Color(0xFFF6EEF0)
    val Muted = Color(0xFFA8979C)
    val Red = Color(0xFFE50914)
    val RedBright = Color(0xFFFF3B45)
    val RedDark = Color(0xFF8A0710)
    val Green = Color(0xFF22C55E)
    val redBrush get() = Brush.linearGradient(listOf(RedBright, RedDark))
}
