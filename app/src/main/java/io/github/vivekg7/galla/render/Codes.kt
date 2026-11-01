package io.github.vivekg7.galla.render

import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.graphics.createBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import io.github.vivekg7.galla.data.Ean

/** Barcodes and QR codes drawn on the phone with ZXing: nothing to download, nothing online. */
object Codes {
    fun qr(
        text: String,
        size: Int,
    ): Bitmap {
        val hints = mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 2)
        return MultiFormatWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints).toBitmap()
    }

    /** The symbology a scanner expects for this number: the retail ones for retail numbers, Code 128 for anything else. */
    fun formatFor(code: String): BarcodeFormat =
        when {
            Ean.isValid(code) -> BarcodeFormat.EAN_13
            code.length == 8 && code.all(Char::isDigit) -> BarcodeFormat.EAN_8
            code.length == 12 && code.all(Char::isDigit) -> BarcodeFormat.UPC_A
            else -> BarcodeFormat.CODE_128
        }

    /** Null when ZXing refuses the number for its format (a printed code with a bad check digit). */
    fun barcode(
        code: String,
        width: Int,
        height: Int,
    ): Bitmap? =
        runCatching {
            MultiFormatWriter().encode(code, formatFor(code), width, height, mapOf(EncodeHintType.MARGIN to 10)).toBitmap()
        }.recoverCatching {
            MultiFormatWriter().encode(code, BarcodeFormat.CODE_128, width, height, mapOf(EncodeHintType.MARGIN to 10)).toBitmap()
        }.getOrNull()

    private fun BitMatrix.toBitmap(): Bitmap {
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) pixels[row + x] = if (get(x, y)) Color.BLACK else Color.WHITE
        }
        return createBitmap(width, height).apply { setPixels(pixels, 0, width, 0, 0, width, height) }
    }
}
