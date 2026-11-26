package com.cinedor.admin

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

const val ADMIN_UID = "xCbK5jp6PNf8UvTIE4mZYYLhpBj2"
const val DEFAULT_FIREBASE_KEY = "AIzaSyCddEpMsakf5vHeLlHuJLMPUCC50uTYOpY"

class AdminException(message: String) : Exception(message)

/** Encrypted storage (Android Keystore) for the login session and connection settings. */
class AdminStore(context: Context) {
    private val prefs: SharedPreferences = runCatching {
        val alias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            "cinedor_admin_secure", alias, context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }.getOrElse { context.getSharedPreferences("cinedor_admin_plain", Context.MODE_PRIVATE) }

    var refreshToken: String
        get() = prefs.getString("refresh", "").orEmpty()
        set(v) = prefs.edit().putString("refresh", v).apply()
    var email: String
        get() = prefs.getString("email", "").orEmpty()
        set(v) = prefs.edit().putString("email", v).apply()
    var workerUrl: String
        get() = prefs.getString("worker", "").orEmpty().trimEnd('/')
        set(v) = prefs.edit().putString("worker", v.trim().trimEnd('/')).apply()
    var apiKey: String
        get() = prefs.getString("apikey", "").orEmpty().ifBlank { DEFAULT_FIREBASE_KEY }
        set(v) = prefs.edit().putString("apikey", v.trim()).apply()

    var githubToken: String
        get() = prefs.getString("gh_token", "").orEmpty()
        set(v) = prefs.edit().putString("gh_token", v.trim()).apply()

    fun historyJson(): String = prefs.getString("history", "[]").orEmpty()
    fun saveHistory(json: String) = prefs.edit().putString("history", json).apply()
}

internal data class HttpResult(val code: Int, val body: String)

internal fun http(
    url: String,
    method: String = "GET",
    body: String? = null,
    contentType: String = "application/json",
    headers: Map<String, String> = emptyMap()
): HttpResult {
    val c = (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = method
        connectTimeout = 15000
        readTimeout = 30000
        headers.forEach { (k, v) -> setRequestProperty(k, v) }
        if (body != null) {
            doOutput = true
            setRequestProperty("Content-Type", contentType)
        }
    }
    try {
        if (body != null) c.outputStream.use { it.write(body.toByteArray()) }
        val code = c.responseCode
        val text = (if (code in 200..299) c.inputStream else c.errorStream)
            ?.bufferedReader()?.use { it.readText() }.orEmpty()
        return HttpResult(code, text)
    } finally {
        c.disconnect()
    }
}

/** Firebase Authentication (email + password) through the REST API. Only the admin UID is accepted. */
class AdminAuth(private val store: AdminStore) {
    private var idToken: String? = null
    private var expiresAt = 0L

    val isLoggedIn: Boolean get() = store.refreshToken.isNotBlank()

    suspend fun login(email: String, password: String) = withContext(Dispatchers.IO) {
        val body = JSONObject().put("email", email).put("password", password).put("returnSecureToken", true)
        val r = http(
            "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=${store.apiKey}",
            "POST", body.toString()
        )
        val json = runCatching { JSONObject(r.body) }.getOrDefault(JSONObject())
        if (r.code !in 200..299) {
            throw AdminException(friendlyError(json.optJSONObject("error")?.optString("message").orEmpty()))
        }
        if (json.optString("localId") != ADMIN_UID) throw AdminException("This account is not the Cinedor admin.")
        idToken = json.optString("idToken")
        expiresAt = System.currentTimeMillis() + (json.optString("expiresIn").toLongOrNull() ?: 3600L) * 1000L - 60_000L
        store.refreshToken = json.optString("refreshToken")
        store.email = json.optString("email")
    }

    private fun refresh() {
        val rt = store.refreshToken
        if (rt.isBlank()) throw AdminException("Please log in")
        val r = http(
            "https://securetoken.googleapis.com/v1/token?key=${store.apiKey}",
            "POST", "grant_type=refresh_token&refresh_token=" + URLEncoder.encode(rt, "UTF-8"),
            contentType = "application/x-www-form-urlencoded"
        )
        val json = runCatching { JSONObject(r.body) }.getOrDefault(JSONObject())
        if (r.code !in 200..299) throw AdminException("Session expired. Please log in again.")
        if (json.optString("user_id") != ADMIN_UID) throw AdminException("This account is not the Cinedor admin.")
        idToken = json.optString("id_token")
        expiresAt = System.currentTimeMillis() + (json.optString("expires_in").toLongOrNull() ?: 3600L) * 1000L - 60_000L
        store.refreshToken = json.optString("refresh_token")
    }

