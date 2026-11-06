package io.github.vivekg7.galla.data

import android.content.Context
import androidx.room3.ColumnInfo
import androidx.room3.Dao
import androidx.room3.Database
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.room3.Transaction
import androidx.room3.Update
import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.flow.Flow

/**
 * Everything the shop knows, in one SQLite file: the file is the backup
 * (Backup.kt), so settings live here too rather than in SharedPreferences.
 *
 * Money is whole paise and quantities are thousandths of a unit (grams,
 * millilitres, or 1000 per piece), both as Long: no floating-point rounding
 * anywhere a rupee is counted.
 *
 * Ids are numbered per phone so that phones syncing through Drive never hand
 * out the same one: phone n uses n × [ID_SPAN] + 1 onwards. A phone that
 * never syncs is phone 0, numbering from 1 as it always did
 * (docs/plans/002_sync_between_phones.md).
 */
@Database(
    entities = [Product::class, StockMove::class, Bill::class, BillLine::class, Setting::class, Pending::class],
    version = 2,
)
abstract class Db : RoomDatabase() {
    abstract fun dao(): ShopDao

    companion object {
        const val FILE = "galla.db"
        const val VERSION = 2

        // No fallbackToDestructiveMigration: this is the shop's only copy of
        // its data, so a schema change without a migration must fail loudly.
        fun open(context: Context): Db =
            Room
                .databaseBuilder(context, Db::class.java, FILE)
                .setDriver(AndroidSQLiteDriver())
                .addMigrations(MIGRATION_1_2)
                .addCallback(Triggers)
                .build()

        /** Edit times for last-edit-wins sync, and the outbox of changes not yet uploaded. */
        val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override suspend fun migrate(connection: SQLiteConnection) {
                    connection.execSQL("ALTER TABLE bill ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                    connection.execSQL("UPDATE bill SET updatedAt = at")
                    connection.execSQL("ALTER TABLE setting ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                    connection.execSQL(
                        "CREATE TABLE IF NOT EXISTS `pending` (`seq` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`tbl` TEXT NOT NULL, `rid` TEXT NOT NULL)",
                    )
                    Triggers.create(connection)
                }
            }
    }

    /**
     * While this phone syncs, every change to the shop's data leaves a row in
     * [Pending], so the next upload knows what to send. Triggers rather than
     * DAO code, so no write path can forget. They are quiet while sync is off,
     * and while changes from other phones are applied (the `sync.applying`
     * setting, set only inside that transaction).
     */
    object Triggers : Callback() {
        override suspend fun onCreate(connection: SQLiteConnection) = create(connection)

        fun create(connection: SQLiteConnection) {
            val on = "EXISTS (SELECT 1 FROM setting WHERE key = '$DEVICE') AND NOT EXISTS (SELECT 1 FROM setting WHERE key = '$APPLYING')"
            // Stock is left out: every phone adds it up from the stock_move log.
            val productColumns = "name, barcode, generated, unit, price, mrp, cost, lowAt, archived"
            val triggers =
                listOf(
                    Triple("product_add", "INSERT ON product", "'product', NEW.id"),
                    Triple("product_edit", "UPDATE OF $productColumns ON product", "'product', NEW.id"),
                    Triple("move_add", "INSERT ON stock_move", "'stock_move', NEW.id"),
                    Triple("bill_add", "INSERT ON bill", "'bill', NEW.id"),
                    Triple("bill_edit", "UPDATE ON bill", "'bill', NEW.id"),
                    Triple("line_add", "INSERT ON bill_line", "'bill_line', NEW.id"),
                    Triple("setting_add", "INSERT ON setting", "'setting', NEW.key"),
                    Triple("setting_edit", "UPDATE ON setting", "'setting', NEW.key"),
                )
            for ((name, event, values) in triggers) {
                // Only the shop's details sync; this phone's own settings stay here.
                val shared = if (name.startsWith("setting")) " AND NEW.key LIKE 'shop.%'" else ""
                connection.execSQL(
                    "CREATE TRIGGER IF NOT EXISTS sync_$name AFTER $event WHEN $on$shared " +
                        "BEGIN INSERT INTO pending (tbl, rid) VALUES ($values); END",
                )
            }
        }

        const val DEVICE = "sync.device"
        const val APPLYING = "sync.applying"
    }
}

/** Ids per phone: phone n numbers from n × ID_SPAN + 1. Nine digits each, which the in-store barcode needs. */
const val ID_SPAN = 1_000_000_000L

@Dao
interface ShopDao {
    /** Where this phone's ids start: 0 until it joins a shop's sync as phone n > 0. */
    @Query("SELECT COALESCE((SELECT CAST(value AS INTEGER) FROM setting WHERE key = 'sync.device'), 0) * 1000000000")
    suspend fun idBase(): Long

