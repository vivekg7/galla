package io.github.vivekg7.galla.data

import android.util.JsonReader
import android.util.JsonToken
import android.util.JsonWriter
import androidx.room3.PooledConnection
import androidx.room3.Transactor
import androidx.room3.useReaderConnection
import androidx.room3.useWriterConnection
import androidx.sqlite.SQLITE_DATA_INTEGER
import androidx.sqlite.SQLITE_DATA_NULL
import androidx.sqlite.SQLiteStatement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.time.Instant
import java.util.Locale
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** A file in the shared folder. [created] is the folder's own clock, RFC 3339. */
data class RemoteFile(
    val id: String,
    val name: String,
    val created: String,
)

/** Where the phones meet: Drive's hidden app folder (Drive.kt), or a map in the tests. */
interface Remote {
    /** Every file, or those created after [after]. */
    suspend fun list(after: String?): List<RemoteFile>

    suspend fun download(
        id: String,
        to: File,
    )

    suspend fun upload(
        name: String,
        from: File,
    ): RemoteFile

    suspend fun delete(id: String)
}

/** Google wants the person to confirm before Galla may use Drive again. */
class SignInNeeded(
    val intent: android.app.PendingIntent,
) : Exception("Sign in to Google Drive again")

/**
 * Keeps the shop's phones in step through a shared folder
 * (docs/plans/002_sync_between_phones.md). Each phone uploads only files of
 * its own, so two phones never write the same file:
 *
 * - `phone-NN.json`: phone NN has joined.
 * - `b-NN-SSSSSSSS.json.gz`: phone NN's changes, numbered 1, 2, 3…
 * - `snap-NN-TIME.json.gz`: the whole shop as phone NN saw it, with which
 *   batches it had applied; a joining phone starts from the newest.
 *
 * Rows are applied by rule: products, bills and shop details take the later
 * edit (updatedAt); bill lines and stock moves are never edited, so a row
 * already there is skipped. Stock is then added up again from the moves.
 */