    fun token(): String {
        if (idToken == null || System.currentTimeMillis() > expiresAt) refresh()
        return idToken ?: throw AdminException("Please log in")
    }

    suspend fun checkSession() = withContext(Dispatchers.IO) { token() }

    fun logout() {
        idToken = null
        store.refreshToken = ""
    }

    private fun friendlyError(code: String): String = when {
        "INVALID_LOGIN_CREDENTIALS" in code || "INVALID_PASSWORD" in code || "EMAIL_NOT_FOUND" in code ->
            "Wrong email or password."
        "TOO_MANY_ATTEMPTS" in code -> "Too many tries. Wait a few minutes."
        "API_KEY" in code || "BLOCKED" in code.uppercase() ->
            "This Firebase key blocks the login. Add a Web app in Firebase and paste its API key in Connection."
        "PASSWORD_LOGIN_DISABLED" in code || "OPERATION_NOT_ALLOWED" in code ->
            "Turn on Email/Password sign-in in Firebase → Authentication."
        code.isBlank() -> "Login failed. Check the internet."
        else -> code
    }
}

/** Calls the Cinedor admin Worker (it holds the Firebase private key and GitHub token). */
class WorkerApi(private val store: AdminStore, private val auth: AdminAuth) {
    private fun call(path: String, method: String = "GET", body: JSONObject? = null): JSONObject {
        val base = store.workerUrl
        if (base.isBlank()) throw AdminException("Set the Worker address in Connection first.")
        val r = http(base + path, method, body?.toString(), headers = mapOf("Authorization" to "Bearer " + auth.token()))
        val json = runCatching { JSONObject(r.body) }.getOrElse { JSONObject().put("ok", false).put("error", "Worker answered ${r.code}") }
        if (r.code !in 200..299 || !json.optBoolean("ok")) throw AdminException(json.optString("error", "Request failed"))
        return json
    }

    suspend fun send(title: String, body: String, image: String, link: String, topic: String = "all"): String = withContext(Dispatchers.IO) {
        call(
            "/send", "POST",
            JSONObject().put("title", title).put("body", body).put("image", image).put("link", link).put("topic", topic)
        ).optString("id")
    }

    suspend fun loadConfig(): Pair<String, String> = withContext(Dispatchers.IO) {
        val j = call("/config")
        j.optString("ref") to j.optString("config")
    }

    suspend fun saveConfig(text: String): String = withContext(Dispatchers.IO) {
        JSONObject(text) // must be valid JSON
        call("/config", "POST", JSONObject().put("config", text)).optString("ref")
    }

    suspend fun health(): Boolean = withContext(Dispatchers.IO) {
        val base = store.workerUrl
        if (base.isBlank()) return@withContext false
        runCatching { http("$base/health").code in 200..299 }.getOrDefault(false)
    }
}


/**
 * App settings file (console/app-config.json in the Cinedor repo).
 * Reads work without any setup (public repo). Saving uses the Worker if set, otherwise a GitHub token.
 */
class ConfigRepo(private val store: AdminStore, private val worker: WorkerApi) {
    companion object {
        const val REPO = "contactbanglascreen-bit/Baizid-"
        const val PATH = "console/app-config.json"
        val REFS = listOf("main", "ccr-c4b9015d-7d590g")
    }

    private fun ghHeaders(): Map<String, String> = buildMap {
        put("Accept", "application/vnd.github+json")
        put("User-Agent", "Cinedor-Admin")
        if (store.githubToken.isNotBlank()) put("Authorization", "Bearer " + store.githubToken)
    }

    private data class Found(val ref: String, val sha: String, val text: String)

    private fun find(): Found {
        for (ref in REFS) {
            val r = http("https://api.github.com/repos/$REPO/contents/$PATH?ref=$ref", headers = ghHeaders())
            if (r.code in 200..299) {
                val j = JSONObject(r.body)
                val bytes = android.util.Base64.decode(j.optString("content").replace("\n", ""), android.util.Base64.DEFAULT)
                return Found(ref, j.optString("sha"), String(bytes, Charsets.UTF_8))
            }
        }
        throw AdminException("Settings file not found")
    }

    suspend fun load(): Pair<String, String> = withContext(Dispatchers.IO) {
        if (store.workerUrl.isNotBlank()) {
            runCatching { return@withContext worker.loadConfig() }
        }
        val f = find()
        f.ref to f.text
    }

    val canSave: Boolean get() = store.workerUrl.isNotBlank() || store.githubToken.isNotBlank()

