package io.github.vivekg7.galla.data

import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import io.github.vivekg7.galla.app
import java.io.File

/**
 * The whole shop in one file, to wherever the user picks: a USB stick, the
 * Downloads folder, or Drive once they are online. Galla's data otherwise
 * lives on this phone only, so this is not optional
 * (docs/plans/001_offline_shop_app.md).
 */
object Backup {
    private const val LAST = "backup.last"

    /** A consistent single-file copy, write-ahead log included, made while the app keeps running. */
    private fun snapshot(
        context: Context,
        to: File,
    ) {
        to.delete()
        to.parentFile?.mkdirs()
        SQLiteDatabase.openDatabase(context.getDatabasePath(Db.FILE).path, null, SQLiteDatabase.OPEN_READWRITE).use {
            it.execSQL("VACUUM INTO ?", arrayOf(to.path))
        }
    }

    suspend fun export(
        context: Context,
        uri: Uri,
    ) {
        val tmp = File(context.cacheDir, "backup/export.db")
        snapshot(context, tmp)
        requireNotNull(context.contentResolver.openOutputStream(uri, "wt")) { "cannot write there" }.use { out ->
            tmp.inputStream().use { it.copyTo(out) }
        }
        tmp.delete()
        context.app.dao.putSetting(Setting(LAST, System.currentTimeMillis().toString()))
    }

    fun lastExport(settings: List<Setting>): Long? = settings.firstOrNull { it.key == LAST }?.value?.toLongOrNull()

    /**
     * Replaces the shop's data with a backup, then restarts the app on it.
     * The current data is first kept in files/before-restore.db, so picking
     * the wrong file is not the end of it. Throws, changing nothing, if the
     * file is not a Galla backup.
     */
    fun restore(
        context: Context,
        uri: Uri,
    ) {
        val incoming = File(context.cacheDir, "backup/import.db")
        incoming.parentFile?.mkdirs()
        requireNotNull(context.contentResolver.openInputStream(uri)) { "cannot read that file" }.use { input ->
            incoming.outputStream().use { input.copyTo(it) }
        }
        check(isGallaBackup(incoming)) { "That file is not a Galla backup." }

        snapshot(context, File(context.filesDir, "before-restore.db"))
        context.app.db.close()
        val live = context.getDatabasePath(Db.FILE)
        for (suffix in listOf("", "-wal", "-shm", "-journal")) File(live.path + suffix).delete()
        incoming.copyTo(live, overwrite = true)
        incoming.delete()

        // Room keeps the closed database for the life of the process: start a fresh one.
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)!!
        context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        Runtime.getRuntime().exit(0)
    }

    private fun isGallaBackup(file: File): Boolean =
        runCatching {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                val tables =
                    db.rawQuery("SELECT name FROM sqlite_master WHERE type = 'table'", null).use { c ->
                        buildSet { while (c.moveToNext()) add(c.getString(0)) }
                    }
                // A backup from a newer Galla would need a migration this one does not have.
                tables.containsAll(listOf("product", "bill", "bill_line", "stock_move", "setting")) && db.version <= DB_VERSION
            }
        }.getOrDefault(false)

    private const val DB_VERSION = 1
}
