package com.cinedor.admin

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
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

    /** "admin" or "moderator", and what a moderator may do. */
    var role: String
        get() = prefs.getString("role", "admin").orEmpty()
        set(v) = prefs.edit().putString("role", v).apply()
    var perms: Set<String>
        get() = prefs.getStringSet("perms", emptySet()).orEmpty()
        set(v) = prefs.edit().putStringSet("perms", v).apply()
    var displayName: String
        get() = prefs.getString("display_name", "").orEmpty()
        set(v) = prefs.edit().putString("display_name", v).apply()
    val isAdmin: Boolean get() = role == "admin"
    fun can(perm: String): Boolean = isAdmin || perm in perms

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
        val uid = json.optString("localId")
        idToken = json.optString("idToken")
        expiresAt = System.currentTimeMillis() + (json.optString("expiresIn").toLongOrNull() ?: 3600L) * 1000L - 60_000L
        loadRole(uid)
        store.refreshToken = json.optString("refreshToken")
        store.email = json.optString("email")
    }

    /** The owner is admin; anyone else must be an active moderator in staff/{uid}. */
    private fun loadRole(uid: String) {
        if (uid == ADMIN_UID) {
            store.role = "admin"; store.perms = emptySet(); return
        }
        val r = http(
            "https://firestore.googleapis.com/v1/projects/cinedor-60aac/databases/(default)/documents/staff/$uid",
            headers = mapOf("Authorization" to "Bearer " + (idToken ?: ""))
        )
        val f = if (r.code in 200..299) JSONObject(r.body).optJSONObject("fields") else null
        val active = f?.optJSONObject("active")?.optBoolean("booleanValue") == true
        if (f == null || !active) {
            idToken = null
            throw AdminException("This account doesn't have access to Cinedor Admin.")
        }
        val arr = f.optJSONObject("perms")?.optJSONObject("arrayValue")?.optJSONArray("values")
        val perms = if (arr == null) emptySet() else (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.optString("stringValue") }.toSet()
        store.role = "moderator"
        store.perms = perms
        store.displayName = f.optJSONObject("name")?.optString("stringValue").orEmpty()
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
        idToken = json.optString("id_token")
        expiresAt = System.currentTimeMillis() + (json.optString("expires_in").toLongOrNull() ?: 3600L) * 1000L - 60_000L
        store.refreshToken = json.optString("refresh_token")
        loadRole(json.optString("user_id"))
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

    suspend fun send(title: String, body: String, image: String, link: String, topic: String = "all", images: List<String> = emptyList()): String = withContext(Dispatchers.IO) {
        call(
            "/send", "POST",
            JSONObject().put("title", title).put("body", body).put("image", image).put("link", link).put("topic", topic)
                .put("images", JSONArray(images))
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
class ConfigRepo(private val store: AdminStore, private val worker: WorkerApi, private val auth: AdminAuth) {
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
            var r = http("https://api.github.com/repos/$REPO/contents/$PATH?ref=$ref", headers = ghHeaders())
            if (r.code == 401 || r.code == 403) {
                r = http(
                    "https://api.github.com/repos/$REPO/contents/$PATH?ref=$ref",
                    headers = mapOf("Accept" to "application/vnd.github+json", "User-Agent" to "Cinedor-Admin")
                )
            }
            if (r.code in 200..299) {
                val j = JSONObject(r.body)
                val bytes = android.util.Base64.decode(j.optString("content").replace("\n", ""), android.util.Base64.DEFAULT)
                return Found(ref, j.optString("sha"), String(bytes, Charsets.UTF_8))
            }
        }
        throw AdminException("Settings file not found")
    }

    private val fsDoc = "https://firestore.googleapis.com/v1/projects/cinedor-60aac/databases/(default)/documents/config/app"

    /** Settings stored in Firebase (config/app) — no token or server needed. */
    private fun loadFirestore(): String? {
        val r = http(fsDoc, headers = mapOf("Authorization" to "Bearer " + auth.token()))
        if (r.code !in 200..299) return null
        return JSONObject(r.body).optJSONObject("fields")?.optJSONObject("json")?.optString("stringValue")
            ?.takeIf { it.isNotBlank() }
    }

    private fun saveFirestore(text: String) {
        val now = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
            .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(java.util.Date())
        val fields = JSONObject()
            .put("json", JSONObject().put("stringValue", text))
            .put("updatedAt", JSONObject().put("timestampValue", now))
        val r = http(
            "$fsDoc?updateMask.fieldPaths=json&updateMask.fieldPaths=updatedAt", "POST",
            JSONObject().put("fields", fields).toString(),
            headers = mapOf("Authorization" to "Bearer " + auth.token(), "X-HTTP-Method-Override" to "PATCH")
        )
        if (r.code !in 200..299) throw AdminException(
            if (r.code == 403) "Firebase blocked saving. Publish the Cinedor Firestore rules."
            else if (r.code == 404) "Create the Firestore database in Firebase first."
            else "Firebase save failed (${r.code})"
        )
    }

    /** Built-in starting settings, used when nothing has been published yet. */
    private val defaultConfig = "{\"premium\": {\"enabled\": false, \"id\": \"premium-1\", \"title\": \"Go Premium\", \"message\": \"Ad-free streaming, faster downloads and early access to new features.\", \"perks\": [\"No ads\", \"Super fast downloads\", \"4K & 1080p\", \"Early access\"], \"price\": \"\", \"buttonText\": \"Get Premium\", \"buttonUrl\": \"\", \"showEveryHours\": 24}, \"announcement\": {\"enabled\": false, \"id\": \"notice-1\", \"title\": \"Welcome to Cinedor\", \"message\": \"New movies and series are added every day.\", \"buttonText\": \"OK\", \"buttonUrl\": \"\"}, \"maintenance\": {\"enabled\": false, \"message\": \"Cinedor is under maintenance. Please come back in a little while.\"}, \"community\": {\"enabled\": false, \"id\": \"community-1\", \"title\": \"🎉 Join the Cinedor family\", \"message\": \"Get new movies, updates and requests first on our channels.\", \"buttonText\": \"Join our Telegram channel\", \"buttonUrl\": \"\", \"secondaryText\": \"Visit our Facebook page\", \"secondaryUrl\": \"\", \"showEveryHours\": 24}, \"sharePrompt\": {\"enabled\": false, \"id\": \"share-1\", \"title\": \"Enjoying the movie? 🍿\", \"message\": \"\", \"afterMinutes\": 20, \"perks\": [\"✨ Full HD\", \"⚡ Buffer free\", \"🎁 100% free\"], \"shareMessage\": \"\", \"buttonUrl\": \"\", \"showEveryHours\": 24}, \"audioDubs\": {\"enabled\": true, \"title\": \"Audio & Dubs\"}, \"minVersionCode\": 0, \"spotlight\": {\"enabled\": false, \"id\": \"spot-1\", \"title\": \"\", \"message\": \"\", \"image\": \"\", \"buttonText\": \"Watch now\", \"buttonUrl\": \"\", \"showEveryHours\": 24}}"

    /** Public copy of the GitHub file (no API limits, no token). */
    private fun loadRaw(): String? {
        for (ref in REFS) {
            val r = http("https://raw.githubusercontent.com/$REPO/$ref/$PATH?t=" + System.currentTimeMillis() / 60000)
            if (r.code in 200..299 && runCatching { JSONObject(r.body) }.isSuccess) return r.body
        }
        return null
    }

    suspend fun load(): Pair<String, String> = withContext(Dispatchers.IO) {
        runCatching { loadFirestore() }.getOrNull()?.let { return@withContext "Firebase" to it }
        runCatching { find() }.getOrNull()?.let { return@withContext it.ref to it.text }
        runCatching { loadRaw() }.getOrNull()?.let { return@withContext "GitHub" to it }
        // Nothing published yet: start from the default settings; Publish saves them to Firebase.
        "new" to JSONObject(defaultConfig).toString(2)
    }

    val canSave: Boolean get() = true

    suspend fun save(text: String): String = withContext(Dispatchers.IO) {
        JSONObject(text)
        saveFirestore(text)
        // Older app versions read the GitHub file: update it too when a token is set (optional).
        if (store.githubToken.isNotBlank()) runCatching { saveGithub(text) }
        "Firebase"
    }

    private fun saveGithub(text: String): String {
        val f = find()
        val body = JSONObject()
            .put("message", "Update Cinedor settings from Cinedor Admin")
            .put("content", android.util.Base64.encodeToString(text.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP))
            .put("sha", f.sha)
            .put("branch", f.ref)
        val r = http("https://api.github.com/repos/$REPO/contents/$PATH", "PUT", body.toString(), headers = ghHeaders())
        if (r.code !in 200..299) throw AdminException("GitHub save failed (${r.code})")
        return f.ref
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
    val rating: Double,
    /** Cinedor catalog identity (empty for TMDB items): used to open the exact page in the app. */
    val cid: String = "",
    val src: String = "",
    val path: String = ""
) {
    val inCinedor: Boolean get() = cid.isNotBlank()

    /** Link the Cinedor app understands: opens this exact title (or searches the name for TMDB items). */
    fun appLink(): String {
        if (!inCinedor) return title
        fun e(v: String) = URLEncoder.encode(v, "UTF-8")
        return "cinedor://item?id=${e(cid)}&src=${e(src)}&path=${e(path)}&type=${e(type)}&t=${e(title)}&p=${e(poster)}"
    }
}

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


/** Titles straight from the Cinedor app's own catalog (same server the app uses). */
object CinedorCatalog {
    private const val BASE = "https://inepulse-edge.siamvai9474737.workers.dev"
    private const val KEY = "cp_live_c77b19611c9dcbf67de5a574b40adce88822e62a"

    private fun JSONObject.str(vararg keys: String): String {
        for (k in keys) {
            val v = opt(k)
            if (v is String && v.isNotBlank() && v != "null") return v.trim()
            if (v is Number) return v.toString()
        }
        return ""
    }

    private fun JSONObject.img(vararg keys: String): String {
        for (k in keys) {
            when (val v = opt(k)) {
                is String -> if (v.startsWith("http")) return v.trim()
                is JSONObject -> v.str("url", "src", "image", "cover", "poster", "thumbnail", "high", "original").let { if (it.startsWith("http")) return it }
            }
        }
        return ""
    }

    private fun item(o: JSONObject): PosterItem? {
        val obj = o.optJSONObject("subject") ?: o.optJSONObject("item") ?: o
        val title = obj.str("title", "name", "subjectTitle", "movieTitle")
        if (title.isBlank()) return null
        val poster = obj.img("poster", "posterUrl", "cover", "coverUrl", "thumbnail", "thumb", "image", "img", "verticalCover", "pic")
        if (poster.isBlank()) return null
        val backdrop = obj.img("backdrop", "backdropUrl", "banner", "bannerUrl", "horizontalCover", "still").ifBlank { poster }
        val id = obj.str("subjectId", "id", "movieId", "slug", "detailPath", "url").ifBlank { title }
        val rawType = obj.str("subjectType", "type", "mediaType", "category", "contentType")
        val type = when (rawType) { "1" -> "Movie"; "2" -> "Series"; else -> if (rawType.contains("tv", true) || rawType.contains("series", true)) "Series" else "Movie" }
        val year = obj.str("year", "releaseYear").ifBlank { obj.str("releaseDate", "release_date").take(4) }
        return PosterItem(
            id = id.hashCode().toLong(),
            title = title,
            year = year,
            type = type,
            poster = poster,
            backdrop = backdrop,
            overview = obj.str("description", "overview", "synopsis", "plot", "intro"),
            rating = obj.str("imdbRatingValue", "rating", "score").toDoubleOrNull() ?: 0.0,
            cid = id,
            src = obj.str("source", "provider", "site").ifBlank { "moviebox" },
            path = obj.str("detailPath", "url", "link", "slug")
        )
    }

    private fun parse(body: String): List<PosterItem> {
        val out = LinkedHashMap<String, PosterItem>()
        fun walk(node: Any?, depth: Int) {
            if (node == null || depth > 6) return
            when (node) {
                is JSONArray -> for (i in 0 until node.length()) {
                    val o = node.opt(i)
                    if (o is JSONObject) {
                        val pi = item(o)
                        if (pi != null) out.putIfAbsent(pi.title.lowercase() + pi.cid, pi) else walk(o, depth + 1)
                    } else walk(o, depth + 1)
                }
                is JSONObject -> node.keys().forEach { k -> walk(node.opt(k), depth + 1) }
            }
        }
        runCatching { walk(if (body.trim().startsWith("[")) JSONArray(body) else JSONObject(body), 0) }
        return out.values.toList()
    }

    private fun get(path: String): List<PosterItem> = runCatching {
        val r = http(BASE + path, headers = mapOf("x-api-key" to KEY, "Accept" to "application/json"))
        if (r.code in 200..299) parse(r.body) else emptyList()
    }.getOrDefault(emptyList())

    suspend fun trending(): List<PosterItem> = withContext(Dispatchers.IO) { get("/api/v1/trending?source=all") }
    suspend fun latest(): List<PosterItem> = withContext(Dispatchers.IO) { get("/api/v1/updates-24h?source=all") }
    suspend fun search(q: String): List<PosterItem> = withContext(Dispatchers.IO) {
        if (q.isBlank()) emptyList() else get("/api/v1/search?q=" + URLEncoder.encode(q, "UTF-8") + "&source=all")
    }
}


// ------------------------------------------------------------------ team (moderators) & premium codes

data class StaffMember(val uid: String, val name: String, val email: String, val perms: Set<String>, val active: Boolean)
data class PremiumCode(val code: String, val days: Int, val used: Boolean, val usedBy: String, val note: String, val createdAt: String)

/** What a moderator can be allowed to do. */
val STAFF_PERMS = listOf(
    "notify" to "Send notifications",
    "popups" to "Edit popups & features",
    "ads" to "Manage ads",
    "feedback" to "Answer feedback",
    "users" to "See users & countries",
    "premium" to "Make Premium codes"
)

class TeamAdmin(private val store: AdminStore, private val auth: AdminAuth) {
    private val docs = "https://firestore.googleapis.com/v1/projects/cinedor-60aac/databases/(default)/documents"
    private fun bearer() = mapOf("Authorization" to "Bearer " + auth.token())
    private fun s(v: String) = JSONObject().put("stringValue", v)

    private fun ok(r: HttpResult) {
        if (r.code in 200..299) return
        val msg = runCatching { JSONObject(r.body).optJSONObject("error")?.optString("message") }.getOrNull().orEmpty()
        throw AdminException(if (r.code == 403) "Firebase blocked this. Publish the latest Cinedor rules." else msg.ifBlank { "Error ${r.code}" })
    }

    suspend fun staff(): List<StaffMember> = withContext(Dispatchers.IO) {
        val r = http("$docs/staff?pageSize=100", headers = bearer())
        if (r.code == 404) return@withContext emptyList()
        ok(r)
        val arr = JSONObject(r.body).optJSONArray("documents") ?: return@withContext emptyList()
        (0 until arr.length()).mapNotNull { i ->
            val d = arr.optJSONObject(i) ?: return@mapNotNull null
            val f = d.optJSONObject("fields")
            val pa = f?.optJSONObject("perms")?.optJSONObject("arrayValue")?.optJSONArray("values")
            StaffMember(
                uid = d.optString("name").substringAfterLast('/'),
                name = f?.optJSONObject("name")?.optString("stringValue").orEmpty(),
                email = f?.optJSONObject("email")?.optString("stringValue").orEmpty(),
                perms = if (pa == null) emptySet() else (0 until pa.length()).mapNotNull { pa.optJSONObject(it)?.optString("stringValue") }.toSet(),
                active = f?.optJSONObject("active")?.optBoolean("booleanValue") == true
            )
        }
    }

    private fun saveStaff(m: StaffMember) {
        val perms = JSONArray(); m.perms.forEach { perms.put(s(it)) }
        val fields = JSONObject()
            .put("name", s(m.name)).put("email", s(m.email))
            .put("perms", JSONObject().put("arrayValue", JSONObject().put("values", perms)))
            .put("active", JSONObject().put("booleanValue", m.active))
        ok(http("$docs/staff/${m.uid}", "POST", JSONObject().put("fields", fields).toString(),
            headers = bearer() + ("X-HTTP-Method-Override" to "PATCH")))
    }

    suspend fun update(m: StaffMember) = withContext(Dispatchers.IO) { saveStaff(m) }

    /** Creates the moderator's login (email + password) and gives access. */
    suspend fun addModerator(name: String, email: String, password: String, perms: Set<String>): StaffMember = withContext(Dispatchers.IO) {
        if (password.length < 6) throw AdminException("Password must be at least 6 characters")
        val r = http(
            "https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=${store.apiKey}", "POST",
            JSONObject().put("email", email.trim()).put("password", password).put("returnSecureToken", true).toString()
        )
        val j = runCatching { JSONObject(r.body) }.getOrDefault(JSONObject())
        val uid = if (r.code in 200..299) j.optString("localId") else {
            val code = j.optJSONObject("error")?.optString("message").orEmpty()
            throw AdminException(if ("EMAIL_EXISTS" in code) "This email already has an account. Ask them for their UID or use another email." else code.ifBlank { "Couldn't create the account" })
        }
        val m = StaffMember(uid, name.trim(), email.trim(), perms, true)
        saveStaff(m)
        m
    }

    suspend fun remove(uid: String) = withContext(Dispatchers.IO) { ok(http("$docs/staff/$uid", "DELETE", headers = bearer())) }

    // ---- premium codes
    private fun newCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val rnd = java.security.SecureRandom()
        fun part() = (1..4).map { chars[rnd.nextInt(chars.length)] }.joinToString("")
        return "CINE-${part()}-${part()}"
    }

    suspend fun makeCodes(days: Int, count: Int, note: String): List<String> = withContext(Dispatchers.IO) {
        val now = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
            .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(java.util.Date())
        (1..count.coerceIn(1, 20)).map {
            val code = newCode()
            val fields = JSONObject()
                .put("days", JSONObject().put("integerValue", days.toString()))
                .put("used", JSONObject().put("booleanValue", false))
                .put("usedBy", s(""))
                .put("note", s(note))
                .put("createdBy", s(store.email))
                .put("createdAt", JSONObject().put("timestampValue", now))
            ok(http("$docs/codes?documentId=$code", "POST", JSONObject().put("fields", fields).toString(), headers = bearer()))
            code
        }
    }

    suspend fun codes(): List<PremiumCode> = withContext(Dispatchers.IO) {
        val q = JSONObject().put(
            "structuredQuery", JSONObject()
                .put("from", JSONArray().put(JSONObject().put("collectionId", "codes")))
                .put("orderBy", JSONArray().put(JSONObject().put("field", JSONObject().put("fieldPath", "createdAt")).put("direction", "DESCENDING")))
                .put("limit", 60)
        )
        val r = http("$docs:runQuery", "POST", q.toString(), headers = bearer())
        ok(r)
        val arr = JSONArray(r.body)
        (0 until arr.length()).mapNotNull { i ->
            val d = arr.optJSONObject(i)?.optJSONObject("document") ?: return@mapNotNull null
            val f = d.optJSONObject("fields")
            PremiumCode(
                code = d.optString("name").substringAfterLast('/'),
                days = f?.optJSONObject("days")?.optString("integerValue")?.toIntOrNull() ?: 0,
                used = f?.optJSONObject("used")?.optBoolean("booleanValue") == true,
                usedBy = f?.optJSONObject("usedBy")?.optString("stringValue").orEmpty(),
                note = f?.optJSONObject("note")?.optString("stringValue").orEmpty(),
                createdAt = f?.optJSONObject("createdAt")?.optString("timestampValue").orEmpty()
            )
        }
    }

    suspend fun deleteCode(code: String) = withContext(Dispatchers.IO) { ok(http("$docs/codes/$code", "DELETE", headers = bearer())) }
}