class Sync(
    private val db: Db,
    private val dir: File,
    private val installId: String,
) {
    data class Status(
        val running: Boolean = false,
        val error: String? = null,
        val signIn: android.app.PendingIntent? = null,
    )

    val status = MutableStateFlow(Status())
    private val lock = Mutex()
    private val dao get() = db.dao()

    // --- Joining and leaving --------------------------------------------

    /** The shop's phones already in [files]; empty means this phone will be the first. */
    fun phones(files: List<RemoteFile>) =
        files
            .mapNotNull {
                PHONE
                    .matchEntire(it.name)
                    ?.groupValues
                    ?.get(1)
                    ?.toLong()
            }.sorted()

    /**
     * Makes this phone one of the shop's. The first phone uploads its data as
     * it is. A later one replaces its data with the shop's (keeping a copy in
     * before-sync.db) and takes the next free phone number.
     */
    suspend fun join(
        remote: Remote,
        account: String,
        model: String,
    ): Long =
        lock
            .withLock {
                val files = remote.list(null)
                val phone = (phones(files).maxOrNull() ?: -1) + 1
                check(phone <= MAX_PHONE) { "A shop can have at most ${MAX_PHONE + 1} phones." }
                dir.mkdirs()
                try {
                    if (phone == 0L) {
                        setIdentity(phone, account)
                        uploadSnapshot(remote, phone)
                    } else {
                        val copy = File(dir.parentFile, "before-sync.db").apply { delete() }
                        db.useWriterConnection { it.exec("VACUUM INTO ?", copy.path) }
                        val snap = files.filter { SNAP.matches(it.name) }.maxByOrNull { it.created }
                        val file = File(dir, "join.json.gz")
                        snap?.let { remote.download(it.id, file) }
                        db.useWriterConnection { c ->
                            c.withTransaction(Transactor.SQLiteTransactionType.IMMEDIATE) {
                                for (t in TABLES) exec(if (t == "setting") "DELETE FROM setting WHERE key LIKE 'shop.%'" else "DELETE FROM $t")
                                exec("DELETE FROM pending")
                                if (snap != null) applyFiles(this, listOf(null to file))
                            }
                        }
                        file.delete()
                        setIdentity(phone, account)
                    }
                    val marker =
                        File(
                            dir,
                            "phone.json",
                        ).apply { writeText("""{"model":${org.json.JSONObject.quote(model)},"joined":${System.currentTimeMillis()}}""") }
                    remote.upload("phone-%02d.json".format(Locale.ROOT, phone), marker)
                    marker.delete()
                } catch (e: Exception) {
                    leaveNow()
                    throw e
                }
                phone
            }.also { sync { remote } }

    private suspend fun setIdentity(
        phone: Long,
        account: String,
    ) = db.useWriterConnection { c ->
        c.withTransaction(Transactor.SQLiteTransactionType.IMMEDIATE) {
            put(this, INSTALL, installId)
            put(this, ACCOUNT, account)
            put(this, SENT, "0")
            // Last: from here on, the triggers record every change.
            put(this, Db.Triggers.DEVICE, phone.toString())
        }
    }

    /** Stops syncing. The data stays; joining again replaces it with the shop's from Drive. */
    suspend fun leave() = lock.withLock { leaveNow() }

    private suspend fun leaveNow() {
        db.useWriterConnection { c ->
            c.withTransaction(Transactor.SQLiteTransactionType.IMMEDIATE) {
                exec("DELETE FROM setting WHERE key LIKE 'sync.%'")
                exec("DELETE FROM pending")
            }
        }
        status.value = Status()
    }

    /**
     * This phone's number, or null when not syncing. The data may have come
     * from another install (Android's own backup, restored onto a new phone):
     * then it must not upload as the old phone, and it leaves.
     */
    suspend fun phone(): Long? {
        val phone = dao.setting(Db.Triggers.DEVICE)?.toLongOrNull() ?: return null
        if (dao.setting(INSTALL) == installId) return phone
        leaveNow()
        status.value = Status(error = "This phone's data was restored from a backup, so sync was turned off. Turn it on to get the shop's data from Drive.")
        return null
    }

    // --- Syncing ----------------------------------------------------------

    /** While the app is open: now, every two minutes, and half a minute after a change here. */
    suspend fun whileOpen(remote: (account: String) -> Remote) =
        coroutineScope {
            launch {
                while (true) {
                    sync(remote)
                    delay(120_000)
                }
            }
            dao.pendingCount().collectLatest {
                if (it > 0) {
                    delay(30_000)
                    sync(remote)
                }
            }
        }

    /** One round: fetch the other phones' new changes, then upload this phone's. Never throws; [status] says how it went. */
    suspend fun sync(remote: (account: String) -> Remote) {
        val phone = phone() ?: return
        val account = dao.setting(ACCOUNT) ?: return
        lock.withLock {
            status.value = status.value.copy(running = true)
            status.value =
                try {
                    withContext(Dispatchers.IO) { round(remote(account), phone) }
                    put(LAST, System.currentTimeMillis().toString())
                    Status()
                } catch (e: SignInNeeded) {
                    Status(error = e.message, signIn = e.intent)
                } catch (e: IOException) {
                    Status(error = "Could not reach Google Drive (${e.message ?: "no internet"}). Galla keeps working and tries again.")
                } catch (e: Exception) {
                    Status(error = e.message ?: e.toString())
                }
        }
    }

    private suspend fun round(
        remote: Remote,
        phone: Long,
    ) {
        dir.mkdirs()
        val seen = dao.setting(SEEN)
        // An hour's overlap: a file can show up in the list a little after a newer one.
        val files = remote.list(seen?.let { Instant.parse(it).minusSeconds(3600).toString() })

        val batches = files.mapNotNull { f -> BATCH.matchEntire(f.name)?.let { Triple(it.groupValues[1].toLong(), it.groupValues[2].toLong(), f) } }
        // A batch of ours already up but not recorded (the app stopped mid-upload): never reuse its number.
        batches.filter { it.first == phone }.maxOfOrNull { it.second }?.let { if (it > sent()) put(SENT, it.toString()) }

        var gap = false
        val todo = mutableListOf<Pair<Pair<Long, Long>, RemoteFile>>()
        for ((p, list) in batches.filter { it.first != phone }.groupBy { it.first }) {
            var next = (dao.setting(GOT + p)?.toLongOrNull() ?: 0) + 1
            for ((_, seq, file) in list.sortedBy { it.second }) {
                if (seq < next) continue
                if (seq > next) {
                    gap = true
                    break
                }
                todo += (p to seq) to file
                next++
            }
        }
        for (chunk in todo.chunked(20)) {
            val local = coroutineScope { chunk.map { (_, f) -> async { File(dir, f.id).also { remote.download(f.id, it) } } }.awaitAll() }
            db.useWriterConnection { c ->
                c.withTransaction(Transactor.SQLiteTransactionType.IMMEDIATE) { applyFiles(this, chunk.map { it.first }.zip(local)) }
            }
            local.forEach(File::delete)
        }
        // After a gap, look at the whole folder again next time rather than miss the batch.
        if (gap) dao.deleteSetting(SEEN) else files.maxOfOrNull { it.created }?.let { put(SEEN, it) }

        upload(remote, phone)
        val last = dao.setting(SNAPPED)?.toLongOrNull() ?: 0
        if (System.currentTimeMillis() - last > SNAPSHOT_EVERY) uploadSnapshot(remote, phone)
    }

    private suspend fun sent() = dao.setting(SENT)?.toLongOrNull() ?: 0

    /** This phone's changes since the last upload, as its next batch. */
    private suspend fun upload(
        remote: Remote,
        phone: Long,
    ) {
        val file = File(dir, "upload.json.gz")
        val upTo =
            db.useReaderConnection { c ->
                c.withTransaction<Long?>(Transactor.SQLiteTransactionType.DEFERRED) {
                    val keys = mutableListOf<Pair<String, String>>()
                    var upTo = 0L
                    usePrepared("SELECT tbl, rid, MAX(seq) FROM pending GROUP BY tbl, rid") { st ->
                        while (st.step()) {
                            keys += st.getText(0) to st.getText(1)
                            upTo = maxOf(upTo, st.getLong(2))
                        }
                    }
                    if (keys.isEmpty()) return@withTransaction null
                    write(file, null) { w ->
                        for ((t, rid) in keys) {
                            usePrepared("SELECT * FROM $t WHERE ${keyOf(t)} = ?") { st ->
                                if (t == "setting") st.bindText(1, rid) else st.bindLong(1, rid.toLong())
                                if (st.step()) writeRow(w, t, st)
                            }
                        }
                    }
                    upTo
                }
            } ?: return
        val seq = sent() + 1
        remote.upload("b-%02d-%08d.json.gz".format(Locale.ROOT, phone, seq), file)
        file.delete()
        db.useWriterConnection { c ->
            c.withTransaction(Transactor.SQLiteTransactionType.IMMEDIATE) {
                exec("DELETE FROM pending WHERE seq <= ?", upTo)
                put(this, SENT, seq.toString())
            }
        }
    }

    /**
     * The whole shop, with how far it had got with each phone's batches, so a
     * joining phone downloads one file and not every batch since the start.
     * Changes not yet uploaded are in it too, and come again in a later batch:
     * applying a row twice changes nothing.
     */
    private suspend fun uploadSnapshot(
        remote: Remote,
        phone: Long,
    ) {
        val file = File(dir, "snapshot.json.gz")
        db.useReaderConnection { c ->
            c.withTransaction(Transactor.SQLiteTransactionType.DEFERRED) {
                val marks = mutableMapOf<Long, Long>()
                usePrepared("SELECT key, value FROM setting WHERE key LIKE '$GOT%'") { st ->
                    while (st.step()) marks[st.getText(0).removePrefix(GOT).toLong()] = st.getText(1).toLong()
                }
                usePrepared("SELECT value FROM setting WHERE key = '$SENT'") { st -> if (st.step()) marks[phone] = st.getText(0).toLong() }
                write(file, marks) { w ->
                    for (t in TABLES) {
                        val where = if (t == "setting") " WHERE key LIKE 'shop.%'" else ""
                        usePrepared("SELECT * FROM $t$where") { st -> while (st.step()) writeRow(w, t, st) }
                    }
                }
            }
        }
        val now = System.currentTimeMillis()
        val made = remote.upload("snap-%02d-%d.json.gz".format(Locale.ROOT, phone, now), file)
        file.delete()
        // The previous one is no longer needed; if deleting it fails, a spare old snapshot does no harm.
        dao.setting(SNAP_ID)?.let { runCatching { remote.delete(it) } }
        put(SNAP_ID, made.id)
        put(SNAPPED, now.toString())
    }

    // --- Files ------------------------------------------------------------

    private suspend fun write(
        file: File,
        marks: Map<Long, Long>?,
        rows: suspend (JsonWriter) -> Unit,
    ) {
        JsonWriter(GZIPOutputStream(file.outputStream()).bufferedWriter()).use { w ->
            w.beginObject()
            // First, so a reader can refuse a newer format before it reads any row.
            w.name("galla").value(Db.VERSION.toLong())
            if (marks != null) {
                w.name("marks").beginObject()
                for ((p, seq) in marks) w.name(p.toString()).value(seq)
                w.endObject()
            }
            w.name("rows").beginArray()
            rows(w)
            w.endArray()
            w.endObject()
        }
    }

    private fun writeRow(
        w: JsonWriter,
        table: String,
        st: SQLiteStatement,
    ) {
        w.beginArray().value(table).beginObject()
        for (i in 0 until st.getColumnCount()) {
            w.name(st.getColumnName(i))
            when (st.getColumnType(i)) {
                SQLITE_DATA_NULL -> w.nullValue()
                SQLITE_DATA_INTEGER -> w.value(st.getLong(i))
                else -> w.value(st.getText(i))
            }
        }
        w.endObject().endArray()
    }

    /**
     * Applies downloaded files inside the caller's transaction, each labelled
     * with the (phone, batch) it is, or null for a snapshot, whose marks are
     * taken over. Then adds stock up again from the moves.
     */
    private suspend fun applyFiles(
        tx: PooledConnection,
        files: List<Pair<Pair<Long, Long>?, File>>,
    ) {
        // Quiets the triggers: these changes came from Drive, so they are not this phone's to upload.
        tx.exec("INSERT OR REPLACE INTO setting (key, value, updatedAt) VALUES (?, '1', 0)", Db.Triggers.APPLYING)
        val columns = TABLES.associateWith { t -> tx.usePrepared("PRAGMA table_info($t)") { st -> buildSet { while (st.step()) add(st.getText(1)) } } }
        for ((label, file) in files) {
            JsonReader(GZIPInputStream(file.inputStream()).bufferedReader()).use { r ->
                r.beginObject()
                while (r.hasNext()) {
                    when (r.nextName()) {
                        "galla" -> {
                            val version = r.nextLong()
                            check(version <= Db.VERSION) { "Another phone has a newer Galla. Update Galla on this phone to keep syncing." }
                        }

                        "marks" -> {
                            r.beginObject()
                            while (r.hasNext()) put(tx, GOT + r.nextName(), r.nextLong().toString())
                            r.endObject()
                        }

                        "rows" -> {
                            r.beginArray()
                            while (r.hasNext()) {
                                r.beginArray()
                                val table = r.nextString()
                                val row = readRow(r)
                                r.endArray()
                                val known = columns[table] ?: error("Another phone has a newer Galla. Update Galla on this phone to keep syncing.")
                                check(known.containsAll(row.keys)) { "Another phone has a newer Galla. Update Galla on this phone to keep syncing." }
                                applyRow(tx, table, row)
                            }
                            r.endArray()
                        }

                        else -> {
                            r.skipValue()
                        }
                    }
                }
                r.endObject()
            }
            label?.let { (p, seq) -> put(tx, GOT + p, seq.toString()) }
        }
        // The moves are the truth about stock (Db.kt): a move may arrive before its product, or twice.
        tx.exec("UPDATE product SET stock = COALESCE((SELECT SUM(delta) FROM stock_move WHERE productId = product.id), 0)")
        tx.exec("DELETE FROM setting WHERE key = ?", Db.Triggers.APPLYING)
    }

    private fun readRow(r: JsonReader): MutableMap<String, Any?> {
        val row = linkedMapOf<String, Any?>()
        r.beginObject()
        while (r.hasNext()) {
            val name = r.nextName()
            row[name] =
                when (r.peek()) {
                    JsonToken.NULL -> r.nextNull().let { null }
                    JsonToken.NUMBER -> r.nextLong()
                    else -> r.nextString()
                }
        }
        r.endObject()
        return row
    }

    private suspend fun applyRow(
        tx: PooledConnection,
        table: String,
        row: MutableMap<String, Any?>,
    ) {
        val key = keyOf(table)
        val id = row[key]
        if (table in EDITED) {
            val mine = tx.usePrepared("SELECT updatedAt FROM $table WHERE `$key` = ?") { st -> st.bind(1, id).let { if (st.step()) st.getLong(0) else null } }
            if (mine != null && mine >= (row["updatedAt"] as? Long ?: 0)) return
        }
        // Two phones gave one barcode to two new products: the older product keeps it, on every phone alike.
        if (table == "product" && row["barcode"] != null) {
            val other =
                tx.usePrepared("SELECT id FROM product WHERE barcode = ? AND id != ?") { st ->
                    st.bind(1, row["barcode"]).bind(2, id).let {
                        if (st.step()) st.getLong(0) else null
                    }
                }
            if (other != null && other < id as Long) {
                row["barcode"] = null
                row["generated"] = 0L
            } else if (other != null) {
                tx.exec("UPDATE product SET barcode = NULL, generated = 0 WHERE id = ?", other)
            }
        }
        val cols = row.keys.toList()
        val names = cols.joinToString { "`$it`" }
        val conflict = if (table in EDITED) "DO UPDATE SET ${cols.joinToString { "`$it` = excluded.`$it`" }}" else "DO NOTHING"
        tx.exec("INSERT INTO $table ($names) VALUES (${cols.joinToString { "?" }}) ON CONFLICT(`$key`) $conflict", *cols.map(row::get).toTypedArray())
    }

    // --- Small things -----------------------------------------------------

    private suspend fun put(
        key: String,
        value: String,
    ) = dao.putSetting(Setting(key, value, 0))

    private suspend fun put(
        tx: PooledConnection,
        key: String,
        value: String,
    ) = tx.exec("INSERT OR REPLACE INTO setting (key, value, updatedAt) VALUES (?, ?, 0)", key, value)

    private fun SQLiteStatement.bind(
        index: Int,
        value: Any?,
    ): SQLiteStatement {
        when (value) {
            null -> bindNull(index)
            is Long -> bindLong(index, value)
            is Int -> bindLong(index, value.toLong())
            else -> bindText(index, value.toString())
        }
        return this
    }

    private suspend fun PooledConnection.exec(
        sql: String,
        vararg args: Any?,
    ) = usePrepared(sql) { st ->
        args.forEachIndexed { i, a -> st.bind(i + 1, a) }
        st.step()
    }

    companion object {
        /** In apply order. Only the shop's details sync from `setting`. */
        private val TABLES = listOf("product", "setting", "bill", "bill_line", "stock_move")

        /** Changed after they are made; the later edit wins. The others are only ever added. */
        private val EDITED = setOf("product", "setting", "bill")

        private fun keyOf(table: String) = if (table == "setting") "key" else "id"

        /** Two digits in the in-store barcode (Ean). */
        const val MAX_PHONE = 99L
        private const val SNAPSHOT_EVERY = 7 * 24 * 3600 * 1000L

        // Names are written with Locale.ROOT: the phone's language would give
        // Devanagari or Bengali digits in Nepali, Marathi, Bengali or Assamese.
        // Android's \d and toLong() happen to read those too, but desktop Java's
        // \d is 0-9 only, and names on Drive should not depend on the difference.
        private val PHONE = Regex("phone-(\\d+)\\.json")
        private val BATCH = Regex("b-(\\d+)-(\\d+)\\.json\\.gz")
        private val SNAP = Regex("snap-(\\d+)-(\\d+)\\.json\\.gz")

        // This phone's own settings: in `setting`, never synced (the triggers pass only shop.*).
        const val ACCOUNT = "sync.account"
        const val LAST = "sync.last"
        private const val INSTALL = "sync.install"
        private const val SENT = "sync.sent"
        private const val GOT = "sync.got."
        private const val SEEN = "sync.seen"
        private const val SNAP_ID = "sync.snapId"
        private const val SNAPPED = "sync.snapped"
    }
}
