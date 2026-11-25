package app.hissa

import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Sample data for debug builds only (launch with the intent extra "demo" = true).
 * Lets the whole UI be reviewed and screenshotted without an account or a network.
 */
object Demo {
    const val ME = "me"
    private val names = mapOf(ME to "Aarav Mehta", "u1" to "Asha Rao", "u2" to "Ravi Iyer", "u3" to "Meera Shah", "u4" to "Kabir Singh")
    private val groups = mutableListOf(
        Group("g1", "Goa trip", "INR", "goa2026x", createdBy = ME),
        Group("g2", "Flat 402", "INR", "flat402a", createdBy = "u1"),
        Group("g3", "Office lunches", "INR", "lunchbox", createdBy = "u3"),
        Group("g4", "Trek crew", "INR", "trek2026", createdBy = "u1"), // joinable with its code
    )
    private val members = mutableMapOf(
        "g1" to listOf(ME, "u1", "u2", "u3", "u4"),
        "g2" to listOf(ME, "u1", "u2"),
        "g3" to listOf(ME, "u3", "u4"),
        "g4" to listOf("u1", "u2"),
    )

    private fun exp(id: String, gid: String, desc: String, rupees: Long, by: String, daysAgo: Long, who: List<String>, settle: Boolean = false, cat: String? = null, also: String? = null): Expense {
        val cents = rupees * 100
        val shares = if (settle) mapOf(who.first() to cents) else equalSplit(cents, who)
        // `also` makes it a two-payer expense: the second person covers 40%.
        val payers = if (also == null) emptyList() else listOf(PayerRow(by, cents - cents * 2 / 5), PayerRow(also, cents * 2 / 5))
        return Expense(
            id, desc, gid, cents, by,
            LocalDate.now().minusDays(daysAgo).toString(),
            Instant.now().minus(daysAgo, ChronoUnit.DAYS).minus(id.length.toLong(), ChronoUnit.MINUTES).toString(),
            settle, cat, null, shares.map { SplitRow(it.key, it.value) }, payers,
        )
    }

    private val g1m = members.getValue("g1")
    private val expenses = mutableListOf(
        exp("e1", "g1", "Villa booking", 28000, "u1", 4, g1m, cat = "stay", also = "u2"),
        exp("e2", "g1", "Scooter rentals", 3200, "u2", 3, listOf(ME, "u2", "u3"), cat = "transport"),
        exp("e3", "g1", "Dinner at Fisherman's Wharf", 6450, ME, 2, g1m, cat = "food"),
        exp("e4", "g1", "Beach shack lunch", 2780, "u4", 2, g1m, cat = "food"),
        exp("e5", "g1", "Airport cab", 1900, "u3", 4, listOf(ME, "u1", "u3"), cat = "transport"),
        exp("e6", "g1", "Payment", 2000, "u4", 1, listOf("u1"), settle = true),
        exp("e7", "g2", "Wi-Fi bill", 1179, ME, 5, members.getValue("g2")),
        exp("e8", "g2", "Groceries", 3640, "u2", 3, members.getValue("g2"), cat = "groceries"),
        exp("e9", "g2", "Electricity", 2450, "u1", 1, members.getValue("g2"), cat = "utilities"),
        exp("e10", "g3", "Thali lunch", 1260, "u3", 0, members.getValue("g3")),
        exp("e11", "g3", "Coffee run", 540, "u4", 6, members.getValue("g3")),
    )

    private fun net(gid: String): Map<String, Long> {
        val list = expenses.filter { it.groupId == gid }
        return members.getValue(gid).associateWith { u -> list.sumOf { it.netFor(u) } }
    }

    fun home(): HomeData {
        val mine = groups.filter { ME in members.getValue(it.id) }
        return HomeData(
            mine, members, names,
            mine.associate { it.id to (net(it.id)[ME] ?: 0L) },
            expenses.filter { e -> mine.any { it.id == e.groupId } }.sortedByDescending { it.createdAt },
            mapOf("u1" to "asha@upi"),
        )
    }

    fun createGroup(name: String, currency: String) {
        val id = "g" + (groups.size + 1)
        groups.add(0, Group(id, name, currency, "new" + id + "xyz", createdBy = ME))
        members[id] = listOf(ME)
    }

    fun joinGroup(code: String) {
        val g = groups.firstOrNull { it.inviteCode.equals(code, true) } ?: error("No group with that code")
        if (ME !in members.getValue(g.id)) members[g.id] = members.getValue(g.id) + ME
    }

    private fun build(id: String, gid: String, x: Api.ExpenseInput, settlement: Boolean) = Expense(
        id, x.desc, gid, x.cents, x.payers.maxByOrNull { it.value }!!.key, x.date.toString(),
        Instant.now().toString(), settlement, x.category, x.notes,
        x.shares.filter { it.value > 0 }.map { SplitRow(it.key, it.value) },
        x.payers.filter { it.value > 0 }.map { PayerRow(it.key, it.value) },
    )

    fun addExpense(gid: String, x: Api.ExpenseInput, settlement: Boolean) { expenses.add(build("x" + System.nanoTime(), gid, x, settlement)) }

    fun editExpense(id: String, x: Api.ExpenseInput) {
        val i = expenses.indexOfFirst { it.id == id }
        if (i >= 0) expenses[i] = build(id, expenses[i].groupId, x, false)
    }

    private val rec = mutableListOf<Pair<String, RecurringRow>>()
    fun addRecurring(gid: String, x: Api.ExpenseInput, every: String, next: LocalDate) { rec.add(gid to RecurringRow("r" + System.nanoTime(), x.desc, x.cents, every, next.toString())) }
    fun recurring(gid: String) = rec.filter { it.first == gid }.map { it.second }
    fun deleteRecurring(id: String) { rec.removeAll { it.second.id == id } }

    fun deleteExpense(id: String) { expenses.removeAll { it.id == id } }

    fun updateGroup(gid: String, name: String, image: String?) {
        val i = groups.indexOfFirst { it.id == gid }
        if (i >= 0) groups[i] = groups[i].copy(name = name, image = image?.ifEmpty { null })
    }

    fun leaveGroup(gid: String) {
        if (net(gid)[ME] != 0L) error("settle up before leaving this group")
        members[gid] = members.getValue(gid) - ME
    }

    fun deleteGroup(gid: String) { groups.removeAll { it.id == gid }; expenses.removeAll { it.groupId == gid } }

    fun group(gid: String) = GroupData(
        members.getValue(gid),
        expenses.filter { it.groupId == gid }.sortedByDescending { it.date },
        net(gid),
    )
}
