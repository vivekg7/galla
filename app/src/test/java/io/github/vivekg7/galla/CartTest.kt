package io.github.vivekg7.galla

import io.github.vivekg7.galla.data.Cart
import io.github.vivekg7.galla.data.Measure
import io.github.vivekg7.galla.data.Product
import io.github.vivekg7.galla.data.qtyForAmount
import org.junit.Assert.assertEquals
import org.junit.Test

class CartTest {
    private val sugar = Product(id = 2, name = "Sugar", unit = Measure.KG, price = 4600)
    private val biscuit = Product(id = 1, name = "Biscuit", price = 2500)

    /** ₹20 of sugar at ₹46/kg is 434.78 g; pricing the rounded 435 g would charge ₹20.01. */
    @Test fun askedForAnAmountCostsExactlyThat() {
        val cart = Cart()
        cart.add(sugar, qtyForAmount(sugar.price, 2000), fixed = 2000)
        assertEquals(435L, cart.lines[0].qty)
        assertEquals(2000L, cart.subtotal)
        // More of it by weight keeps the ₹20 exact and adds 250 g at the kilo price.
        cart.add(sugar, 250)
        assertEquals(685L, cart.lines[0].qty)
        assertEquals(2000L + 1150L, cart.subtotal)
    }

    @Test fun rescanningAnItemCountsItAgainOnOneLine() {
        val cart = Cart()
        cart.add(biscuit, 1000)
        cart.add(sugar, 500)
        cart.add(biscuit, 1000)
        assertEquals(listOf("Biscuit", "Sugar"), cart.lines.map { it.name })
        assertEquals(2000L, cart.lines[0].qty)
        cart.discount = 500
        assertEquals(5000L + 2300L - 500L, cart.total)
    }
}
