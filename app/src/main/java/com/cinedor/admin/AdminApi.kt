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

    fun historyJson(): String = prefs.getString("history", "[]").orEmpty()
    fun saveHistory(json: String) = prefs.edit().putString("history", json).apply()
}

private data class HttpResult(val code: Int, val body: String)

private fun http(
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

    suspend fun send(title: String, body: String, image: String, link: String): String = withContext(Dispatchers.IO) {
        call(
            "/send", "POST",
            JSONObject().put("title", title).put("body", body).put("image", image).put("link", link).put("topic", "all")
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
