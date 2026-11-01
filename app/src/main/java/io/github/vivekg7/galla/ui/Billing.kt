package io.github.vivekg7.galla.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.vivekg7.galla.app
import io.github.vivekg7.galla.data.CartLine
import io.github.vivekg7.galla.data.Measure
import io.github.vivekg7.galla.data.Product
import io.github.vivekg7.galla.data.lineAmount
import io.github.vivekg7.galla.data.paiseField
import io.github.vivekg7.galla.data.parsePaise
import io.github.vivekg7.galla.data.parseQty
import io.github.vivekg7.galla.data.qtyField
import io.github.vivekg7.galla.data.qtyForAmount
import io.github.vivekg7.galla.data.qtyText
import io.github.vivekg7.galla.data.rupees
import kotlinx.coroutines.launch

/** What a quantity dialog is editing: a new item for the bill, or a line already on it. */
private data class QtyEdit(
    val name: String,
    val unit: Measure,
    val price: Long,
    val qty: Long,
    val product: Product? = null,
    val line: Int? = null,
    val fixed: Long? = null,
)

/**
 * Making a bill: scan items into the cart, or find them by name; weigh loose
 * ones; charge. The cart survives leaving this screen (App.cart).
 */
@Composable
fun BillScreen() {
    val app = LocalContext.current.app
    val nav = LocalNav.current
    val cart = app.cart
    val scope = rememberCoroutineScope()
    var cameraOn by rememberSaveable { mutableStateOf(true) }
    var qtyEdit by remember { mutableStateOf<QtyEdit?>(null) }
    var picking by remember { mutableStateOf(false) }
    var custom by remember { mutableStateOf<String?>(null) }
    var unknown by remember { mutableStateOf<String?>(null) }
    var editDiscount by remember { mutableStateOf(false) }
    var clearing by remember { mutableStateOf(false) }
    var charging by remember { mutableStateOf(false) }

    fun add(product: Product) {
        if (product.unit.loose) {
            qtyEdit = QtyEdit(product.name, product.unit, product.price, 0, product = product)
        } else {
            cart.add(product, 1000)
        }
    }

    fun onCode(code: String) {
        scope.launch {
            val product = app.dao.byBarcode(code)
            if (product == null) unknown = code else add(product)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Bar("New bill") {
            IconButton(onClick = { cameraOn = !cameraOn }) {
                Icon(if (cameraOn) Icons.CameraOff else Icons.Barcode, if (cameraOn) "Turn camera off" else "Scan")
            }
            if (cart.lines.isNotEmpty()) TextButton(onClick = { clearing = true }) { Text("Clear") }
        }
        if (cameraOn) {
            Scanner(
                ::onCode,
                Modifier.fillMaxWidth().height(170.dp).padding(horizontal = 12.dp),
                paused = qtyEdit != null || unknown != null || picking || custom != null || charging,
            )
        }
        Row(Modifier.fillMaxWidth().padding(12.dp, 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { picking = true }, Modifier.weight(1f)) {
                Icon(Icons.Search, null)
                Spacer(Modifier.width(6.dp))
                Text("Find item")
            }
            OutlinedButton(onClick = { custom = "" }, Modifier.weight(1f)) {
                Icon(Icons.Add, null)
                Spacer(Modifier.width(6.dp))
                Text("Other item")
            }
        }
        HorizontalDivider()
        Box(Modifier.weight(1f)) {
            if (cart.lines.isEmpty()) {
                Empty("Scan an item to start", "Or find it by name. Loose items ask for a weight or an amount.")
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    itemsIndexed(cart.lines, key = { i, line -> "${line.productId ?: "x$i"}:${line.name}" }) { i, line ->
                        CartRow(
                            line,
                            onTap = { qtyEdit = QtyEdit(line.name, line.unit, line.price, line.qty, line = i, fixed = line.fixed) },
                            onStep = { step -> cart.setQty(i, line.qty + step) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
        Surface(tonalElevation = 3.dp) {
            Row(Modifier.fillMaxWidth().padding(16.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).clickable(enabled = cart.lines.isNotEmpty()) { editDiscount = true }) {
                    Text("${cart.lines.size} item${if (cart.lines.size == 1) "" else "s"}", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        if (cart.discount > 0) "Discount − ${rupees(cart.discount)}" else "Add discount",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Button(
                    onClick = {
                        charging = true
                        scope.launch {
                            val id = cart.checkout(app.dao)
                            charging = false
                            nav.go(Route.Slip(id))
                        }
                    },
                    enabled = cart.lines.isNotEmpty() && !charging,
                    modifier = Modifier.height(52.dp),
                ) { Text("Charge ${rupees(cart.total)}", style = MaterialTheme.typography.titleMedium) }
            }
        }
    }

    qtyEdit?.let { edit ->
        QtyDialog(edit, onDismiss = { qtyEdit = null }) { qty, fixed ->
            qtyEdit = null
            when {
                edit.line != null -> cart.setQty(edit.line, qty, fixed)
                edit.product != null && qty > 0 -> cart.add(edit.product, qty, fixed)
            }
        }
    }
    unknown?.let { code ->
        AlertDialog(
            onDismissRequest = { unknown = null },
            title = { Text("New barcode") },
            text = { Text("$code is not in your inventory yet. Add it as a product, or sell it once at a price you type?") },
            confirmButton = {
                TextButton(onClick = {
                    unknown = null
                    nav.go(Route.Product(null, barcode = code, addToCart = true))
                }) { Text("Add product") }
            },
            dismissButton = {
                TextButton(onClick = {
                    unknown = null
                    custom = ""
                }) { Text("Sell once") }
            },
        )
    }
    custom?.let {
        CustomItemDialog(onDismiss = { custom = null }) { name, price, qty ->
            custom = null
            cart.addCustom(name, price, qty)
        }
    }
    if (picking) {
        PickProduct(onDismiss = { picking = false }) {
            picking = false
            add(it)
        }
    }
    if (editDiscount) {
        DiscountDialog(cart.discount, cart.subtotal, onDismiss = { editDiscount = false }) {
            editDiscount = false
            cart.discount = it
        }
    }
    if (clearing) Confirm("Clear this bill?", "All ${cart.lines.size} items come off it.", "Clear", cart::clear) { clearing = false }
}

@Composable
private fun CartRow(
    line: CartLine,
    onTap: () -> Unit,
    onStep: (Long) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onTap).padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(line.name, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
            Text(
                "${qtyText(line.qty, line.unit)} × ${rupees(line.price)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(rupees(line.amount), Modifier.padding(horizontal = 8.dp), fontWeight = FontWeight.SemiBold)
        if (!line.unit.loose) {
            FilledTonalIconButton(onClick = { onStep(-1000) }) { Icon(Icons.Remove, "One less") }
            FilledTonalIconButton(onClick = { onStep(1000) }) { Icon(Icons.Add, "One more") }
        }
    }
}

/**
 * How much: a count for packaged goods; for loose ones a weight or an amount
 * of money, each filling in the other ("₹20 of sugar").
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QtyDialog(
    edit: QtyEdit,
    onDismiss: () -> Unit,
    onDone: (qty: Long, fixed: Long?) -> Unit,
) {
    val loose = edit.unit.loose
    val small = if (edit.unit == Measure.LITRE) "ml" else "g"
    // Loose quantities are typed in grams or millilitres, which are exactly the stored thousandths.
    var qty by remember {
        mutableStateOf(
            if (edit.qty == 0L) {
                ""
            } else if (loose) {
                edit.qty.toString()
            } else {
                qtyField(edit.qty)
            },
        )
    }
    var amount by remember {
        mutableStateOf(edit.fixed?.let(::paiseField) ?: if (loose && edit.qty > 0) paiseField(lineAmount(edit.price, edit.qty)) else "")
    }

    // Whichever was typed last wins: asked for ₹20 of sugar, the line costs ₹20 exactly.
    var byAmount by remember { mutableStateOf(edit.fixed != null) }
    val milli = if (loose) qty.trim().toLongOrNull() else parseQty(qty)
    val fixed = if (loose && byAmount) parsePaise(amount) else null
    val valid = milli != null && (milli > 0 || edit.line != null)

    fun setQty(text: String) {
        qty = text
        byAmount = false
        if (loose) amount = text.trim().toLongOrNull()?.let { paiseField(lineAmount(edit.price, it)) } ?: ""
    }

    fun setAmount(text: String) {
        amount = text
        byAmount = true
        qty = parsePaise(text)?.let { qtyForAmount(edit.price, it).toString() } ?: ""
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(edit.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${rupees(edit.price)} per ${edit.unit.short}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (loose) {
                    Field("Weight", qty, ::setQty, kind = Kind.Whole, suffix = small)
                    Field("Amount", amount, ::setAmount, kind = Kind.Money)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (g in listOf(100L, 250L, 500L, 1000L, 2000L)) {
                            AssistChip(onClick = { setQty(g.toString()) }, label = { Text(qtyText(g, edit.unit)) })
                        }
                        for (r in listOf(10L, 20L, 50L)) AssistChip(onClick = { setAmount(r.toString()) }, label = { Text("₹$r") })
                    }
                } else {
                    Field("Quantity", qty, { qty = it }, kind = Kind.Decimal, suffix = edit.unit.short)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (n in listOf(1, 2, 3, 5, 6, 10, 12)) AssistChip(onClick = { qty = n.toString() }, label = { Text("$n") })
                    }
                }
                if (milli != null && milli > 0) {
                    Text("= ${rupees(fixed ?: lineAmount(edit.price, milli))}", style = MaterialTheme.typography.titleMedium)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onDone(milli ?: 0, fixed) }, enabled = valid) { Text(if (edit.line != null && milli == 0L) "Remove" else "Done") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun CustomItemDialog(
    onDismiss: () -> Unit,
    onDone: (String, Long, Long) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf("1") }
    val paise = parsePaise(price)
    val milli = parseQty(qty)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Other item") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "For something not in your inventory. It is sold on this bill only.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Field("Name", name, { name = it })
                Field("Price each", price, { price = it }, kind = Kind.Money)
                Field("Quantity", qty, { qty = it }, kind = Kind.Decimal)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onDone(name.trim().ifEmpty { "Item" }, paise!!, milli!!) },
                enabled = paise != null && paise > 0 && milli != null && milli > 0,
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun DiscountDialog(
    current: Long,
    subtotal: Long,
    onDismiss: () -> Unit,
    onDone: (Long) -> Unit,
) {
    var text by remember { mutableStateOf(if (current > 0) paiseField(current) else "") }
    val paise = if (text.isBlank()) 0 else parsePaise(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Discount") },
        text = {
            Column {
                Field("Discount", text, { text = it }, kind = Kind.Money, error = if (paise != null && paise > subtotal) "More than the bill" else null)
                // Rounding the total down is the usual discount at a kirana counter.
                val round = subtotal % 1000
                if (round in 1..<subtotal) {
                    TextButton(onClick = { text = paiseField(round) }) { Text("Round down to ${rupees(subtotal - round)}") }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onDone(paise!!) }, enabled = paise != null && paise <= subtotal) { Text("Done") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Every product, searchable by name or barcode: for items with no barcode, or one that will not scan. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickProduct(
    onDismiss: () -> Unit,
    onPick: (Product) -> Unit,
) {
    val app = LocalContext.current.app
    val products by app.dao.products().collectAsState(initial = emptyList())
    var query by remember { mutableStateOf("") }
    val shown = products.filter { !it.archived && it.matches(query) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxHeight(0.85f)) {
            Field("Search", query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            LazyColumn {
                itemsIndexed(shown, key = { _, p -> p.id }) { _, p ->
                    ListItem(
                        headlineContent = { Text(p.name) },
                        supportingContent = { Text("${rupees(p.price)} / ${p.unit.short} · ${qtyText(p.stock, p.unit)} in stock") },
                        modifier = Modifier.clickable { onPick(p) },
                    )
                }
            }
        }
    }
}

fun Product.matches(query: String): Boolean {
    val q = query.trim()
    return q.isEmpty() || name.contains(q, ignoreCase = true) || barcode?.contains(q) == true
}
