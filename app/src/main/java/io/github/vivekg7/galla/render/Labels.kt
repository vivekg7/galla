package io.github.vivekg7.galla.render

import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.TextPaint
import android.text.TextUtils
import io.github.vivekg7.galla.data.Product
import io.github.vivekg7.galla.data.rupees
import java.io.File

/**
 * Barcode labels on A4, 3 across and 8 down: the common 24-up sticker sheet.
 * A PDF, so it prints from any phone, shop or computer; Galla itself needs no
 * printer.
 */
object Labels {
    const val PER_PAGE = 24
    private const val COLS = 3
    private const val ROWS = 8

    // A4 in PDF points (1/72 inch).
    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MARGIN = 18f

    fun write(
        product: Product,
        copies: Int,
        out: File,
    ) {
        val code = requireNotNull(product.barcode) { "product has no barcode" }
        val image = requireNotNull(Codes.barcode(code, 600, 180)) { "cannot draw $code" }
        val cellW = (PAGE_W - 2 * MARGIN) / COLS
        val cellH = (PAGE_H - 2 * MARGIN) / ROWS
        val name =
            TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 9f
                typeface = Typeface.DEFAULT_BOLD
            }
        val small = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 8f }
        // Sharp bars: a smoothed edge reads as grey to a scanner.
        val bars = Paint().apply { isFilterBitmap = false }
        val doc = PdfDocument()
        try {
            val pages = (copies + PER_PAGE - 1) / PER_PAGE
            var left = copies
            for (p in 0 until pages) {
                val page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, p + 1).create())
                val canvas = page.canvas
                for (i in 0 until minOf(left, PER_PAGE)) {
                    val x = MARGIN + (i % COLS) * cellW + 8
                    val y = MARGIN + (i / COLS) * cellH + 8
                    val w = cellW - 16
                    val price = rupees(product.price)
                    canvas.drawText(price, x + w - name.measureText(price), y + 9, name)
                    val title = TextUtils.ellipsize(product.name, name, w - name.measureText(price) - 6, TextUtils.TruncateAt.END).toString()
                    canvas.drawText(title, x, y + 9, name)
                    canvas.drawBitmap(image, Rect(0, 0, image.width, image.height), RectF(x, y + 14, x + w, y + 14 + w * 0.3f), bars)
                    canvas.drawText(code, x + (w - small.measureText(code)) / 2, y + 14 + w * 0.3f + 9, small)
                }
                left -= PER_PAGE
                doc.finishPage(page)
            }
            out.parentFile?.mkdirs()
            out.outputStream().use(doc::writeTo)
        } finally {
            doc.close()
        }
    }
}
