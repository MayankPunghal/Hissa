package app.hissa

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DebtsTest {
    private val ids = listOf("a", "b", "c")

    @Test fun equalSplitSumsExactly() {
        val s = equalSplit(100, ids)
        assertEquals(100L, s.values.sum())
        assertEquals(listOf(34L, 33L, 33L), s.values.toList())
    }

    @Test fun sharesAreProportional() {
        val s = buildSplit(Mode.SHARES, 100, listOf("a", "b"), mapOf("a" to "2", "b" to "1")).getOrThrow()
        assertEquals(mapOf("a" to 67L, "b" to 33L), s)
    }

    @Test fun percentMustReach100() {
        val v = mapOf("a" to "50", "b" to "30")
        assertTrue(buildSplit(Mode.PERCENT, 1000, listOf("a", "b"), v).isFailure)
        val ok = buildSplit(Mode.PERCENT, 1000, listOf("a", "b"), v + ("b" to "50")).getOrThrow()
        assertEquals(mapOf("a" to 500L, "b" to 500L), ok)
    }

    @Test fun exactMustMatchTotal() {
        val v = mapOf("a" to "6", "b" to "3.99")
        assertTrue(buildSplit(Mode.EXACT, 1000, listOf("a", "b"), v).isFailure)
        assertEquals(1000L, buildSplit(Mode.EXACT, 1000, listOf("a", "b"), v + ("b" to "4")).getOrThrow().values.sum())
    }

    @Test fun oddWeightsStillSumExactly() {
        val s = weightedSplit(1001, mapOf("a" to 1L, "b" to 1L, "c" to 1L))
        assertEquals(1001L, s.values.sum())
        val t = weightedSplit(99_999, mapOf("a" to 7L, "b" to 13L, "c" to 29L))
        assertEquals(99_999L, t.values.sum())
    }

    @Test fun simplifyClearsAllBalances() {
        val net = mapOf("a" to -50L, "b" to 30L, "c" to 20L)
        val t = simplify(net)
        val after = net.toMutableMap()
        t.forEach { after[it.from] = after[it.from]!! + it.cents; after[it.to] = after[it.to]!! - it.cents }
        assertEquals(true, after.values.all { it == 0L })
        assertEquals(2, t.size)
    }

    private fun exp(by: String, to: String, cents: Long) =
        Expense("e$by$to", "x", "g", cents, by, "2026-01-01", "", false, splits = listOf(SplitRow(to, cents)))

    @Test fun chainBecomesOneTransfer() {
        // B paid 20 for A (A owes B 20); C paid 20 for B (B owes C 20)
        val ex = listOf(exp("B", "A", 2000), exp("C", "B", 2000))
        assertEquals(listOf(Transfer("A", "B", 2000), Transfer("B", "C", 2000)).toSet(), rawTransfers(ex).toSet())
        val net = mapOf("A" to -2000L, "B" to 0L, "C" to 2000L)
        assertEquals(listOf(Transfer("A", "C", 2000)), simplify(net))
    }

    @Test fun opposingDebtsCancelWithoutSimplify() {
        val ex = listOf(exp("B", "A", 3000), exp("A", "B", 1000))
        assertEquals(listOf(Transfer("A", "B", 2000)), rawTransfers(ex))
    }
}
