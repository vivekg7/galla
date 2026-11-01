package io.github.vivekg7.galla.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withTranslation
import io.github.vivekg7.galla.data.Bill
import io.github.vivekg7.galla.data.BillLine
import io.github.vivekg7.galla.data.Payment
import io.github.vivekg7.galla.data.ShopInfo
import io.github.vivekg7.galla.data.qtyText
import io.github.vivekg7.galla.data.rupees
import io.github.vivekg7.galla.data.upiLink
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The payment slip as an image: what the screen shows and what is shared,
 * from one drawing, so the two can never disagree. An unpaid bill carries a
 * UPI QR code for its exact total.
 */
object Slip {
    private const val WIDTH = 720
    private const val PAD = 40
    private const val INNER = WIDTH - 2 * PAD

    fun render(
        shop: ShopInfo,
        bill: Bill,
        lines: List<BillLine>,
    ): Bitmap {
        val height = draw(null, shop, bill, lines)
        return createBitmap(WIDTH, height).also { draw(Canvas(it), shop, bill, lines) }
    }

    fun showsQr(
        shop: ShopInfo,
        bill: Bill,
    ) = shop.canTakeUpi && bill.payment == Payment.PENDING && !bill.void && bill.total > 0

    fun upiFor(
        shop: ShopInfo,
        bill: Bill,
    ) = upiLink(shop.upiId, shop.payeeName, bill.total, "Bill ${bill.no}")

    private fun text(
        size: Float,
        bold: Boolean = false,
        color: Int = Color.BLACK,
    ) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }

    /** Draws when [canvas] is given; either way returns the height used. Measuring is the same walk with nothing drawn. */
    private fun draw(
        canvas: Canvas?,
        shop: ShopInfo,
        bill: Bill,
        lines: List<BillLine>,
    ): Int {
        canvas?.drawColor(Color.WHITE)
        var y = PAD.toFloat()

        fun block(
            value: String,
            paint: TextPaint,
            align: Layout.Alignment = Layout.Alignment.ALIGN_CENTER,
            width: Int = INNER,
            x: Float = PAD.toFloat(),
        ) {
            if (value.isBlank()) return
            val layout =
                StaticLayout.Builder
                    .obtain(value, 0, value.length, paint, width)
                    .setAlignment(align)
                    .build()
            canvas?.withTranslation(x, y) { layout.draw(this) }
            y += layout.height
        }

        fun row(
            left: String,
            right: String,
            paint: TextPaint,
        ) {
            val fm = paint.fontMetrics
            canvas?.drawText(left, PAD.toFloat(), y - fm.ascent, paint)
            canvas?.drawText(right, WIDTH - PAD - paint.measureText(right), y - fm.ascent, paint)
            y += fm.descent - fm.ascent + 6
        }

        val dash =
            Paint().apply {
                color = Color.GRAY
                strokeWidth = 2f
                pathEffect = DashPathEffect(floatArrayOf(10f, 8f), 0f)
            }

        fun rule() {
            y += 14
            canvas?.drawLine(PAD.toFloat(), y, (WIDTH - PAD).toFloat(), y, dash)
            y += 16
        }

        block(shop.name.ifBlank { "Payment slip" }, text(44f, bold = true))
        y += 6
        block(shop.address, text(24f, color = Color.DKGRAY))
        if (shop.phone.isNotBlank()) block("Phone: ${shop.phone}", text(24f, color = Color.DKGRAY))
        rule()
        val small = text(24f, color = Color.DKGRAY)
        row("Bill #${bill.no}", SimpleDateFormat("d MMM yyyy, h:mm a", Locale.ENGLISH).format(Date(bill.at)), small)
        rule()

        val itemName = text(28f, bold = true)
        val itemDetail = text(26f)
        val amountWidth = 200
        for (line in lines) {
            val top = y
            block(line.name, itemName, Layout.Alignment.ALIGN_NORMAL, INNER - amountWidth)
            val amount = rupees(line.amount)
            canvas?.drawText(amount, WIDTH - PAD - itemName.measureText(amount), top - itemName.fontMetrics.ascent, itemName)
            row("${qtyText(line.qty, line.unit)} × ${rupees(line.price)}", "", itemDetail)
            y += 8
        }
        rule()

        val sums = text(28f)
        if (bill.discount > 0) {
            row("Subtotal", rupees(bill.subtotal), sums)
            row("Discount", "− ${rupees(bill.discount)}", sums)
        }
        row("TOTAL", rupees(bill.total), text(40f, bold = true))
        y += 8
        when {
            bill.void -> block("CANCELLED", text(36f, bold = true, color = Color.rgb(0xB0, 0x20, 0x20)))
            bill.payment == Payment.PENDING -> block("Payment due", text(28f, bold = true, color = Color.rgb(0xB0, 0x60, 0x00)))
            else -> block("Paid by ${bill.payment.label}", text(28f, bold = true, color = Color.rgb(0x1E, 0x7A, 0x45)))
        }

        if (showsQr(shop, bill)) {
            rule()
            block("Scan to pay ${rupees(bill.total)} with any UPI app", text(28f, bold = true))
            y += 10
            val size = 440
            canvas?.drawBitmap(Codes.qr(upiFor(shop, bill), size), ((WIDTH - size) / 2).toFloat(), y, null)
            y += size + 6
            block("UPI ID: ${shop.upiId.trim()}", small)
        }

        if (shop.footer.isNotBlank()) {
            rule()
            block(shop.footer, text(26f, color = Color.DKGRAY))
        }
        return (y + PAD).toInt()
    }
}