    @Query("SELECT COALESCE(MAX(id), :base) + 1 FROM product WHERE id > :base AND id <= :base + 999999999")
    suspend fun nextProductId(base: Long): Long

    @Query("SELECT COALESCE(MAX(id), :base) + 1 FROM stock_move WHERE id > :base AND id <= :base + 999999999")
    suspend fun nextMoveId(base: Long): Long

    @Query("SELECT COALESCE(MAX(id), :base) + 1 FROM bill WHERE id > :base AND id <= :base + 999999999")
    suspend fun nextBillId(base: Long): Long

    @Query("SELECT COALESCE(MAX(id), :base) + 1 FROM bill_line WHERE id > :base AND id <= :base + 999999999")
    suspend fun nextLineId(base: Long): Long

    // --- Products ---------------------------------------------------------

    /** Archived ones too; Inventory hides them unless asked. */
    @Query("SELECT * FROM product ORDER BY name COLLATE NOCASE")
    fun products(): Flow<List<Product>>

    @Query("SELECT * FROM product WHERE id = :id")
    fun product(id: Long): Flow<Product?>

    @Query("SELECT * FROM product WHERE id = :id")
    suspend fun productNow(id: Long): Product?

    /** Archived ones too: a hidden product scanned at the counter still sells. */
    @Query("SELECT * FROM product WHERE barcode = :code")
    suspend fun byBarcode(code: String): Product?

    @Insert
    suspend fun insert(product: Product): Long

    @Update
    suspend fun update(product: Product)

    @Query("UPDATE product SET barcode = :code, generated = 1, updatedAt = :now WHERE id = :id")
    suspend fun setGeneratedBarcode(
        id: Long,
        code: String,
        now: Long,
    )

    // Not updatedAt: that is the time of the last edit, which decides between
    // two phones' edits, and a sale is not an edit of the product.
    @Query("UPDATE product SET stock = stock + :delta WHERE id = :id")
    suspend fun addStock(
        id: Long,
        delta: Long,
    )

    @Insert
    suspend fun insertMove(move: StockMove)

    @Query("SELECT * FROM stock_move WHERE productId = :productId ORDER BY at DESC LIMIT 30")
    fun moves(productId: Long): Flow<List<StockMove>>

    /**
     * Every stock change goes through here, so the log always explains the
     * count. [id] is given only where two phones must agree on it (voidBill).
     */
    @Transaction
    suspend fun moveStock(
        productId: Long,
        delta: Long,
        reason: Reason,
        billId: Long? = null,
        now: Long = System.currentTimeMillis(),
        id: Long? = null,
    ) {
        addStock(productId, delta)
        val moveId = id ?: nextMoveId(idBase())
        insertMove(StockMove(id = moveId, productId = productId, delta = delta, reason = reason, billId = billId, at = now))
    }

    /** A new product and its opening stock, logged like any restock. */
    @Transaction
    suspend fun create(product: Product): Long {
        val id = insert(product.copy(id = nextProductId(idBase()), stock = 0))
        if (product.stock != 0L) moveStock(id, product.stock, Reason.RESTOCK)
        return id
    }

    // --- Bills ------------------------------------------------------------

    @Query("SELECT COALESCE(MAX(no), :base) + 1 FROM bill WHERE no > :base AND no <= :base + 999999999")
    suspend fun nextBillNo(base: Long): Long

    @Insert
    suspend fun insertBill(bill: Bill): Long

    @Insert
    suspend fun insertLines(lines: List<BillLine>)

    /** Saves the bill and takes its items out of stock, all or nothing. */
    @Transaction
    suspend fun checkout(
        bill: Bill,
        lines: List<BillLine>,
    ): Long {
        val now = bill.at
        val base = idBase()
        val id = insertBill(bill.copy(id = nextBillId(base), no = nextBillNo(base)))
        val first = nextLineId(base)
        insertLines(lines.mapIndexed { i, line -> line.copy(id = first + i, billId = id) })
        for (line in lines) line.productId?.let { moveStock(it, -line.qty, Reason.SALE, id, now) }
        return id
    }

    @Query("SELECT * FROM bill ORDER BY at DESC LIMIT :limit")
    fun bills(limit: Int = 500): Flow<List<Bill>>

    @Query("SELECT * FROM bill WHERE at >= :from AND void = 0")
    fun billsSince(from: Long): Flow<List<Bill>>

    @Query("SELECT * FROM bill WHERE id = :id")
    fun bill(id: Long): Flow<Bill?>

