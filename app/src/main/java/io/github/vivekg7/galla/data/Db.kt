package io.github.vivekg7.galla.data

import android.content.Context
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
import androidx.sqlite.driver.AndroidSQLiteDriver
import kotlinx.coroutines.flow.Flow

/**
 * Everything the shop knows, in one SQLite file: the file is the backup
 * (Backup.kt), so settings live here too rather than in SharedPreferences.
 *
 * Money is whole paise and quantities are thousandths of a unit (grams,
 * millilitres, or 1000 per piece), both as Long: no floating-point rounding
 * anywhere a rupee is counted.
 */
@Database(
    entities = [Product::class, StockMove::class, Bill::class, BillLine::class, Setting::class],
    version = 1,
)
abstract class Db : RoomDatabase() {
    abstract fun dao(): ShopDao

    companion object {
        const val FILE = "galla.db"

        // No fallbackToDestructiveMigration: this is the shop's only copy of
        // its data, so a schema change without a migration must fail loudly.
        fun open(context: Context): Db =
            Room
                .databaseBuilder(context, Db::class.java, FILE)
                .setDriver(AndroidSQLiteDriver())
                .build()
    }
}

@Dao
interface ShopDao {
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

    @Query("UPDATE product SET stock = stock + :delta, updatedAt = :now WHERE id = :id")
    suspend fun addStock(
        id: Long,
        delta: Long,
        now: Long,
    )

    @Insert
    suspend fun insertMove(move: StockMove)

    @Query("SELECT * FROM stock_move WHERE productId = :productId ORDER BY at DESC LIMIT 30")
    fun moves(productId: Long): Flow<List<StockMove>>

    /** Every stock change goes through here, so the log always explains the count. */
    @Transaction
    suspend fun moveStock(
        productId: Long,
        delta: Long,
        reason: Reason,
        billId: Long? = null,
        now: Long = System.currentTimeMillis(),
    ) {
        addStock(productId, delta, now)
        insertMove(StockMove(productId = productId, delta = delta, reason = reason, billId = billId, at = now))
    }

    /** A new product and its opening stock, logged like any restock. */
    @Transaction
    suspend fun create(product: Product): Long {
        val id = insert(product.copy(stock = 0))
        if (product.stock != 0L) moveStock(id, product.stock, Reason.RESTOCK)
        return id
    }

    // --- Bills ------------------------------------------------------------

    @Query("SELECT COALESCE(MAX(no), 0) + 1 FROM bill")
    suspend fun nextBillNo(): Long

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
        val id = insertBill(bill.copy(no = nextBillNo()))
        insertLines(lines.map { it.copy(billId = id) })
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

    @Query("UPDATE bill SET payment = :payment WHERE id = :id")
    suspend fun setPayment(
        id: Long,
        payment: Payment,
    )

    @Query("UPDATE bill SET void = 1 WHERE id = :id AND void = 0")
    suspend fun markVoid(id: Long): Int

    /** Cancels a bill and puts its items back on the shelf. */
    @Transaction
    suspend fun voidBill(id: Long) {
        if (markVoid(id) == 0) return
        for (line in linesNow(id)) line.productId?.let { moveStock(it, line.qty, Reason.VOID, id) }
    }

    // --- Settings ---------------------------------------------------------

    @Query("SELECT * FROM setting")
    fun settings(): Flow<List<Setting>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putSetting(setting: Setting)
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
)
