package io.github.vivekg7.galla.ui

import android.app.PendingIntent
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.vivekg7.galla.App
import io.github.vivekg7.galla.app
import io.github.vivekg7.galla.data.Auth
import io.github.vivekg7.galla.data.Drive
import io.github.vivekg7.galla.data.Setting
import io.github.vivekg7.galla.data.ShopInfo
import io.github.vivekg7.galla.data.SignInNeeded
import io.github.vivekg7.galla.data.Sync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The welcome screen's sign-in, kept in [App] rather than in the screen:
 * joining a shop brings its name in halfway, which swaps the welcome screen
 * for the shop, and the join must not be cancelled with the screen.
 */
class Welcome {
    /** What it is doing, shown instead of the buttons; null when idle. */
    val busy = MutableStateFlow<String?>(null)
    val error = MutableStateFlow<String?>(null)

    /**
     * Signed in: join the account's shop if another phone set it up, taking
     * its products, stock and bills, or become its first phone. This phone
     * has nothing of its own yet, so there is nothing to ask.
     */
    fun join(
        app: App,
        token: String,
    ) = app.scope.launch {
        error.value = null
        busy.value = "Signing in…"
        try {
            withContext(Dispatchers.IO) {
                val drive = Drive(app, null, token)
                val email = drive.email()
                val phones = app.sync.phones(drive.list(null)).size
                busy.value = if (phones == 0) "Setting up sync for $email…" else "Getting the shop's data from $email…"
                app.sync.join(drive, email, Build.MODEL)
            }
        } catch (e: Exception) {
            error.value = "Could not set up sync: ${e.message ?: "no internet"}. Try again, or continue without it."
            // If the shop's data had already come in, the shop is on screen, not this one: Shop shows it there.
            if (app.shop.value
                    ?.name
                    .isNullOrBlank()
                    .not()
            ) {
                app.sync.status.value = Sync.Status(error = "Sync was not turned on: ${e.message}. Sign in again to retry.")
            }
        } finally {
            busy.value = null
        }
    }

    companion object {
        /** Set when the person chose to keep the shop on this phone only. Never synced: it isn't a `shop.` setting. */
        const val LOCAL = "app.local"
    }
}

/** First run: Google sign-in first, so the shop's phones start in step; this phone alone if not. */
@Composable
fun WelcomeScreen() {
    val app = LocalContext.current.app
    val scope = rememberCoroutineScope()
    val busy by app.welcome.busy.collectAsState()
    val error by app.welcome.error.collectAsState()
    val signIn = rememberSignIn { app.welcome.join(app, it) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Text("Galla", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text(
            "Bills, stock and UPI payments for your shop. Works without internet.",
            Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.weight(1f))
        Text(
            "Sign in with the shop's Google account to keep all the shop's phones in step. " +
                "If the shop already uses Galla on another phone, this phone gets its products, stock and bills.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        error?.let {
            Text(
                it,
                Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }
        val doing = busy
        if (doing != null) {
            CircularProgressIndicator(Modifier.padding(top = 24.dp))
            Text(doing, Modifier.padding(top = 12.dp, bottom = 64.dp), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        } else {
            Button(onClick = { signIn(null) }, Modifier.fillMaxWidth().padding(top = 16.dp).height(52.dp)) { Text("Sign in with Google") }
            TextButton(
                onClick = { scope.launch { app.dao.putSetting(Setting(Welcome.LOCAL, "1")) } },
                modifier = Modifier.padding(top = 16.dp),
            ) { Text("Continue without signing in") }
            Text(
                "The shop's data then stays on this phone only: no other phone sees it, and Galla sends nothing online. " +
                    "You can sign in later from Shop.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** After the welcome screen, for a new shop: the details the slip prints. [account] is the synced account, or null for this phone only. */
@Composable
fun SetupScreen(
    saved: ShopInfo,
    account: String?,
) {
    val app = LocalContext.current.app
    val scope = rememberCoroutineScope()
    // On this phone only, back returns to the choice; once syncing, sync is turned off in Shop.
    BackHandler(enabled = account == null) { scope.launch { app.dao.deleteSetting(Welcome.LOCAL) } }

    Column(Modifier.fillMaxSize()) {
        Bar("Your shop")
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                (
                    if (account != null) {
                        "Syncing with $account. This is the shop's first phone, so its details start here; the other phones get them when they sign in. "
                    } else {
                        "Kept on this phone only. To share it with the shop's other phones later, sign in from Shop. "
                    }
                ) + "The slip prints these at the top, and with a UPI ID each bill shows a QR code for its total.",
                Modifier.padding(bottom = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
            ShopForm(saved, "Start selling")
        }
    }
}

/**
 * Google sign-in for Drive. Call the result with null to sign in, asking
 * through Google's own screen only if it must, or with the screen a
 * [SignInNeeded] carries. [onToken] gets the access token.
 */
@Composable
fun rememberSignIn(onToken: (String) -> Unit): (PendingIntent?) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val then by rememberUpdatedState(onToken)
    val ask =
        rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            runCatching { Auth.tokenFrom(context.app, result.data) }
                .onSuccess { then(it) }
                .onFailure { context.toast("Not signed in") }
        }
    return { intent ->
        if (intent != null) {
            ask.launch(IntentSenderRequest.Builder(intent).build())
        } else {
            scope.launch {
                try {
                    then(Auth.token(context.app, null))
                } catch (e: SignInNeeded) {
                    ask.launch(IntentSenderRequest.Builder(e.intent).build())
                } catch (e: Exception) {
                    context.toast("Google sign-in failed: ${e.message}")
                }
            }
        }
    }
}
