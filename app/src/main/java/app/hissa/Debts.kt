package app.hissa

import java.math.BigDecimal
import java.math.RoundingMode

data class Transfer(val from: String, val to: String, val cents: Long)

enum class Mode(val label: String) { EQUAL("Equally"), EXACT("Exact"), PERCENT("Percent"), SHARES("Shares") }

/** net: positive = is owed, negative = owes. Greedy match; at most n-1 transfers. */
fun simplify(net: Map<String, Long>): List<Transfer> {
    val debtors = net.filter { it.value < 0 }.map { it.key to -it.value }.sortedByDescending { it.second }.toMutableList()
    val creditors = net.filter { it.value > 0 }.map { it.key to it.value }.sortedByDescending { it.second }.toMutableList()
    val out = mutableListOf<Transfer>()
    var i = 0
    var j = 0
    while (i < debtors.size && j < creditors.size) {
        val pay = minOf(debtors[i].second, creditors[j].second)
        out += Transfer(debtors[i].first, creditors[j].first, pay)
        debtors[i] = debtors[i].first to debtors[i].second - pay
        creditors[j] = creditors[j].first to creditors[j].second - pay
        if (debtors[i].second == 0L) i++
        if (creditors[j].second == 0L) j++
    }
    return out
}

/** Proportional split (largest remainder): shares always add up to [total] exactly. */
fun weightedSplit(total: Long, weights: Map<String, Long>): Map<String, Long> {
    val sum = weights.values.sum()
    val out = weights.mapValues { total * it.value / sum }.toMutableMap()
    var left = total - out.values.sum()
    for (k in weights.keys.sortedByDescending { total * weights.getValue(it) % sum }) {
        if (left == 0L) break
        out[k] = out.getValue(k) + 1
        left--
    }
    return out
}

fun equalSplit(total: Long, ids: List<String>): Map<String, Long> =
    weightedSplit(total, ids.associateWith { 1L })

/** "12.5" -> 1250 (hundredths). Null if not a positive number. */
fun parseCents(s: String): Long? = try {
    BigDecimal(s.trim()).movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong().takeIf { it > 0 }
} catch (e: Exception) { null }

/**
 * Turns what the user typed into exact per-person cents.
 * [values] are per-person inputs: amounts (EXACT), percentages (PERCENT) or share counts (SHARES); ignored for EQUAL.
 */
fun buildSplit(mode: Mode, total: Long, chosen: List<String>, values: Map<String, String>): Result<Map<String, Long>> = runCatching {
    require(chosen.isNotEmpty()) { "Pick at least one person" }
    fun units(id: String) = parseCents(values[id].orEmpty()) ?: error("Enter a value for everyone selected")
    when (mode) {
        Mode.EQUAL -> equalSplit(total, chosen)
        Mode.SHARES -> weightedSplit(total, chosen.associateWith { units(it) })
        Mode.PERCENT -> {
            val w = chosen.associateWith { units(it) }
            require(w.values.sum() == 10_000L) { "Percentages must add up to 100" }
            weightedSplit(total, w)
        }
        Mode.EXACT -> {
            val c = chosen.associateWith { units(it) }
            require(c.values.sum() == total) { "Amounts must add up to the total" }
            c
        }
    }
}

/** Who owes whom without combining debts across people (the "Simplify group debts" switch off). */
fun rawTransfers(expenses: List<Expense>): List<Transfer> {
    val owed = HashMap<Pair<String, String>, Long>() // (debtor, creditor) -> cents
    expenses.forEach { e ->
        e.transfers().forEach { t -> owed.merge(t.from to t.to, t.cents, Long::plus) }
    }
    return owed.keys.mapNotNull { (a, b) ->
        val net = (owed[a to b] ?: 0L) - (owed[b to a] ?: 0L)
        if (net > 0) Transfer(a, b, net) else null
    }.sortedByDescending { it.cents }
}

/** Who pays whom to square this one expense (handles several payers). */
fun Expense.transfers(): List<Transfer> =
    simplify((payers.map { it.userId } + splits.map { it.userId }).distinct().associateWith { netFor(it) })
