package app.hissa

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeaturesTest {
    private fun exp(paid: Map<String, Long>, shares: Map<String, Long>) = Expense(
        "e", "x", "g", paid.values.sum(), paid.maxBy { it.value }.key, "2026-01-01", "", false, null, null,
        shares.map { SplitRow(it.key, it.value) }, paid.map { PayerRow(it.key, it.value) },
    )

    @Test fun twoPayersNetCorrectly() {
        val e = exp(mapOf("a" to 700, "b" to 500), equalSplit(1200, listOf("a", "b", "c")))
        assertEquals(300L, e.netFor("a")); assertEquals(100L, e.netFor("b")); assertEquals(-400L, e.netFor("c"))
        assertEquals(0L, listOf("a", "b", "c").sumOf { e.netFor(it) })
    }

    @Test fun twoPayerTransfersClearTheDebt() {
        val e = exp(mapOf("a" to 700, "b" to 500), equalSplit(1200, listOf("a", "b", "c")))
        val t = e.transfers()
        assertEquals(400L, t.sumOf { it.cents })
        assertTrue(t.all { it.from == "c" })
    }

    @Test fun categoryGuess() {
        assertEquals(Cat.TRANSPORT, guessCategory("Uber to airport"))
        assertEquals(Cat.TRANSPORT, guessCategory("Airport cab"))
        assertEquals(Cat.FOOD, guessCategory("Dinner at Fisherman's Wharf"))
        assertEquals(Cat.GROCERIES, guessCategory("Groceries"))
        assertEquals(Cat.UTILITIES, guessCategory("Wi-Fi bill"))
        assertEquals(Cat.STAY, guessCategory("Villa booking"))
        assertEquals(Cat.FUEL, guessCategory("Petrol"))
        assertNull(guessCategory("Misc"))
        assertNull(guessCategory(""))
    }

    @Test fun inviteCodeIsFoundInPastedText() {
        assertEquals("a1b2c3d4", extractInviteCode("Join \"Goa\" on Hissa\n\nTap to join:\nhttps://x.github.io/hissa/join/?code=a1b2c3d4\n\nInvite code:\na1b2c3d4"))
        assertEquals("a1b2c3d4", extractInviteCode("hissa://join/A1B2C3D4"))
        assertEquals("a1b2c3d4", extractInviteCode("Code: a1b2c3d4"))
        assertEquals("a1b2c3d4", extractInviteCode("  A1B2C3D4 "))
    }

    @Test fun csvParsesQuotes() {
        val r = parseCsv("a,\"b,c\",\"d\"\"e\"\n1,2,3\n")
        assertEquals(listOf("a", "b,c", "d\"e"), r[0]); assertEquals(listOf("1", "2", "3"), r[1])
    }

    @Test fun plainCsvImportsAsEqualSplit() {
        val p = planImport("Date,Description,Amount\n2026-01-02,Dinner,900\n,,\n2026-01-03,Taxi,120.50\n", listOf("me", "u"), emptyMap(), "me")
        assertEquals(2, p.rows.size)
        assertEquals(90000L, p.rows[0].input.cents); assertEquals(45000L, p.rows[0].input.shares.getValue("u"))
        assertEquals(Cat.FOOD.id, p.rows[0].input.category); assertEquals(Cat.TRANSPORT.id, p.rows[1].input.category)
    }

    @Test fun splitwiseCsvKeepsWhoPaid() {
        val csv = "Date,Description,Category,Cost,Currency,Aarav,Asha\n2026-01-02,Dinner,Food,1000.00,INR,600.00,-600.00\n"
        val p = planImport(csv, listOf("m1", "m2"), mapOf("m1" to listOf("Aarav"), "m2" to listOf("Asha")), "m1")
        val x = p.rows.single().input
        assertEquals(mapOf("m1" to 100000L), x.payers)
        assertEquals(mapOf("m1" to 40000L, "m2" to 60000L), x.shares)
    }

    @Test fun splitwisePaymentBecomesSettlement() {
        val csv = "Date,Description,Category,Cost,Currency,Aarav,Asha\n2026-01-02,Payment,Payment,500.00,INR,500.00,-500.00\n"
        val p = planImport(csv, listOf("m1", "m2"), mapOf("m1" to listOf("Aarav"), "m2" to listOf("Asha")), "m1")
        assertTrue(p.rows.single().settlement)
        assertEquals(mapOf("m2" to 50000L), p.rows.single().input.shares)
    }
}
