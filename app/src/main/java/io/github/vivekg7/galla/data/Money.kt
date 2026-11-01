package io.github.vivekg7.galla.data

import java.math.BigDecimal
import java.math.RoundingMode
import java.net.URLEncoder

/*
 * Pure arithmetic and text, no Android: the unit tests run it on the JVM.
 * Money is paise and quantity is thousandths of a unit (Db.kt).
 */

/**
 * ₹1,23,456.50 with Indian digit grouping; whole rupees drop the ".00".
 * Grouped by hand: the JVM's DecimalFormat has a single grouping size, so it
 * would print 123,456 in the tests while Android's ICU prints 1,23,456.
 */
fun rupees(paise: Long): String {
    val abs = kotlin.math.abs(paise)
    val whole = (abs / 100).toString()
    val grouped =
        if (whole.length <= 3) {
            whole
        } else {
            whole
                .dropLast(3)
                .reversed()
                .chunked(2)
                .joinToString(",")
                .reversed() + "," + whole.takeLast(3)
        }
    val fraction = if (abs % 100 == 0L) "" else "." + (abs % 100).toString().padStart(2, '0')
    return (if (paise < 0) "−" else "") + "₹" + grouped + fraction
}

/** "12", "12.5" or "12.50" as typed into a price field; null if not a price. */
fun parsePaise(text: String): Long? {
    val clean =
        text
            .trim()
            .removePrefix("₹")
            .replace(",", "")
            .trim()
    if (clean.isEmpty()) return null
    val value = clean.toBigDecimalOrNull() ?: return null
    if (value.signum() < 0 || value.scale() > 2) return null
    return value.movePointRight(2).longValueExact()
}

/** The plain form for an edit field: 12.5 rupees as "12.50", 12 as "12". */
fun paiseField(paise: Long): String = if (paise % 100 == 0L) (paise / 100).toString() else BigDecimal.valueOf(paise, 2).toPlainString()

/** A typed quantity in whole units ("1.25" kg) to thousandths; null if not a quantity. */
fun parseQty(text: String): Long? {
    val value = text.trim().replace(",", "").toBigDecimalOrNull() ?: return null
    if (value.signum() < 0 || value.scale() > 3) return null
    return value.movePointRight(3).longValueExact()
}

fun qtyField(milli: Long): String = BigDecimal.valueOf(milli, 3).stripTrailingZeros().toPlainString()

/** "3 pc", "1.25 kg", "250 g", "500 ml". */
fun qtyText(
    milli: Long,
    unit: Measure,
): String {
    val small =
        when (unit) {
            Measure.KG -> "g"
            Measure.LITRE -> "ml"
            else -> null
        }
    return if (small != null && milli in -999..999) "$milli $small" else "${qtyField(milli)} ${unit.short}"
}

/** Price per unit times quantity, rounded to the nearest paisa. */
fun lineAmount(
    price: Long,
    qtyMilli: Long,
): Long =
    BigDecimal
        .valueOf(price)
        .multiply(BigDecimal.valueOf(qtyMilli))
        .divide(BigDecimal.valueOf(1000), 0, RoundingMode.HALF_UP)
        .longValueExact()

/** How much a given amount of money buys of a loose item, in thousandths. */
fun qtyForAmount(
    price: Long,
    amount: Long,
): Long =
    if (price <= 0) {
        0
    } else {
        BigDecimal
            .valueOf(amount)
            .multiply(BigDecimal.valueOf(1000))
            .divide(BigDecimal.valueOf(price), 0, RoundingMode.HALF_UP)
            .longValueExact()
    }

/**
 * Barcodes Galla makes for products that have none. EAN-13 numbers starting
 * with 2 are reserved by GS1 for use inside one shop, so they never clash with
 * a manufacturer's code: "2", the phone's number in two digits, its product
 * number in nine (together the product id, Db.kt), a check digit. Phone 0's
 * codes start "200", as they did before phones synced.
 */
object Ean {
    fun forProduct(id: Long): String {
        require(id in 1..99_999_999_999) { "product id out of range: $id" }
        val body = "2" + id.toString().padStart(11, '0')
        return body + checkDigit(body)
    }

    fun checkDigit(body12: String): Int {
        require(body12.length == 12 && body12.all(Char::isDigit))
        val sum = body12.mapIndexed { i, c -> (c - '0') * if (i % 2 == 0) 1 else 3 }.sum()
        return (10 - sum % 10) % 10
    }

    fun isValid(code: String): Boolean = code.length == 13 && code.all(Char::isDigit) && checkDigit(code.take(12)) == code.last() - '0'
}

/** "57" for phone 0's bills, "2-57" for phone 2's: each phone numbers its own (Db.kt). */
fun billNo(no: Long): String = if (no <= 999_999_999) "$no" else "${no / 1_000_000_000}-${no % 1_000_000_000}"

/**
 * The link a UPI QR code carries (NPCI's deep-link format). Any UPI app opens
 * it with the payee and amount filled in; the customer only confirms.
 */
fun upiLink(
    vpa: String,
    payee: String,
    amountPaise: Long,
    note: String,
): String {
    fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
    val amount = BigDecimal.valueOf(amountPaise, 2).toPlainString()
    // The '@' in the UPI ID stays as it is: some apps do not decode %40.
    return "upi://pay?pa=${enc(vpa.trim()).replace("%40", "@")}&pn=${enc(payee.trim())}&am=$amount&cu=INR&tn=${enc(note)}"
}

/** name@bank, the shape every UPI ID has. */
fun isUpiId(text: String) = Regex("^[A-Za-z0-9.\\-_]{2,256}@[A-Za-z][A-Za-z0-9.\\-]{1,63}$").matches(text.trim())
