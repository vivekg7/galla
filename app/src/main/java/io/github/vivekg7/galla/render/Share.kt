package io.github.vivekg7.galla.render

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import java.io.File

/** Hands a file to another app (WhatsApp, a PDF viewer, Drive) through the share sheet. */
object Share {
    fun dir(context: Context) = File(context.cacheDir, "share").apply { mkdirs() }

    fun image(
        context: Context,
        bitmap: Bitmap,
        name: String,
    ) {
        val file = File(dir(context), name)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        file(context, file, "image/png")
    }

    fun file(
        context: Context,
        file: File,
        type: String,
    ) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send =
            Intent(Intent.ACTION_SEND)
                .setType(type)
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
