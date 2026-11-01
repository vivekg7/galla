package io.github.vivekg7.galla

import androidx.room3.testing.MigrationTestHelper
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.vivekg7.galla.data.Db
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** A shop's data from Galla 0.1 must come through the upgrade whole: it is the only copy. */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @get:Rule
    val helper =
        MigrationTestHelper(
            instrumentation,
            instrumentation.targetContext.getDatabasePath("migration-test.db"),
            AndroidSQLiteDriver(),
            Db::class,
        )

    @Test fun version1DataSurvivesAndGainsEditTimes() =
        runBlocking {
            helper.createDatabase(1).apply {
                execSQL(
                    "INSERT INTO product (id, name, barcode, generated, unit, price, mrp, cost, stock, lowAt, archived, createdAt, updatedAt) " +
                        "VALUES (1, 'Sugar', '2000000000013', 1, 'KG', 4500, 0, 0, 2000, 0, 0, 10, 10)",
                )
                execSQL("INSERT INTO bill (id, no, at, subtotal, discount, total, payment, void) VALUES (1, 1, 1234, 900, 0, 900, 'PENDING', 0)")
                execSQL("INSERT INTO setting (key, value) VALUES ('shop.name', 'Gupta Kirana')")
                close()
            }
            // Checks the result against the exported schema 2.json, column for column.
            val db = helper.runMigrationsAndValidate(2, listOf(Db.MIGRATION_1_2))

            fun long(sql: String) = db.prepare(sql).use { assertTrue(it.step()).let { _ -> it.getLong(0) } }
            assertEquals(2000L, long("SELECT stock FROM product WHERE id = 1"))
            // A bill's last change is its making, until it is paid or cancelled.
            assertEquals(1234L, long("SELECT updatedAt FROM bill WHERE id = 1"))
            assertEquals(1L, long("SELECT COUNT(*) FROM setting WHERE key = 'shop.name'"))
            assertEquals(8L, long("SELECT COUNT(*) FROM sqlite_master WHERE type = 'trigger' AND name LIKE 'sync_%'"))
            // Sync is off after an upgrade, so nothing is waiting to go up.
            db.execSQL("UPDATE product SET price = 5000 WHERE id = 1")
            assertEquals(0L, long("SELECT COUNT(*) FROM pending"))
            db.close()
        }
}