    @Query("SELECT * FROM bill_line WHERE billId = :billId ORDER BY id")
    fun lines(billId: Long): Flow<List<BillLine>>

    @Query("SELECT * FROM bill_line WHERE billId = :billId ORDER BY id")
    suspend fun linesNow(billId: Long): List<BillLine>

    @Query("UPDATE bill SET payment = :payment, updatedAt = :now WHERE id = :id")
    suspend fun setPayment(
        id: Long,
        payment: Payment,
        now: Long,
    )

    @Query("UPDATE bill SET void = 1, updatedAt = :now WHERE id = :id AND void = 0")
    suspend fun markVoid(
        id: Long,
        now: Long,
    ): Int

    /**
     * Cancels a bill and puts its items back on the shelf. Each return is
     * numbered after its bill line (negative, outside every phone's range),
     * so two phones cancelling the same bill return its items only once.
     */
    @Transaction
    suspend fun voidBill(id: Long) {
        val now = System.currentTimeMillis()
        if (markVoid(id, now) == 0) return
        for (line in linesNow(id)) line.productId?.let { moveStock(it, line.qty, Reason.VOID, id, now, id = -line.id) }
    }

    // --- Settings ---------------------------------------------------------

    @Query("SELECT * FROM setting")
    fun settings(): Flow<List<Setting>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putSetting(setting: Setting)

    /** In one transaction, so the screen sees them land together (saveShop). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putSettings(settings: List<Setting>)

    @Query("SELECT value FROM setting WHERE key = :key")
    suspend fun setting(key: String): String?

    @Query("DELETE FROM setting WHERE key = :key")
    suspend fun deleteSetting(key: String)

    // --- Sync -------------------------------------------------------------

    @Query("SELECT COUNT(*) FROM pending")
    fun pendingCount(): Flow<Int>
}

/** How a product is counted and priced. Loose ones are weighed or measured. */
enum class Measure(
    val label: String,
    val short: String,
    val loose: Boolean,
) {
    PIECE("Piece", "pc", false),
    PACKET("Packet", "pkt", false),
    BOX("Box", "box", false),
    DOZEN("Dozen", "dz", false),
    KG("Kilogram (loose)", "kg", true),
    LITRE("Litre (loose)", "L", true),
}

@Entity(tableName = "product", indices = [Index(value = ["barcode"], unique = true), Index("name")])
data class Product(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** The printed barcode, or one Galla made ([generated]); null until either. */
    val barcode: String? = null,
    val generated: Boolean = false,
    val unit: Measure = Measure.PIECE,
    /** Selling price for one [unit], in paise. */
    val price: Long,
    val mrp: Long = 0,
    val cost: Long = 0,
    /** In thousandths of a [unit]; may go below zero when sales outrun restocks. */
    val stock: Long = 0,
    /** Warn at or below this stock; 0 means never. */
    val lowAt: Long = 0,
    val archived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val low get() = lowAt > 0 && stock <= lowAt
}

enum class Reason { RESTOCK, SALE, ADJUST, VOID }

@Entity(tableName = "stock_move", indices = [Index("productId")])
data class StockMove(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long,
    val delta: Long,
    val reason: Reason,
    val billId: Long? = null,
    val at: Long,
)

enum class Payment(
    val label: String,
) {
    PENDING("Unpaid"),
    CASH("Cash"),
    UPI("UPI"),
}

@Entity(tableName = "bill", indices = [Index(value = ["no"], unique = true), Index("at")])
data class Bill(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Shown on the slip; counts up from 1 and is never reused. */
    val no: Long = 0,
    val at: Long = System.currentTimeMillis(),
    val subtotal: Long,
    val discount: Long,
    val total: Long,
    val payment: Payment = Payment.PENDING,
    val void: Boolean = false,
    /** The last change (payment or cancel); the later one wins when two phones change the same bill. */
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = at,
)

/**
 * One line of a bill. Name, unit and price are copied from the product at the
 * time of sale, so editing the product later never changes an old slip.
 * [productId] is null for an item typed in at the counter.
 */
@Entity(tableName = "bill_line", indices = [Index("billId")])
data class BillLine(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val billId: Long = 0,
    val productId: Long?,
    val name: String,
    val unit: Measure,
    val qty: Long,
    val price: Long,
    val amount: Long,
)

@Entity(tableName = "setting")
data class Setting(
    @PrimaryKey val key: String,
    val value: String,
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = System.currentTimeMillis(),
)

/** A row changed on this phone and not yet uploaded: [tbl] and the row's key. Filled by [Db.Triggers]. */
@Entity(tableName = "pending")
data class Pending(
    @PrimaryKey(autoGenerate = true) val seq: Long = 0,
    val tbl: String,
    val rid: String,
)
