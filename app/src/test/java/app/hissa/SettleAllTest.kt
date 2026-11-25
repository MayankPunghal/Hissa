package app.hissa

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettleAllTest {
    private val me = "me"
    private val g1 = Group("g1", "Goa")
    private val g2 = Group("g2", "Flat")
    private val g3 = Group("g3", "Office")
    private var n = 0

    private fun exp(gid: String, paid: Map<String, Long>, shares: Map<String, Long>, settlement: Boolean = false) = Expense(
        "e${n++}", "x", gid, paid.values.sum(), paid.maxBy { it.value }.key, "2026-01-01", "", settlement, null, null,
        shares.map { SplitRow(it.key, it.value) }, paid.map { PayerRow(it.key, it.value) },
    )

    private fun home(vararg e: Expense, groups: List<Group> = listOf(g1, g2, g3)) = HomeData(
        groups, groups.associate { it.id to listOf(me, "ravi", "asha", "kabir") }, emptyMap(), emptyMap(), e.toList(),
    )

    private fun applyAll(h: HomeData, plan: List<Payment>) = h.copy(recent = h.recent + plan.map {
        exp(it.group.id, mapOf(it.from to it.cents), mapOf(it.to to it.cents), true)
    })

    @Test fun owedInOneGroupAndOwingInAnotherNetsOut() {
        // Ravi paid 1000 for two in Goa (I owe 500); I paid 300 for two in Flat (Ravi owes 300)
        val h = home(exp("g1", mapOf("ravi" to 1000), mapOf("ravi" to 500, me to 500)), exp("g2", mapOf(me to 300), mapOf("ravi" to 150, me to 150)))
        val lines = pairwise(me, h)["ravi"].orEmpty()
        val plan = settleAllPlan(me, "ravi", lines)
        assertEquals(2, plan.size)
        assertEquals(setOf(Triple("g1", me, 500L), Triple("g2", "ravi", 150L)), plan.map { Triple(it.group.id, it.from, it.cents) }.toSet())
        assertEquals(mapOf("INR" to -350L), settleNet(me, plan)) // I pay 350 net
    }

    @Test fun planZeroesEveryGroup() {
        val h = home(
            exp("g1", mapOf("ravi" to 1000), mapOf("ravi" to 500, me to 500)),
            exp("g2", mapOf(me to 901), mapOf("ravi" to 451, me to 450)),
            exp("g3", mapOf("asha" to 600), mapOf("asha" to 200, me to 200, "ravi" to 200)),
        )
        val plan = settleAllPlan(me, "ravi", pairwise(me, h)["ravi"].orEmpty())
        assertTrue(pairwise(me, applyAll(h, plan))["ravi"].orEmpty().isEmpty())
    }

    @Test fun settlingOneFriendLeavesOthersAlone() {
        val h = home(exp("g1", mapOf("ravi" to 900), mapOf("ravi" to 300, me to 300, "asha" to 300)))
        val before = pairwise(me, h)["asha"]
        val plan = settleAllPlan(me, "ravi", pairwise(me, h)["ravi"].orEmpty())
        assertEquals(before, pairwise(me, applyAll(h, plan))["asha"])
    }

    @Test fun noOpenBalancesMeansNothingToSettle() {
        assertTrue(settleAllPlan(me, "ravi", emptyList()).isEmpty())
        assertTrue(settleAllPlan(me, "ravi", listOf(PairLine(g1, 0))).isEmpty())
    }

    @Test fun equalAndOppositeBalancesStillRecordBothPayments() {
        val h = home(exp("g1", mapOf("ravi" to 1000), mapOf("ravi" to 500, me to 500)), exp("g2", mapOf(me to 1000), mapOf("ravi" to 500, me to 500)))
        val plan = settleAllPlan(me, "ravi", pairwise(me, h)["ravi"].orEmpty())
        assertEquals(2, plan.size)
        assertEquals(mapOf("INR" to 0L), settleNet(me, plan))
        assertTrue(pairwise(me, applyAll(h, plan))["ravi"].orEmpty().isEmpty())
    }

    @Test fun multiPayerExpenseIsSettledPerPerson() {
        // Ravi 700 and Asha 500 paid 1200, split three ways: I owe 400, split across the payers
        val h = home(exp("g1", mapOf("ravi" to 700, "asha" to 500), equalSplit(1200, listOf("ravi", "asha", me))))
        val total = listOf("ravi", "asha").sumOf { f -> pairwise(me, h)[f].orEmpty().sumOf { it.cents } }
        assertEquals(-400L, total)
        listOf("ravi", "asha").forEach { f ->
            val plan = settleAllPlan(me, f, pairwise(me, h)[f].orEmpty())
            assertTrue(plan.all { it.from == me && it.to == f })
        }
    }

    @Test fun existingPaymentsAreCounted() {
        val h = home(
            exp("g1", mapOf("ravi" to 1000), mapOf("ravi" to 500, me to 500)),
            exp("g1", mapOf(me to 200), mapOf("ravi" to 200), settlement = true), // I already paid 200 back
        )
        val plan = settleAllPlan(me, "ravi", pairwise(me, h)["ravi"].orEmpty())
        assertEquals(listOf(300L), plan.map { it.cents })
    }

    @Test fun oddPaiseSurvive() {
        val h = home(exp("g1", mapOf("ravi" to 1000), equalSplit(1000, listOf("ravi", "asha", me))))
        val plan = settleAllPlan(me, "ravi", pairwise(me, h)["ravi"].orEmpty())
        assertEquals(333L, plan.single().cents)
    }

    @Test fun mixedCurrenciesAreNettedSeparately() {
        val usd = Group("g4", "Trip", "USD")
        val h = home(
            exp("g1", mapOf("ravi" to 1000), mapOf("ravi" to 500, me to 500)),
            exp("g4", mapOf(me to 800), mapOf("ravi" to 400, me to 400)), groups = listOf(g1, usd),
        )
        val net = settleNet(me, settleAllPlan(me, "ravi", pairwise(me, h)["ravi"].orEmpty()))
        assertEquals(mapOf("INR" to -500L, "USD" to 400L), net)
    }
}
