package app.hissa

import java.time.LocalDate

/** One expense read from a CSV file, ready to save. */
data class ImportedExpense(val input: Api.ExpenseInput, val settlement: Boolean)
data class ImportPlan(val rows: List<ImportedExpense>, val skipped: Int)

/** Splits CSV text into rows of cells, honouring "quoted, cells" and "" escapes. */
fun parseCsv(text: String): List<List<String>> {
    val rows = ArrayList<List<String>>()
    var row = ArrayList<String>()
    val cell = StringBuilder()
    var quoted = false
    var i = 0
    val t = text.removePrefix("﻿")
    while (i < t.length) {
        val c = t[i]
        when {
            quoted && c == '"' && t.getOrNull(i + 1) == '"' -> { cell.append('"'); i++ }
            c == '"' -> quoted = !quoted
            !quoted && c == ',' -> { row.add(cell.toString().trim()); cell.clear() }
            !quoted && (c == '\n' || c == '\r') -> {
                if (c == '\r' && t.getOrNull(i + 1) == '\n') i++
                row.add(cell.toString().trim()); cell.clear()
                if (row.any { it.isNotEmpty() }) rows.add(row)
                row = ArrayList()
            }
            else -> cell.append(c)
        }
        i++
    }
    row.add(cell.toString().trim())
    if (row.any { it.isNotEmpty() }) rows.add(row)
    return rows
}

/**
 * Turns a CSV export into expenses for this group. Works with Splitwise exports (one column of net balance per person)
 * and with plain Date, Description, Amount files (paid by [me], split equally between everyone).
 * [names] maps each member id to the names that may appear as a column header.
 */
fun planImport(csv: String, members: List<String>, names: Map<String, List<String>>, me: String): ImportPlan {
    val rows = parseCsv(csv)
    if (rows.size < 2) return ImportPlan(emptyList(), 0)
    val head = rows[0].map { it.lowercase() }
    fun col(vararg keys: String) = head.indexOfFirst { h -> keys.any { h == it } }
    val iDate = col("date"); val iDesc = col("description", "title", "name"); val iCat = col("category")
    val iAmt = col("cost", "amount", "total")
    if (iDesc < 0 || iAmt < 0) return ImportPlan(emptyList(), rows.size - 1)
    // member columns: the header matches a member's full or first name
    val memberCol = HashMap<String, Int>()
    head.forEachIndexed { i, h ->
        if (i in listOf(iDate, iDesc, iCat, iAmt) || h == "currency") return@forEachIndexed
        members.firstOrNull { m -> names[m].orEmpty().any { it.lowercase() == h } }?.let { memberCol[it] = i }
    }
    val out = ArrayList<ImportedExpense>()
    var skipped = 0
    rows.drop(1).take(2000).forEach { r ->
        val desc = r.getOrNull(iDesc).orEmpty()
        val cents = r.getOrNull(iAmt)?.replace(",", "")?.let { parseCents(it.removePrefix("-")) }
        if (desc.isBlank() || cents == null || desc.equals("total balance", true)) { skipped++; return@forEach }
        val date = r.getOrNull(iDate)?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now()
        val cat = guessCategory(desc)?.id?.ifEmpty { null }
        val settlement = r.getOrNull(iCat).orEmpty().equals("payment", true) || desc.equals("payment", true) || desc.startsWith("settle", true)

        val nets = memberCol.mapValues { (_, i) -> r.getOrNull(i)?.replace(",", "")?.toBigDecimalOrNull()?.movePointRight(2)?.toLong() ?: 0L }
        val pos = nets.filter { it.value > 0 }
        val x: Api.ExpenseInput? = when {
            memberCol.isNotEmpty() && pos.isNotEmpty() -> {
                val paid = weightedSplit(cents, pos)
                val shares = nets.keys.associateWith { u -> (paid[u] ?: 0L) - nets.getValue(u) }
                if (shares.values.any { it < 0 } || shares.values.sum() != cents) null
                else Api.ExpenseInput(desc, cents, paid, shares.filter { it.value > 0 }, date, if (settlement) null else cat)
            }
            memberCol.isEmpty() && members.isNotEmpty() ->
                Api.ExpenseInput(desc, cents, mapOf((if (me in members) me else members.first()) to cents), equalSplit(cents, members), date, cat)
            else -> null
        }
        if (x == null) skipped++ else out.add(ImportedExpense(x, settlement))
    }
    return ImportPlan(out, skipped)
}
