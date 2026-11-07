package io.github.vivekg7.galla

import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.vivekg7.galla.data.Bill
import io.github.vivekg7.galla.data.BillLine
import io.github.vivekg7.galla.data.Db
import io.github.vivekg7.galla.data.Measure
import io.github.vivekg7.galla.data.Product
import io.github.vivekg7.galla.data.Reason
import io.github.vivekg7.galla.data.Remote
import io.github.vivekg7.galla.data.RemoteFile
import io.github.vivekg7.galla.data.ShopInfo
import io.github.vivekg7.galla.data.Sync
import io.github.vivekg7.galla.data.billNo
import io.github.vivekg7.galla.data.saveShop
import io.github.vivekg7.galla.data.shop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import java.util.Locale

/** Phones that sync through a folder in memory, standing in for Drive (Drive.kt). */
@RunWith(AndroidJUnit4::class)
class SyncTest {
    private class Folder : Remote {
        val files = linkedMapOf<String, Pair<RemoteFile, ByteArray>>()
        private var made = 0L

        override suspend fun list(after: String?) = files.values.map { it.first }.filter { after == null || it.created > after }

        override suspend fun download(
            id: String,
            to: File,
        ) = to.writeBytes(files.getValue(id).second)

        override suspend fun upload(
            name: String,
            from: File,
        ): RemoteFile {
            val file = RemoteFile("f${++made}", name, Instant.ofEpochSecond(1_800_000_000 + made).toString())
            files[file.id] = file to from.readBytes()
            return file
        }

        override suspend fun delete(id: String) {
            files.remove(id)
        }
    }

    private inner class Phone(
        name: String,
    ) {
        val db: Db =
            Room
                .inMemoryDatabaseBuilder(context, Db::class.java)
                .setDriver(AndroidSQLiteDriver())
                .addCallback(Db.Triggers)
                .build()
        val dao = db.dao()
        val sync = Sync(db, File(context.cacheDir, "sync-test/$name/sync").apply { parentFile!!.deleteRecursively() }, "install-$name")

        suspend fun stock(id: Long) = dao.productNow(id)!!.stock

        suspend fun sell(
            productId: Long,
            qty: Long,
        ): Long {
            val price = dao.productNow(productId)!!.price
            val amount = price * qty / 1000
            return dao.checkout(
                Bill(subtotal = amount, discount = 0, total = amount),
                listOf(BillLine(productId = productId, name = "x", unit = Measure.KG, qty = qty, price = price, amount = amount)),
            )
        }
    }

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val folder = Folder()
    private val phones = mutableListOf<Phone>()

    private fun phone(name: String) = Phone(name).also { phones += it }

    private suspend fun syncAll(vararg ps: Phone) =
        repeat(2) {
            for (p in ps) {
                p.sync.sync { folder }
                assertNull(p.sync.status.value.error)
            }
        }

    @After fun close() = phones.forEach { it.db.close() }

