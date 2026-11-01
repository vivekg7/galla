package io.github.vivekg7.galla.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** What the slip prints at the top and where UPI payments go. Stored in the `setting` table. */
data class ShopInfo(
    val name: String = "",
    val address: String = "",
    val phone: String = "",
    val upiId: String = "",
    /** The name the customer's UPI app shows; defaults to the shop's. */
    val payee: String = "",
    val footer: String = "Thank you, visit again!",
) {
    val payeeName get() = payee.ifBlank { name.ifBlank { "Shop" } }
    val canTakeUpi get() = isUpiId(upiId)

    fun toSettings() =
        listOf(
            Setting(NAME, name),
            Setting(ADDRESS, address),
            Setting(PHONE, phone),
            Setting(UPI, upiId),
            Setting(PAYEE, payee),
            Setting(FOOTER, footer),
        )

    companion object {
        private const val NAME = "shop.name"
        private const val ADDRESS = "shop.address"
        private const val PHONE = "shop.phone"
        private const val UPI = "shop.upi"
        private const val PAYEE = "shop.payee"
        private const val FOOTER = "shop.footer"

        fun from(settings: List<Setting>): ShopInfo {
            val map = settings.associate { it.key to it.value }
            val default = ShopInfo()
            return ShopInfo(
                name = map[NAME] ?: default.name,
                address = map[ADDRESS] ?: default.address,
                phone = map[PHONE] ?: default.phone,
                upiId = map[UPI] ?: default.upiId,
                payee = map[PAYEE] ?: default.payee,
                footer = map[FOOTER] ?: default.footer,
            )
        }
    }
}

fun ShopDao.shop(): Flow<ShopInfo> = settings().map(ShopInfo::from)

/**
 * Writes only the fields that changed: each is its own setting, and with
 * phones syncing, the last edit of each field wins. Writing all of them would
 * undo another phone's edit of a field this one did not touch.
 */
suspend fun ShopDao.saveShop(
    info: ShopInfo,
    old: ShopInfo,
) = info
    .toSettings()
    .zip(old.toSettings())
    .filter { (new, was) -> new.value != was.value }
    .forEach { putSetting(it.first) }

/** One line of the bill being made. [productId] is null for an item typed in at the counter. */
data class CartLine(
    val productId: Long?,
    val name: String,
    val unit: Measure,
    val price: Long,
    val qty: Long,
    /**
     * Set when the customer asked for an amount of money ("₹20 of sugar"):
     * the line costs exactly that, and [qty] is the weight rounded to the
     * gram. Otherwise the amount follows from price and quantity.
     */
    val fixed: Long? = null,
) {
    val amount get() = fixed ?: lineAmount(price, qty)
}

/**
 * The bill being made. It lives in [io.github.vivekg7.galla.App], not in a
 * screen, so a trip to Inventory to add a missing product does not lose it.
 */
class Cart {
    val lines = mutableStateListOf<CartLine>()
    var discount by mutableLongStateOf(0L)

    val subtotal get() = lines.sumOf { it.amount }
    val total get() = (subtotal - discount).coerceAtLeast(0)

    /** Adds to the product's line if it has one, and brings that line to the top where the cashier is looking. */
    fun add(
        product: Product,
        qty: Long,
        fixed: Long? = null,
    ) {
        val at = lines.indexOfFirst { it.productId == product.id }
        val line =
            if (at >= 0) {
                lines.removeAt(at).let {
                    // Once either part was bought by amount, the line keeps an exact total.
                    val total = if (fixed != null || it.fixed != null) it.amount + (fixed ?: lineAmount(it.price, qty)) else null
                    it.copy(qty = it.qty + qty, fixed = total)
                }
            } else {
                CartLine(product.id, product.name, product.unit, product.price, qty, fixed)
            }
        lines.add(0, line)
    }

    fun addCustom(
        name: String,
        price: Long,
        qty: Long,
    ) = lines.add(0, CartLine(null, name, Measure.PIECE, price, qty))

    fun setQty(
        index: Int,
        qty: Long,
        fixed: Long? = null,
    ) {
        if (qty <= 0) lines.removeAt(index) else lines[index] = lines[index].copy(qty = qty, fixed = fixed)
    }

    fun clear() {
        lines.clear()
        discount = 0
    }

    /** Saves the bill, takes the items out of stock, empties the cart; returns the bill's id. */
    suspend fun checkout(dao: ShopDao): Long {
        val bill = Bill(subtotal = subtotal, discount = discount.coerceAtMost(subtotal), total = total)
        val rows =
            lines.reversed().map {
                BillLine(
                    productId = it.productId,
                    name = it.name,
                    unit = it.unit,
                    qty = it.qty,
                    price = it.price,
                    amount = it.amount,
                )
            }
        val id = dao.checkout(bill, rows)
        clear()
        return id
    }
}