    suspend fun save(text: String): String = withContext(Dispatchers.IO) {
        JSONObject(text)
        if (store.workerUrl.isNotBlank()) return@withContext worker.saveConfig(text)
        if (store.githubToken.isBlank()) throw AdminException("Add a GitHub token (System tab) or a Worker to publish.")
        val f = find()
        val body = JSONObject()
            .put("message", "Update Cinedor settings from Cinedor Admin")
            .put("content", android.util.Base64.encodeToString(text.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP))
            .put("sha", f.sha)
            .put("branch", f.ref)
        val r = http("https://api.github.com/repos/$REPO/contents/$PATH", "PUT", body.toString(), headers = ghHeaders())
        if (r.code !in 200..299) throw AdminException(
            if (r.code == 401 || r.code == 403) "GitHub token can't write to the Cinedor repo" else "GitHub save failed (${r.code})"
        )
        f.ref
    }
}

/** A movie / series poster to attach to a notification. */
data class PosterItem(
    val id: Long,
    val title: String,
    val year: String,
    val type: String,
    val poster: String,
    val backdrop: String,
    val overview: String,
    val rating: Double
)

/** Posters from TMDB (trending + search), so the admin can pick one instead of pasting a link. */
object PosterSource {
    private const val KEY = "17509a46d9802f1f5b73bf7b9d8bad7c"

    private fun parse(body: String): List<PosterItem> {
        val arr = JSONObject(body).optJSONArray("results") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val type = o.optString("media_type", if (o.has("first_air_date")) "tv" else "movie")
            if (type != "movie" && type != "tv") return@mapNotNull null
            val posterPath = o.optString("poster_path").takeIf { it.isNotBlank() && it != "null" } ?: return@mapNotNull null
            val backdropPath = o.optString("backdrop_path").takeIf { it.isNotBlank() && it != "null" }
            PosterItem(
                id = o.optLong("id"),
                title = o.optString("title").ifBlank { o.optString("name") },
                year = (o.optString("release_date").ifBlank { o.optString("first_air_date") }).take(4),
                type = if (type == "tv") "Series" else "Movie",
                poster = "https://image.tmdb.org/t/p/w500$posterPath",
                backdrop = backdropPath?.let { "https://image.tmdb.org/t/p/w780$it" } ?: "https://image.tmdb.org/t/p/w780$posterPath",
                overview = o.optString("overview"),
                rating = o.optDouble("vote_average", 0.0)
            )
        }
    }

    suspend fun trending(): List<PosterItem> = withContext(Dispatchers.IO) {
        runCatching { parse(http("https://api.themoviedb.org/3/trending/all/day?api_key=$KEY").body) }.getOrDefault(emptyList())
    }

    suspend fun search(query: String): List<PosterItem> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        runCatching {
            parse(http("https://api.themoviedb.org/3/search/multi?api_key=$KEY&query=" + URLEncoder.encode(query, "UTF-8")).body)
        }.getOrDefault(emptyList())
    }
}

/** Latest public Cinedor release (shown on the dashboard). */
data class ReleaseInfo(val name: String, val tag: String, val downloads: Int, val publishedAt: String)

object CinedorReleases {
    suspend fun latest(): ReleaseInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val r = http(
                "https://api.github.com/repos/${ConfigRepo.REPO}/releases/latest",
                headers = mapOf("Accept" to "application/vnd.github+json", "User-Agent" to "Cinedor-Admin")
            )
            if (r.code !in 200..299) return@runCatching null
            val j = JSONObject(r.body)
            val assets = j.optJSONArray("assets")
            var dl = 0
            if (assets != null) for (i in 0 until assets.length()) dl += assets.optJSONObject(i)?.optInt("download_count") ?: 0
            ReleaseInfo(j.optString("name"), j.optString("tag_name"), dl, j.optString("published_at").take(10))
        }.getOrNull()
    }
}


// ------------------------------------------------------------------ users & feedback (Firestore)

data class DeviceInfo(
    val uid: String,
    val country: String,
    val countryName: String,
    val brand: String,
    val model: String,
    val android: String,
    val appVersion: String,
    val lastSeen: String
)

data class FeedbackEntry(
    val id: String,
    val uid: String,
    val category: String,
    val message: String,
    val contact: String,
    val status: String,
    val reply: String,
    val device: String,
    val country: String,
    val appVersion: String,
    val createdAt: String
) {
    val isFixed get() = status == "fixed"
}

/** Reads phone stats and feedback from Firestore as the admin (rules allow only the admin UID). */
class CloudAdmin(private val auth: AdminAuth) {
    private val docs = "https://firestore.googleapis.com/v1/projects/cinedor-60aac/databases/(default)/documents"

    private fun bearer() = mapOf("Authorization" to "Bearer " + auth.token())