    @Test fun phonesShareProductsStockBillsAndShopDetails() =
        runBlocking {
            val a = phone("a")
            val b = phone("b")
            val sugar = a.dao.create(Product(name = "Sugar", unit = Measure.KG, price = 4500, stock = 10_000))
            a.dao.saveShop(ShopInfo(name = "Gupta Kirana", upiId = "gupta@ybl"), ShopInfo())
            // What B had before joining is replaced by the shop's data.
            b.dao.create(Product(name = "Stray", price = 100))

            assertEquals(0L, a.sync.join(folder, "shop@gmail.com", "A"))
            assertEquals(1L, b.sync.join(folder, "shop@gmail.com", "B"))
            assertEquals(
                listOf("Sugar"),
                b.dao
                    .products()
                    .first()
                    .map { it.name },
            )
            assertEquals(
                "Gupta Kirana",
                b.dao
                    .shop()
                    .first()
                    .name,
            )
            assertEquals(10_000L, b.stock(sugar))

            // B sells 2 kg while A restocks 5 kg.
            val bill = b.sell(sugar, 2_000)
            a.dao.moveStock(sugar, 5_000, Reason.RESTOCK)
            syncAll(a, b)
            assertEquals(13_000L, a.stock(sugar))
            assertEquals(13_000L, b.stock(sugar))
            val onA =
                a.dao
                    .bills()
                    .first()
                    .single()
            assertEquals("1-1", billNo(onA.no))
            assertEquals(bill, onA.id)

            // Both cancel the same bill: the 2 kg come back once.
            a.dao.voidBill(bill)
            b.dao.voidBill(bill)
            syncAll(a, b)
            assertEquals(15_000L, a.stock(sugar))
            assertEquals(15_000L, b.stock(sugar))

            // Both edit the price: the later edit wins on both.
            val p = a.dao.productNow(sugar)!!
            a.dao.update(p.copy(price = 4600, updatedAt = p.updatedAt + 1_000))
            b.dao.update(p.copy(price = 4800, updatedAt = p.updatedAt + 2_000))
            // And each edits a different shop detail: both edits stay.
            a.dao.saveShop(
                a.dao
                    .shop()
                    .first()
                    .copy(phone = "98765"),
                a.dao.shop().first(),
            )
            b.dao.saveShop(
                b.dao
                    .shop()
                    .first()
                    .copy(footer = "Dhanyavaad"),
                b.dao.shop().first(),
            )
            syncAll(a, b)
            for (x in listOf(a, b)) {
                assertEquals(4800L, x.dao.productNow(sugar)!!.price)
                assertEquals(15_000L, x.stock(sugar))
                val shop = x.dao.shop().first()
                assertEquals("98765", shop.phone)
                assertEquals("Dhanyavaad", shop.footer)
            }

            // A third phone joins later and gets everything: the snapshot, then the batches since.
            val c = phone("c")
            assertEquals(2L, c.sync.join(folder, "shop@gmail.com", "C"))
            assertNull(c.sync.status.value.error)
            assertEquals(15_000L, c.stock(sugar))
            assertEquals(4800L, c.dao.productNow(sugar)!!.price)
            assertEquals(
                true,
                c.dao
                    .bills()
                    .first()
                    .single()
                    .void,
            )
        }

    @Test fun twoNewProductsWithOneBarcodeLeaveItWithTheOlder() =
        runBlocking {
            val a = phone("a")
            val b = phone("b")
            a.sync.join(folder, "shop@gmail.com", "A")
            b.sync.join(folder, "shop@gmail.com", "B")
            val onA = a.dao.create(Product(name = "Maggi", barcode = "8901058000290", price = 1400))
            val onB = b.dao.create(Product(name = "Maggi noodles", barcode = "8901058000290", price = 1400))
            syncAll(a, b)
            for (x in listOf(a, b)) {
                assertEquals(onA, x.dao.byBarcode("8901058000290")!!.id)
                assertNull(x.dao.productNow(onB)!!.barcode)
            }
        }

    @Test fun aPhoneInNepaliSyncsLikeAnyOther() =
        runBlocking {
            val default = Locale.getDefault()
            // Nepali writes numbers in Devanagari digits: file names must not.
            Locale.setDefault(Locale.forLanguageTag("ne-NP"))
            try {
                val a = phone("a")
                val b = phone("b")
                a.sync.join(folder, "shop@gmail.com", "A")
                b.sync.join(folder, "shop@gmail.com", "B")
                val rice = b.dao.create(Product(name = "Rice", unit = Measure.KG, price = 6000, stock = 5_000))
                syncAll(a, b)
                assertEquals("Rice", a.dao.productNow(rice)!!.name)
                for ((file, _) in folder.files.values) assertTrue(file.name, file.name.all { it.code < 128 })
            } finally {
                Locale.setDefault(default)
            }
        }

    @Test fun dataRestoredOntoAnotherInstallLeavesSync() =
        runBlocking {
            val a = phone("a")
            a.sync.join(folder, "shop@gmail.com", "A")
            val restored = Sync(a.db, File(context.cacheDir, "sync-test/a2/sync"), "another-install")
            assertNull(restored.phone())
            assertNull(a.dao.setting(Db.Triggers.DEVICE))
        }
}
