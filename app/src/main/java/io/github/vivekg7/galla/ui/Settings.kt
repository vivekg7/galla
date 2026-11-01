package io.github.vivekg7.galla.ui

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.vivekg7.galla.BuildConfig
import io.github.vivekg7.galla.app
import io.github.vivekg7.galla.data.Auth
import io.github.vivekg7.galla.data.Backup
import io.github.vivekg7.galla.data.Db
import io.github.vivekg7.galla.data.Drive
import io.github.vivekg7.galla.data.Setting
import io.github.vivekg7.galla.data.ShopInfo
import io.github.vivekg7.galla.data.SignInNeeded
import io.github.vivekg7.galla.data.Sync
import io.github.vivekg7.galla.data.isUpiId
import io.github.vivekg7.galla.data.saveShop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val app = context.app
    val scope = rememberCoroutineScope()
    val saved = app.shop.collectAsState().value ?: return
    val settings by app.dao.settings().collectAsState(initial = emptyList())

    // Keyed on the saved details: another phone's edit, synced in, shows here.
    var name by rememberSaveable(saved) { mutableStateOf(saved.name) }
    var address by rememberSaveable(saved) { mutableStateOf(saved.address) }
    var phone by rememberSaveable(saved) { mutableStateOf(saved.phone) }
    var upi by rememberSaveable(saved) { mutableStateOf(saved.upiId) }
    var payee by rememberSaveable(saved) { mutableStateOf(saved.payee) }
    var footer by rememberSaveable(saved) { mutableStateOf(saved.footer) }
    var restoreFrom by remember { mutableStateOf<android.net.Uri?>(null) }
    val edited = ShopInfo(name.trim(), address.trim(), phone.trim(), upi.trim(), payee.trim(), footer.trim())
    val upiError = if (upi.isNotBlank() && !isUpiId(upi)) "Looks like name@bank, e.g. 9876543210@ybl" else null

    val export =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
            uri ?: return@rememberLauncherForActivityResult
            scope.launch {
                runCatching { withContext(Dispatchers.IO) { Backup.export(context, uri) } }
                    .onSuccess { context.toast("Backup saved") }
                    .onFailure { context.toast("Backup failed: ${it.message}") }
            }
        }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { restoreFrom = it }

    Column(Modifier.fillMaxSize()) {
        Bar("Shop")
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (saved.name.isBlank()) {
                Card(Modifier.fillMaxWidth().padding(bottom = 8.dp), colors = CardDefaults.cardColors(MaterialTheme.colorScheme.secondaryContainer)) {
                    Text(
                        "Welcome to Galla. Enter your shop's name for the slip, and your UPI ID so each bill shows a QR code for its total.",
                        Modifier.padding(16.dp),
                    )
                }
            }
            Text("On the slip", style = MaterialTheme.typography.titleSmall)
            Field("Shop name", name, { name = it })
            Field("Address", address, { address = it }, singleLine = false)
            Field("Phone", phone, { phone = it }, kind = Kind.Phone)
            Field("Message at the bottom", footer, { footer = it })

            Text("UPI payments", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
            Field("UPI ID", upi, { upi = it.trim() }, kind = Kind.Email, error = upiError, hint = "From your UPI app's profile, e.g. 9876543210@ybl")
            Field("Payee name", payee, { payee = it }, hint = "The name customers see when paying; your shop name if empty")

            Button(
                onClick = {
                    scope.launch {
                        app.dao.saveShop(edited, saved)
                        context.toast("Saved")
                    }
                },
                enabled = edited != saved && upiError == null,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(52.dp),
            ) { Text("Save") }

            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            SyncSection(settings)

            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            Text("Backup", style = MaterialTheme.typography.titleSmall)
            val last = Backup.lastExport(settings)
            Text(
                "Save a backup file regularly, to a pen drive or to Google Drive. " +
                    if (last == null) "No backup made yet." else "Last backup: ${SimpleDateFormat("d MMM yyyy, h:mm a", Locale.ENGLISH).format(Date(last))}.",
                style = MaterialTheme.typography.bodySmall,
                color = if (last == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    export.launch("galla-backup-${SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(Date())}.db")
                }, Modifier.weight(1f)) { Text("Save backup") }
                OutlinedButton(onClick = { pick.launch(arrayOf("*/*")) }, Modifier.weight(1f)) { Text("Restore") }
            }

            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            Text(
                "Galla ${BuildConfig.VERSION_NAME}. Works without internet; it goes online only to sync, when sync is on.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    restoreFrom?.let { uri ->
        Confirm(
            "Restore this backup?",
            "Everything in Galla now (products, stock, bills, shop details) is replaced by the backup, and the app restarts. " +
                "A copy of the current data is kept on the phone first. If sync is on, it is turned off.",
            "Restore",
            onConfirm = {
                scope.launch {
                    runCatching { withContext(Dispatchers.IO) { Backup.restore(context, uri) } }
                        .onFailure { context.toast(it.message ?: "Restore failed") }
                }
            },
            onDismiss = { restoreFrom = null },
        )
    }
}

