package io.github.vivekg7.galla.ui

import androidx.activity.compose.rememberLauncherForActivityResult
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
import io.github.vivekg7.galla.data.Backup
import io.github.vivekg7.galla.data.ShopInfo
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

    var name by rememberSaveable { mutableStateOf(saved.name) }
    var address by rememberSaveable { mutableStateOf(saved.address) }
    var phone by rememberSaveable { mutableStateOf(saved.phone) }
    var upi by rememberSaveable { mutableStateOf(saved.upiId) }
    var payee by rememberSaveable { mutableStateOf(saved.payee) }
    var footer by rememberSaveable { mutableStateOf(saved.footer) }
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
                        app.dao.saveShop(edited)
                        context.toast("Saved")
                    }
                },
                enabled = edited != saved && upiError == null,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(52.dp),
            ) { Text("Save") }

            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            Text("Backup", style = MaterialTheme.typography.titleSmall)
            val last = Backup.lastExport(settings)
            Text(
                "Your shop's data is only on this phone. Save a backup file regularly, to a pen drive, " +
                    "or to Google Drive when you are online. " +
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
                "Galla ${BuildConfig.VERSION_NAME}. Works without internet: the app has no permission to go online.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    restoreFrom?.let { uri ->
        Confirm(
            "Restore this backup?",
            "Everything in Galla now (products, stock, bills, shop details) is replaced by the backup, and the app restarts. " +
                "A copy of the current data is kept on the phone first.",
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
