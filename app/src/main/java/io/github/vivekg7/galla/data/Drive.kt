package io.github.vivekg7.galla.data

import android.accounts.Account
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Drive's hidden app folder (appDataFolder), over Drive's REST API. Only Galla
 * sees it: it is not in the account's Drive file list, and the permission
 * asked for (drive.appdata) reaches nothing else in Drive. The phones meet
 * there by all signing in to the shop's one Google account.
 */
class Drive(
    private val context: Context,
    private val account: String?,
    private var token: String? = null,
) : Remote {
    override suspend fun list(after: String?): List<RemoteFile> {
        val files = mutableListOf<RemoteFile>()
        var page: String? = null
        do {
            val query =
                "spaces=appDataFolder&pageSize=1000&orderBy=createdTime&fields=nextPageToken,files(id,name,createdTime)" +
                    (after?.let { "&q=" + enc("createdTime > '$it'") } ?: "") + (page?.let { "&pageToken=" + enc(it) } ?: "")
            val json = JSONObject(call("GET", "$API/files?$query") { it.bufferedReader().readText() })
            val items = json.optJSONArray("files")
            for (i in 0 until (items?.length() ?: 0)) {
                val f = items!!.getJSONObject(i)
                files += RemoteFile(f.getString("id"), f.getString("name"), f.getString("createdTime"))
            }
            page = json.optString("nextPageToken").ifEmpty { null }
        } while (page != null)
        return files
    }

    override suspend fun download(
        id: String,
        to: File,
    ) = call("GET", "$API/files/$id?alt=media") { input -> to.outputStream().use { input.copyTo(it) } }.let { }

    override suspend fun upload(
        name: String,
        from: File,
    ): RemoteFile {
        val boundary = "galla-" + System.nanoTime()
        val meta = JSONObject().put("name", name).put("parents", org.json.JSONArray().put("appDataFolder"))
        val head = "--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$meta\r\n--$boundary\r\nContent-Type: application/octet-stream\r\n\r\n"
        val tail = "\r\n--$boundary--\r\n"
        val body = { out: OutputStream ->
            out.write(head.toByteArray())
            from.inputStream().use { it.copyTo(out) }
            out.write(tail.toByteArray())
        }
        val json =
            call("POST", "$UPLOAD/files?uploadType=multipart&fields=id,name,createdTime", "multipart/related; boundary=$boundary", body) {
                JSONObject(it.bufferedReader().readText())
            }
        return RemoteFile(json.getString("id"), json.getString("name"), json.getString("createdTime"))
    }

    override suspend fun delete(id: String) = call("DELETE", "$API/files/$id") { }

    /** The signed-in account, to sign in to the same one next time without asking. */
    suspend fun email(): String =
        JSONObject(
            call("GET", "$API/about?fields=user(emailAddress)") {
                it.bufferedReader().readText()
            },
        ).getJSONObject("user").getString("emailAddress")

    private suspend fun <T> call(
        method: String,
        url: String,
        type: String? = null,
        body: ((OutputStream) -> Unit)? = null,
        read: (InputStream) -> T,
    ): T =
        withContext(Dispatchers.IO) {
            for (attempt in 0..1) {
                val auth = token ?: Auth.token(context, account).also { token = it }
                val c = URL(url).openConnection() as HttpURLConnection
                try {
                    c.requestMethod = method
                    c.connectTimeout = 20_000
                    c.readTimeout = 60_000
                    c.setRequestProperty("Authorization", "Bearer $auth")
                    if (body != null) {
                        c.doOutput = true
                        c.setRequestProperty("Content-Type", type)
                        c.setChunkedStreamingMode(0)
                        c.outputStream.use(body)
                    }
                    val code = c.responseCode
                    // An expired token: drop it from Google's cache and get a fresh one, once.
                    if (code == 401 && attempt == 0) {
                        Auth.clear(context, auth)
                        token = null
                        continue
                    }
                    if (code !in 200..299) throw IOException("Drive said $code: ${c.errorStream?.bufferedReader()?.readText()?.take(300)}")
                    return@withContext c.inputStream.use(read)
                } finally {
                    c.disconnect()
                }
            }
            error("Drive refused the sign-in twice")
        }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private companion object {
        const val API = "https://www.googleapis.com/drive/v3"
        const val UPLOAD = "https://www.googleapis.com/upload/drive/v3"
    }
}

/** Google sign-in for Drive through Play services: no client secret in the app, Google matches it by package and signing key. */
object Auth {
    private val scope = Scope("https://www.googleapis.com/auth/drive.appdata")

    private fun request(account: String?) =
        AuthorizationRequest
            .Builder()
            .setRequestedScopes(listOf(scope))
            .apply { if (account != null) setAccount(Account(account, "com.google")) }
            .build()

    /** A token without asking, or [SignInNeeded] with the screen that asks. */
    suspend fun token(
        context: Context,
        account: String?,
    ): String = tokenOf(Identity.getAuthorizationClient(context).authorize(request(account)).await())

    /** After the person went through [SignInNeeded.intent]. */
    fun tokenFrom(
        context: Context,
        data: Intent?,
    ): String = tokenOf(Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data))

    private fun tokenOf(result: AuthorizationResult): String {
        if (result.hasResolution()) throw SignInNeeded(result.pendingIntent!!)
        return requireNotNull(result.accessToken) { "Google gave no Drive access" }
    }

    suspend fun clear(
        context: Context,
        token: String,
    ) {
        runCatching { Identity.getAuthorizationClient(context).clearToken(ClearTokenRequest.builder().setToken(token).build()).await() }
    }

    private suspend fun <T> Task<T>.await(): T =
        suspendCancellableCoroutine { c ->
            addOnSuccessListener { c.resume(it) }
            addOnFailureListener { c.resumeWithException(it) }
        }
}