/** What [SyncSection] holds between signing in and the person confirming. */
private class Joining(
    val drive: Drive,
    val email: String,
    val phones: Int,
)

/** Turning sync on and off, and how it is going (docs/plans/002_sync_between_phones.md). */
@Composable
private fun SyncSection(settings: List<Setting>) {
    val context = LocalContext.current
    val app = context.app
    val scope = rememberCoroutineScope()
    val status by app.sync.status.collectAsState()
    val unsent by app.dao.pendingCount().collectAsState(initial = 0)
    val values = settings.associate { it.key to it.value }
    val phone = values[Db.Triggers.DEVICE]?.toLongOrNull()
    var joining by remember { mutableStateOf<Joining?>(null) }
    var busy by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }

    fun syncNow() = scope.launch { app.sync.sync { Drive(app, it) } }

    // Signed in: see whether the shop has phones already, then ask.
    fun begin(token: String) =
        scope.launch {
            busy = true
            runCatching {
                withContext(Dispatchers.IO) {
                    val drive = Drive(app, null, token)
                    Joining(drive, drive.email(), app.sync.phones(drive.list(null)).size)
                }
            }.onSuccess { joining = it }
                .onFailure { context.toast("Could not reach Google Drive: ${it.message}") }
            busy = false
        }

    val signIn =
        rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            runCatching { Auth.tokenFrom(app, result.data) }
                .onSuccess { if (phone == null) begin(it) else syncNow() }
                .onFailure { context.toast("Not signed in") }
        }

    fun turnOn() =
        scope.launch {
            try {
                begin(Auth.token(app, null))
            } catch (e: SignInNeeded) {
                signIn.launch(IntentSenderRequest.Builder(e.intent).build())
            } catch (e: Exception) {
                context.toast("Google sign-in failed: ${e.message}")
            }
        }

    Text("Sync between phones", style = MaterialTheme.typography.titleSmall)
    if (phone == null) {
        Text(
            "Keep the shop's phones in step through Google Drive. Every phone signs in to the shop's one Google account, " +
                "and Galla keeps its data in a hidden folder there. Selling works without internet; changes go up when the phone is online.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        status.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        Button(onClick = ::turnOn, enabled = !busy, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text(if (busy) "Checking Google Drive…" else "Turn on sync")
        }
    } else {
        val last = values[Sync.LAST]?.toLongOrNull()
        Text(
            (if (phone == 0L) "This is the shop's first phone" else "This is phone $phone; its bills are numbered $phone-1, $phone-2…") +
                ", syncing with ${values[Sync.ACCOUNT]}. " +
                (if (last == null) "Not synced yet." else "Last synced ${SimpleDateFormat("d MMM, h:mm a", Locale.ENGLISH).format(Date(last))}.") +
                (if (unsent > 0) " Changes waiting to go up: $unsent." else ""),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        status.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val intent = status.signIn
            if (intent != null) {
                Button(onClick = { signIn.launch(IntentSenderRequest.Builder(intent).build()) }, Modifier.weight(1f)) { Text("Sign in again") }
            } else {
                Button(onClick = { syncNow() }, enabled = !status.running, modifier = Modifier.weight(1f)) {
                    Text(if (status.running) "Syncing…" else "Sync now")
                }
            }
            OutlinedButton(onClick = { leaving = true }, Modifier.weight(1f)) { Text("Turn off") }
        }
    }

    joining?.let { j ->
        Confirm(
            if (j.phones == 0) "Make this the shop's first phone?" else "Join the shop's phones?",
            if (j.phones == 0) {
                "Galla uploads this phone's products, bills and shop details to ${j.email}'s Google Drive. " +
                    "Then turn on sync on the other phones, signed in to the same account."
            } else {
                "${j.email} already has ${j.phones} phone${if (j.phones == 1) "" else "s"} syncing. " +
                    "This phone's products, bills and shop details are replaced by the shop's from Drive. " +
                    "A copy of what is here now is kept on the phone."
            },
            if (j.phones == 0) "Upload" else "Join",
            onConfirm = {
                scope.launch {
                    busy = true
                    runCatching { withContext(Dispatchers.IO) { app.sync.join(j.drive, j.email, Build.MODEL) } }
                        .onSuccess { context.toast(if (it == 0L) "Sync is on" else "Sync is on: this is phone $it") }
                        .onFailure { context.toast("Sync not turned on: ${it.message}") }
                    busy = false
                }
            },
            onDismiss = { joining = null },
        )
    }
    if (leaving) {
        Confirm(
            "Turn off sync?",
            "This phone's data stays here, but stops syncing. Turning sync on again replaces it with the shop's data from Drive." +
                if (unsent > 0) " $unsent changes on this phone have not gone up to Drive yet; sync first if you are online." else "",
            "Turn off",
            onConfirm = { scope.launch { app.sync.leave() } },
            onDismiss = { leaving = false },
        )
    }
}