    private fun v(f: JSONObject?, name: String): String {
        val o = f?.optJSONObject(name) ?: return ""
        return o.optString("stringValue").ifBlank { o.optString("timestampValue") }
    }

    private fun check(r: HttpResult) {
        if (r.code in 200..299) return
        val msg = runCatching { JSONObject(r.body).optJSONObject("error")?.optString("message") }.getOrNull().orEmpty()
        throw AdminException(
            when {
                r.code == 403 -> "Firestore blocked the request. Publish the Cinedor Firestore rules."
                r.code == 404 || "does not exist" in msg -> "Firestore database isn't created yet."
                else -> msg.ifBlank { "Firestore error ${r.code}" }
            }
        )
    }

    suspend fun devices(): List<DeviceInfo> = withContext(Dispatchers.IO) {
        val out = mutableListOf<DeviceInfo>()
        var pageToken = ""
        repeat(10) {
            val url = "$docs/devices?pageSize=300" + if (pageToken.isNotBlank()) "&pageToken=" + URLEncoder.encode(pageToken, "UTF-8") else ""
            val r = http(url, headers = bearer())
            check(r)
            val j = JSONObject(r.body)
            val arr = j.optJSONArray("documents")
            if (arr != null) for (i in 0 until arr.length()) {
                val d = arr.optJSONObject(i) ?: continue
                val f = d.optJSONObject("fields")
                out += DeviceInfo(
                    uid = d.optString("name").substringAfterLast('/'),
                    country = v(f, "country"), countryName = v(f, "countryName"),
                    brand = v(f, "brand"), model = v(f, "model"), android = v(f, "android"),
                    appVersion = v(f, "appVersion"), lastSeen = v(f, "lastSeen")
                )
            }
            pageToken = j.optString("nextPageToken")
            if (pageToken.isBlank()) return@withContext out
        }
        out
    }

    suspend fun feedback(): List<FeedbackEntry> = withContext(Dispatchers.IO) {
        val q = JSONObject().put(
            "structuredQuery", JSONObject()
                .put("from", JSONArray().put(JSONObject().put("collectionId", "feedback")))
                .put("orderBy", JSONArray().put(JSONObject().put("field", JSONObject().put("fieldPath", "createdAt")).put("direction", "DESCENDING")))
                .put("limit", 300)
        )
        val r = http("$docs:runQuery", "POST", q.toString(), headers = bearer())
        check(r)
        val arr = JSONArray(r.body)
        (0 until arr.length()).mapNotNull { i ->
            val d = arr.optJSONObject(i)?.optJSONObject("document") ?: return@mapNotNull null
            val f = d.optJSONObject("fields")
            FeedbackEntry(
                id = d.optString("name").substringAfterLast('/'),
                uid = v(f, "uid"), category = v(f, "category"), message = v(f, "message"), contact = v(f, "contact"),
                status = v(f, "status").ifBlank { "open" }, reply = v(f, "reply"), device = v(f, "device"),
                country = v(f, "country"), appVersion = v(f, "appVersion"), createdAt = v(f, "createdAt")
            )
        }
    }

    suspend fun setStatus(id: String, fixed: Boolean, reply: String) = withContext(Dispatchers.IO) {
        val now = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
            .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(java.util.Date())
        val fields = JSONObject()
            .put("status", JSONObject().put("stringValue", if (fixed) "fixed" else "open"))
            .put("reply", JSONObject().put("stringValue", reply))
            .put("fixedAt", JSONObject().put("timestampValue", now))
        val mask = "updateMask.fieldPaths=status&updateMask.fieldPaths=reply&updateMask.fieldPaths=fixedAt"
        val r = http("$docs/feedback/$id?$mask", "POST", JSONObject().put("fields", fields).toString(),
            headers = bearer() + ("X-HTTP-Method-Override" to "PATCH"))
        check(r)
    }

    /** Saves a sent notification so it also appears in every phone's 🔔 inbox. */
    suspend fun logNotification(title: String, body: String, image: String, link: String) = withContext(Dispatchers.IO) {
        val now = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
            .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(java.util.Date())
        val fields = JSONObject()
            .put("title", JSONObject().put("stringValue", title))
            .put("body", JSONObject().put("stringValue", body))
            .put("image", JSONObject().put("stringValue", image))
            .put("link", JSONObject().put("stringValue", link))
            .put("at", JSONObject().put("timestampValue", now))
        check(http("$docs/notifications", "POST", JSONObject().put("fields", fields).toString(), headers = bearer()))
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        check(http("$docs/feedback/$id", "DELETE", headers = bearer()))
    }
}
