package io.github.vivekg7.galla.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import io.github.vivekg7.galla.app
import io.github.vivekg7.galla.data.Bill
import io.github.vivekg7.galla.data.Payment
import io.github.vivekg7.galla.data.rupees
import io.github.vivekg7.galla.render.Codes
import io.github.vivekg7.galla.render.Share
import io.github.vivekg7.galla.render.Slip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private fun startOfToday() =
    Calendar
        .getInstance()
        .apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

/** Every bill, newest first, under today's takings. */
@Composable
fun SalesScreen() {
    val app = LocalContext.current.app
    val nav = LocalNav.current
    val bills by app.dao.bills().collectAsState(initial = null)
    val today = remember { startOfToday() }
    val format = remember { SimpleDateFormat("d MMM, h:mm a", Locale.ENGLISH) }

    Column(Modifier.fillMaxSize()) {
        Bar("Sales")
        val all = bills ?: return@Column
        val todays = all.filter { it.at >= today && !it.void }
        Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("Today", style = MaterialTheme.typography.labelLarge)
                Text(rupees(todays.sumOf { it.total }), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                Text("${todays.size} bill${if (todays.size == 1) "" else "s"}", style = MaterialTheme.typography.bodySmall)
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    for (p in Payment.entries) {
                        Column {
                            Text(p.label, style = MaterialTheme.typography.labelMedium)
                            Text(rupees(todays.filter { it.payment == p }.sumOf { it.total }))
                        }
                    }
                }
            }
        }
        if (all.isEmpty()) {
            Empty("No sales yet", "Bills you charge appear here.")
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(top = 8.dp)) {
                items(all, key = { it.id }) { bill ->
                    Row(
                        Modifier.fillMaxWidth().clickable { nav.go(Route.Slip(bill.id)) }.padding(16.dp, 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Bill #${bill.no}", fontWeight = FontWeight.Medium)
                            Text(format.format(Date(bill.at)), style = MaterialTheme.typography.bodySmall)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                rupees(bill.total),
                                fontWeight = FontWeight.SemiBold,
                                textDecoration = if (bill.void) TextDecoration.LineThrough else null,
                            )
                            PaymentTag(bill)
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun PaymentTag(bill: Bill) {
    val (text, color) =
        when {
            bill.void -> "Cancelled" to MaterialTheme.colorScheme.onSurfaceVariant
            bill.payment == Payment.PENDING -> "Unpaid" to MaterialTheme.colorScheme.error
            else -> bill.payment.label to MaterialTheme.colorScheme.primary
        }
    Text(text, style = MaterialTheme.typography.labelMedium, color = color)
}

/**
 * A charged bill: its UPI QR for the customer to scan, the buttons to record
 * how it was paid, and the slip to share. Galla is offline, so it cannot see
 * a UPI payment arrive: the shopkeeper checks their UPI app and taps Paid.
 */
@Composable
fun SlipScreen(billId: Long) {
    val context = LocalContext.current
    val app = context.app
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    val bill by app.dao.bill(billId).collectAsState(initial = null)
    val lines by app.dao.lines(billId).collectAsState(initial = null)
    val shop by app.shop.collectAsState()
    var menu by remember { mutableStateOf(false) }
    var cancelling by remember { mutableStateOf(false) }

    val b = bill
    val l = lines
    val s = shop
    val slip by produceState<ImageBitmap?>(null, b, l, s) {
        value = if (b == null || l == null || s == null) null else withContext(Dispatchers.Default) { Slip.render(s, b, l).asImageBitmap() }
    }
    val qr by produceState<ImageBitmap?>(null, b, s) {
        value =
            if (b != null && s != null && Slip.showsQr(s, b)) withContext(Dispatchers.Default) { Codes.qr(Slip.upiFor(s, b), 720).asImageBitmap() } else null
    }

    Column(Modifier.fillMaxSize()) {
        Bar(b?.let { "Bill #${it.no}" } ?: "Bill", onBack = nav::back) {
            IconButton(enabled = b != null && l != null && s != null, onClick = {
                scope.launch {
                    val image = withContext(Dispatchers.Default) { Slip.render(s!!, b!!, l!!) }
                    Share.image(context, image, "bill-${b!!.no}.png")
                }
            }) { Icon(Icons.Share, "Share slip") }
            if (b != null && !b.void) {
                IconButton(onClick = { menu = true }) { Icon(Icons.More, "More") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (b.payment != Payment.PENDING) {
                        DropdownMenuItem(text = { Text("Mark as unpaid") }, onClick = {
                            menu = false
                            scope.launch { app.dao.setPayment(b.id, Payment.PENDING) }
                        })
                    }
                    DropdownMenuItem(text = { Text("Cancel bill") }, onClick = {
                        menu = false
                        cancelling = true
                    })
                }
            }
        }
        if (b == null || s == null) return@Column

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val pending = b.payment == Payment.PENDING && !b.void
            if (pending) {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(rupees(b.total), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                        qr?.let {
                            Text("Scan with any UPI app", style = MaterialTheme.typography.bodyMedium)
                            Image(
                                it,
                                "UPI payment QR code",
                                Modifier.padding(top = 8.dp).size(260.dp).background(Color.White),
                                filterQuality = FilterQuality.None,
                            )
                            Text(s.upiId.trim(), style = MaterialTheme.typography.bodySmall)
                        }
                        if (!s.canTakeUpi && b.total > 0) {
                            Text(
                                "Add your UPI ID under Shop to show a payment QR here.",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { scope.launch { app.dao.setPayment(b.id, Payment.UPI) } },
                        modifier = Modifier.weight(1f).height(52.dp),
                    ) { Text("Paid by UPI") }
                    Button(
                        onClick = { scope.launch { app.dao.setPayment(b.id, Payment.CASH) } },
                        modifier = Modifier.weight(1f).height(52.dp),
                    ) { Text("Paid in cash") }
                }
            } else {
                PaymentTag(b)
            }

            slip?.let {
                Card(elevation = CardDefaults.cardElevation(2.dp)) {
                    Image(it, "Payment slip", Modifier.fillMaxWidth().aspectRatio(it.width / it.height.toFloat()), contentScale = ContentScale.Fit)
                }
            }
            OutlinedButton(onClick = { nav.tab(Tab.Bill) }, modifier = Modifier.fillMaxWidth()) { Text("New bill") }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (cancelling) {
        Confirm(
            "Cancel this bill?",
            "Its items go back into stock and it stops counting in sales. The bill stays in the list, marked cancelled.",
            "Cancel bill",
            onConfirm = { scope.launch { app.dao.voidBill(billId) } },
            onDismiss = { cancelling = false },
        )
    }
}
