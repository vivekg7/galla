package io.github.vivekg7.galla

import io.github.vivekg7.galla.data.Ean
import io.github.vivekg7.galla.data.Measure
import io.github.vivekg7.galla.data.isUpiId
import io.github.vivekg7.galla.data.lineAmount
import io.github.vivekg7.galla.data.parsePaise
import io.github.vivekg7.galla.data.parseQty
import io.github.vivekg7.galla.data.qtyForAmount
import io.github.vivekg7.galla.data.qtyText
import io.github.vivekg7.galla.data.rupees
import io.github.vivekg7.galla.data.upiLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyTest {
    @Test fun rupeesUseIndianGrouping() {
        assertEquals("₹1,23,456.50", rupees(12_345_650))
        assertEquals("₹40", rupees(4000))
        assertEquals("₹1,00,00,000.05", rupees(1_000_000_005))
        assertEquals("₹999", rupees(99_900))
    }

    /** A typo in a price must be refused, not read as a different price. */
    @Test fun pricesParseExactly() {
        assertEquals(1250L, parsePaise("12.5"))
        assertEquals(123_400L, parsePaise("₹1,234"))
        assertNull(parsePaise("12.555"))
        assertNull(parsePaise("-3"))
        assertNull(parsePaise("abc"))
    }

    /** 250 g of rice at ₹62/kg, both ways round, with no float drift. */
    @Test fun looseItemsRoundToThePaisa() {
        assertEquals(1550L, lineAmount(6200, 250))
        assertEquals(333L, lineAmount(1000, 333))
        assertEquals(500L, qtyForAmount(6200, 3100))
        assertEquals(1250L, parseQty("1.25"))
        assertEquals("250 g", qtyText(250, Measure.KG))
        assertEquals("1.25 kg", qtyText(1250, Measure.KG))
        assertEquals("3 pc", qtyText(3000, Measure.PIECE))
    }

    /** A wrong check digit makes the code unscannable, or worse, another product's. */
    @Test fun generatedBarcodesAreValidInStoreEan13() {
        assertEquals(1, Ean.checkDigit("400638133393")) // a published EAN-13: 4006381333931
        val code = Ean.forProduct(42)
        assertEquals("200000000042", code.take(12))
        assertTrue(Ean.isValid(code))
        assertFalse(Ean.isValid("2000000000420"))
    }

    @Test fun upiLinkCarriesPayeeAndExactAmount() {
        assertEquals(
            "upi://pay?pa=shop@okaxis&pn=Gupta%20Kirana&am=123.50&cu=INR&tn=Bill%2012",
            upiLink(" shop@okaxis ", "Gupta Kirana", 12_350, "Bill 12"),
        )
        assertTrue(isUpiId("9876543210@ybl"))
        assertFalse(isUpiId("9876543210"))
    }
}
