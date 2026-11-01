package io.github.vivekg7.galla.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

fun Context.toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Bar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = { Text(title, maxLines = 1) },
        navigationIcon = {
            if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.Back, "Back") }
        },
        actions = actions,
        // The Shell's Scaffold has already made room for the status bar.
        windowInsets = WindowInsets(0),
    )
}

enum class Kind(
    val keyboard: KeyboardType,
) {
    Text(KeyboardType.Text),
    Money(KeyboardType.Decimal),
    Decimal(KeyboardType.Decimal),
    Whole(KeyboardType.Number),
    Phone(KeyboardType.Phone),
    Email(KeyboardType.Email),
}

@Composable
fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    kind: Kind = Kind.Text,
    error: String? = null,
    hint: String? = null,
    suffix: String? = null,
    singleLine: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        // Full width of whatever room it is given: the screen, or its share of a Row.
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        prefix = if (kind == Kind.Money) ({ Text("₹") }) else null,
        suffix = suffix?.let { { Text(it) } },
        isError = error != null,
        supportingText = (error ?: hint)?.let { { Text(it) } },
        singleLine = singleLine,
        trailingIcon = trailing,
        keyboardOptions =
            KeyboardOptions(
                keyboardType = kind.keyboard,
                capitalization = if (kind == Kind.Text) KeyboardCapitalization.Words else KeyboardCapitalization.None,
                imeAction = if (singleLine) ImeAction.Next else ImeAction.Default,
            ),
    )
}

@Composable
fun Confirm(
    title: String,
    text: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                onConfirm()
            }) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun Empty(
    title: String,
    body: String,
) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Text(
                body,
                Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
