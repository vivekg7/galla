package io.github.vivekg7.galla.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.vivekg7.galla.app
import io.github.vivekg7.galla.data.Ean
import io.github.vivekg7.galla.data.Measure
import io.github.vivekg7.galla.data.Product
import io.github.vivekg7.galla.data.Reason
import io.github.vivekg7.galla.data.paiseField
import io.github.vivekg7.galla.data.parsePaise
import io.github.vivekg7.galla.data.parseQty
import io.github.vivekg7.galla.data.qtyField
import io.github.vivekg7.galla.data.qtyText
import io.github.vivekg7.galla.data.rupees
import io.github.vivekg7.galla.render.Codes
import io.github.vivekg7.galla.render.Labels
import io.github.vivekg7.galla.render.Share
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class Filter(
    val label: String,
) {
    All("All"),
    Low("Low stock"),
    Loose("Loose"),
    Archived("Archived"),
}

@Composable
fun InventoryScreen() {
    val context = LocalContext.current
    val app = context.app
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    val products by app.dao.products().collectAsState(initial = null)
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(Filter.All) }
    var scanning by remember { mutableStateOf(false) }
    var restock by remember { mutableStateOf<Product?>(null) }

    val shown =
        products.orEmpty().filter { p ->
            p.matches(query) &&
                when (filter) {
                    Filter.All -> !p.archived
                    Filter.Low -> !p.archived && p.low
                    Filter.Loose -> !p.archived && p.unit.loose
                    Filter.Archived -> p.archived
                }
        }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Bar("Inventory") {
                TextButton(onClick = { scanning = true }) {
                    Icon(Icons.Barcode, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Restock")
                }
            }
            Field("Search name or barcode", query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (f in Filter.entries) FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.label) })
            }
            when {
                products == null -> {}

                products!!.isEmpty() -> {
                    Empty("No products yet", "Tap Add product, or Restock and scan what is on your shelves: each new barcode becomes a product.")
                }

                shown.isEmpty() -> {
                    Empty("Nothing here", "No product matches.")
                }

                else -> {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(shown, key = { it.id }) { p ->
                            ProductRow(p) { nav.go(Route.Product(p.id)) }
                            HorizontalDivider()
                        }
                        item { Spacer(Modifier.height(88.dp)) }
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { nav.go(Route.Product(null)) },
            icon = { Icon(Icons.Add, null) },
            text = { Text("Add product") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }

    if (scanning) {
        ScanDialog("Scan to restock", paused = restock != null, onDismiss = { scanning = false }, onCode = { code ->
            scope.launch {
                val product = app.dao.byBarcode(code)
                if (product == null) {
                    scanning = false
                    nav.go(Route.Product(null, barcode = code))
                } else {
                    restock = product
                }
            }
        })
    }
    restock?.let { p ->
        StockDialog(p, adding = true, onDismiss = { restock = null }) { delta ->
            restock = null
            scope.launch {
                app.dao.moveStock(p.id, delta, Reason.RESTOCK)
                context.toast("Added ${qtyText(delta, p.unit)} of ${p.name}")
            }
        }
    }
}

@Composable
private fun ProductRow(
    p: Product,
    onClick: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp, 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(p.name, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                "${rupees(p.price)} / ${p.unit.short}" + (p.barcode?.let { "  ·  $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        StockBadge(p)
    }
}

@Composable
private fun StockBadge(p: Product) {
    val bad = p.stock <= 0 || p.low
    Text(
        qtyText(p.stock, p.unit),
        Modifier
            .background(
                if (bad) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                RoundedCornerShape(8.dp),
            ).padding(horizontal = 8.dp, vertical = 4.dp),
        color = if (bad) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
        style = MaterialTheme.typography.labelLarge,
    )
}

/**
 * A product: new or existing, editing and its stock in one place. A product
 * saved without a barcode gets one made for it (Ean.forProduct), so
 * everything in the shop can be scanned.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProductScreen(route: Route.Product) {
    val context = LocalContext.current
    val app = context.app
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    val existing by produceState<Product?>(null, route.id) {
        if (route.id != null) app.dao.product(route.id).collect { value = it }
    }
    val isNew = route.id == null
    if (!isNew && existing == null) return
    val original = existing

    var name by rememberSaveable { mutableStateOf(original?.name ?: "") }
    var barcode by rememberSaveable { mutableStateOf(original?.barcode ?: route.barcode ?: "") }
    var unit by rememberSaveable { mutableStateOf(original?.unit ?: Measure.PIECE) }
    var price by rememberSaveable { mutableStateOf(original?.price?.let(::paiseField) ?: "") }
    var mrp by rememberSaveable { mutableStateOf(original?.mrp?.takeIf { it > 0 }?.let(::paiseField) ?: "") }
    var cost by rememberSaveable { mutableStateOf(original?.cost?.takeIf { it > 0 }?.let(::paiseField) ?: "") }
    var opening by rememberSaveable { mutableStateOf("") }
    var lowAt by rememberSaveable { mutableStateOf(original?.lowAt?.takeIf { it > 0 }?.let(::qtyField) ?: "") }
    var tried by remember { mutableStateOf(false) }
    var barcodeTaken by remember { mutableStateOf<String?>(null) }
    var scanning by remember { mutableStateOf(false) }
    var stockDialog by remember { mutableStateOf<Boolean?>(null) }
    var labels by remember { mutableStateOf(false) }

    val pricePaise = parsePaise(price)
    val mrpPaise = if (mrp.isBlank()) 0 else parsePaise(mrp)
    val costPaise = if (cost.isBlank()) 0 else parsePaise(cost)
    val openingQty = if (opening.isBlank()) 0 else parseQty(opening)
    val lowQty = if (lowAt.isBlank()) 0 else parseQty(lowAt)
    val nameError = if (tried && name.isBlank()) "Needed" else null
    val priceError = if (tried && (pricePaise == null || pricePaise <= 0)) "Enter the selling price" else null
    val ok = name.isNotBlank() && pricePaise != null && pricePaise > 0 && mrpPaise != null && costPaise != null && openingQty != null && lowQty != null

    fun save() {
        tried = true
        if (!ok) return
        scope.launch {
            val code = barcode.trim().ifEmpty { null }
            val clash = code?.let { app.dao.byBarcode(it) }
            if (clash != null && clash.id != route.id) {
                barcodeTaken = "Already used by ${clash.name}"
                return@launch
            }
            val now = System.currentTimeMillis()
            val fields =
                (original ?: Product(name = "", price = 0)).copy(
                    name = name.trim(),
                    barcode = code,
                    generated = original?.generated == true && code == original.barcode,
                    unit = unit,
                    price = pricePaise!!,
                    mrp = mrpPaise!!,
                    cost = costPaise!!,
                    lowAt = lowQty!!,
                    updatedAt = now,
                )
            if (original == null) {
                val id = app.dao.create(fields.copy(stock = openingQty!!, createdAt = now))
                if (code == null) app.dao.setGeneratedBarcode(id, Ean.forProduct(id), now)
                if (route.addToCart && !unit.loose) {
                    app.cart.add(fields.copy(id = id), 1000)
                    context.toast("Added to the bill")
                }
            } else {
                app.dao.update(fields)
                if (code == null) app.dao.setGeneratedBarcode(original.id, Ean.forProduct(original.id), now)
            }
            nav.back()
        }
    }

    Column(Modifier.fillMaxSize()) {
        Bar(if (isNew) "New product" else "Product", onBack = nav::back) {
            TextButton(onClick = ::save) { Text("Save") }
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (original != null) StockCard(original, onAdd = { stockDialog = true }, onSet = { stockDialog = false })

            Field("Name", name, { name = it }, error = nameError)
            Field(
                "Barcode",
                barcode,
                {
                    barcode = it.trim()
                    barcodeTaken = null
                },
                kind = Kind.Whole,
                error = barcodeTaken,
                hint = if (barcode.isBlank()) "Leave empty and Galla makes one you can print" else null,
                trailing = { IconButton(onClick = { scanning = true }) { Icon(Icons.Barcode, "Scan barcode") } },
            )
            Text("Sold by", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (m in Measure.entries) FilterChip(selected = unit == m, onClick = { unit = m }, label = { Text(m.label) })
            }
            Field("Selling price", price, { price = it }, kind = Kind.Money, suffix = "/ ${unit.short}", error = priceError)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("MRP", mrp, { mrp = it }, Modifier.weight(1f), kind = Kind.Money, hint = "Optional")
                Field("Cost price", cost, { cost = it }, Modifier.weight(1f), kind = Kind.Money, hint = "Optional")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isNew) Field("Opening stock", opening, { opening = it }, Modifier.weight(1f), kind = Kind.Decimal, suffix = unit.short)
                Field("Warn below", lowAt, { lowAt = it }, Modifier.weight(1f), kind = Kind.Decimal, suffix = unit.short, hint = "Optional")
            }

            if (original != null) {
                original.barcode?.let { code ->
                    BarcodeCard(code, original.generated) { labels = true }
                }
                History(original)
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            app.dao.update(original.copy(archived = !original.archived, updatedAt = System.currentTimeMillis()))
                            nav.back()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) { Text(if (original.archived) "Restore to inventory" else "Archive (hide from inventory)") }
            }
            Button(onClick = ::save, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(52.dp)) { Text("Save") }
        }
    }

    if (scanning) {
        ScanDialog("Scan the product's barcode", onDismiss = { scanning = false }, onCode = {
            barcode = it
            barcodeTaken = null
            scanning = false
        })
    }
    stockDialog?.let { adding ->
        StockDialog(original!!, adding, onDismiss = { stockDialog = null }) { delta ->
            stockDialog = null
            scope.launch { app.dao.moveStock(original.id, delta, if (adding) Reason.RESTOCK else Reason.ADJUST) }
        }
    }
    if (labels) LabelsDialog(original!!) { labels = false }
}

@Composable
private fun StockCard(
    p: Product,
    onAdd: () -> Unit,
    onSet: () -> Unit,
) {
    Card(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("In stock", style = MaterialTheme.typography.labelMedium)
                Text(
                    qtyText(p.stock, p.unit),
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (p.stock <= 0 || p.low) MaterialTheme.colorScheme.error else Color.Unspecified,
                )
            }
            TextButton(onClick = onSet) { Text("Correct") }
            Button(onClick = onAdd) { Text("Add stock") }
        }
    }
}

@Composable
private fun BarcodeCard(
    code: String,
    generated: Boolean,
    onLabels: () -> Unit,
) {
    val image by produceState<BitmapPainter?>(null, code) {
        value = withContext(Dispatchers.Default) { Codes.barcode(code, 600, 180)?.asImageBitmap()?.let(::BitmapPainter) }
    }
    Card(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (generated) "Barcode made by Galla" else "Barcode", style = MaterialTheme.typography.labelMedium)
            image?.let {
                Image(
                    it,
                    null,
                    Modifier
                        .fillMaxWidth()
                        .height(90.dp)
                        .padding(vertical = 8.dp)
                        .background(Color.White),
                    contentScale = ContentScale.Fit,
                )
            }
            Text(code, style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = onLabels, modifier = Modifier.padding(top = 8.dp)) { Text("Labels to print (PDF)") }
        }
    }
}

@Composable
private fun History(p: Product) {
    val app = LocalContext.current.app
    val moves by app.dao.moves(p.id).collectAsState(initial = emptyList())
    if (moves.isEmpty()) return
    val format = remember { SimpleDateFormat("d MMM, h:mm a", Locale.ENGLISH) }
    Text("Stock history", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
    for (m in moves) {
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Text(format.format(Date(m.at)), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            Text(
                when (m.reason) {
                    Reason.RESTOCK -> "Restock"
                    Reason.SALE -> "Sold"
                    Reason.ADJUST -> "Corrected"
                    Reason.VOID -> "Bill cancelled"
                },
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                (if (m.delta > 0) "+" else "") + qtyText(m.delta, p.unit),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

/** [adding]: how much came in. Otherwise the count found on the shelf, which replaces the stock. Calls back with the change. */
@Composable
private fun StockDialog(
    p: Product,
    adding: Boolean,
    onDismiss: () -> Unit,
    onDone: (Long) -> Unit,
) {
    var text by remember { mutableStateOf(if (adding) "" else qtyField(p.stock.coerceAtLeast(0))) }
    val qty = parseQty(text)
    val delta = qty?.let { if (adding) it else it - p.stock }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (adding) "Add stock" else "Correct stock") },
        text = {
            Column {
                Text(p.name, fontWeight = FontWeight.Medium)
                Text("Now: ${qtyText(p.stock, p.unit)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Field(if (adding) "Quantity received" else "Count on the shelf", text, { text = it }, kind = Kind.Decimal, suffix = p.unit.short)
                if (adding && qty != null) Text("New stock: ${qtyText(p.stock + qty, p.unit)}")
            }
        },
        confirmButton = {
            TextButton(onClick = { onDone(delta!!) }, enabled = delta != null && (delta != 0L) && (!adding || delta > 0)) {
                Text(if (adding) "Add" else "Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun LabelsDialog(
    p: Product,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var copies by remember { mutableStateOf(Labels.PER_PAGE.toString()) }
    val n = copies.trim().toIntOrNull()
    val valid = n != null && n in 1..(Labels.PER_PAGE * 20)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Barcode labels") },
        text = {
            Column {
                Text(
                    "An A4 PDF, ${Labels.PER_PAGE} labels a page (3 × 8 sticker sheet). Share it to print at home or at a print shop.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                Field("How many labels", copies, { copies = it }, kind = Kind.Whole)
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                scope.launch {
                    val safe =
                        p.name
                            .replace(Regex("[^A-Za-z0-9]+"), "-")
                            .trim('-')
                            .take(40)
                            .ifEmpty { "product" }
                    val file = File(Share.dir(context), "labels-$safe.pdf")
                    withContext(Dispatchers.IO) { Labels.write(p, n!!, file) }
                    onDismiss()
                    Share.file(context, file, "application/pdf")
                }
            }) { Text("Make PDF") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
