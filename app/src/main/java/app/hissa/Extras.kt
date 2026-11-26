package app.hissa

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.YearMonth

// ───────────────────────────── Friends ─────────────────────────────

/** What one friend owes you (+) or you owe them (-) in one group. */
data class PairLine(val group: Group, val cents: Long)

/**
 * Splitwise-style person-to-person balances: on every expense the payer is owed each other person's share.
 * Only expenses you are part of count, and a payment simply flows the same way.
 */
fun pairwise(me: String, d: HomeData): Map<String, List<PairLine>> {
    val out = HashMap<String, MutableMap<String, Long>>() // friend -> groupId -> cents
    fun add(friend: String, gid: String, c: Long) {
        val m = out.getOrPut(friend) { HashMap() }; m[gid] = (m[gid] ?: 0L) + c
    }
    d.recent.forEach { e ->
        e.transfers().forEach { t -> if (t.to == me) add(t.from, e.groupId, t.cents) else if (t.from == me) add(t.to, e.groupId, -t.cents) }
    }
    return out.mapValues { (_, m) -> d.groups.mapNotNull { g -> m[g.id]?.let { PairLine(g, it) } }.filter { it.cents != 0L } }
}


/** One payment to record so a pair is square in one group. */
data class Payment(val group: Group, val from: String, val to: String, val cents: Long)

/** The payments that zero out every shared group with [friend]: you pay where you owe, they pay where they owe. */
fun settleAllPlan(me: String, friend: String, lines: List<PairLine>): List<Payment> =
    lines.filter { it.cents != 0L }.map { if (it.cents > 0) Payment(it.group, friend, me, it.cents) else Payment(it.group, me, friend, -it.cents) }

/** What actually changes hands once the groups are netted against each other, per currency. + you receive, - you pay. */
fun settleNet(me: String, plan: List<Payment>): Map<String, Long> =
    plan.groupBy { it.group.currency }.mapValues { (_, ps) -> ps.sumOf { if (it.to == me) it.cents else -it.cents } }

/** Result of [settleAll]: how many payments went through and the first error message, if any. */
data class SettleOutcome(val done: Int, val total: Int, val error: String?)

/**
 * Re-reads the balances, refuses if they no longer match what the user confirmed, then records one payment per group.
 * Safe to run again after a partial failure: it only ever records what is still open.
 */
suspend fun settleAll(me: String, friend: String, confirmed: List<Payment>): SettleOutcome {
    val home = Api.loadHome()
    if (home.offline) return SettleOutcome(0, 0, "You are offline. Connect and try again.")
    val fresh = settleAllPlan(me, friend, pairwise(me, home)[friend].orEmpty())
    if (fresh.map { Triple(it.group.id, it.from, it.cents) } != confirmed.map { Triple(it.group.id, it.from, it.cents) })
        return SettleOutcome(0, fresh.size, "Balances changed. Please review and try again.")
    var done = 0
    var err: String? = null
    for (p in fresh) {
        try { Api.settle(p.group.id, p.from, p.to, p.cents); done++ } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            if (err == null) err = friendly(e)
        }
    }
    return SettleOutcome(done, fresh.size, err)
}
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun FriendsTab(model: HomeModel) {
    val cs = MaterialTheme.colorScheme
    val sem = MaterialTheme.semantic
    val run = rememberRunner()
    val d = model.data
    val me = Api.me ?: ""
    var open by remember { mutableStateOf<String?>(null) }
    var filter by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(1) } // 0 all, 1 outstanding, 2 you owe, 3 owe you
    var menu by remember { mutableStateOf(false) }
    var addPeople by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf(false) }
    val snack = LocalSnack.current
    val runSettle = rememberRunner({ busy = it }, { err = it })
    PullToRefreshBox(isRefreshing = model.loading && d != null, onRefresh = { run { model.refresh() } }, modifier = Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp + LocalNavPad.current)) {
            item {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Friends", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
                    androidx.compose.material3.IconButton({ addPeople = true }) { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Rounded.PersonAdd, contentDescription = "Add people") }
                    Box {
                        androidx.compose.material3.IconButton({ menu = true }) { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Rounded.Tune, contentDescription = "Filter friends") }
                        androidx.compose.material3.DropdownMenu(menu, { menu = false }) {
                            listOf("All friends", "Outstanding balances", "Friends you owe", "Friends who owe you").forEachIndexed { i, label ->
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text(label) }, onClick = { filter = i; menu = false },
                                    leadingIcon = { androidx.compose.material3.RadioButton(filter == i, { filter = i; menu = false }) },
                                )
                            }
                        }
                    }
                }
            }
            if (d == null) { item { Box(Modifier.fillMaxWidth().height(240.dp), Alignment.Center) { FullScreenLoading() } }; return@LazyColumn }
            val pairs = pairwise(me, d)
            val all = d.members.values.flatten().filter { it != me }.distinct()
                .sortedWith(compareByDescending<String> { pairs[it].orEmpty().any { l -> l.cents != 0L } }.thenBy { model.fullName(it).lowercase() })
            if (all.isEmpty()) {
                item { EmptyState("No friends yet", "People you share a group with show up here, with what you owe each other.") }
                return@LazyColumn
            }
            // Overall line, only when every balance is in one currency (otherwise adding them would mislead).
            val every = pairs.values.flatten()
            val curs = every.map { it.group.currency }.distinct()
            val overall = every.sumOf { it.cents }
            if (curs.size == 1 && overall != 0L) item {
                BalanceHero(overall, curs[0], Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 8.dp))
            }
            val open0 = all.filter { pairs[it].orEmpty().isNotEmpty() }
            fun owing(f: String) = pairs[f].orEmpty().sumOf { it.cents }
            val friends = when (filter) { 0 -> all; 1 -> open0; 2 -> all.filter { owing(it) < 0 }; else -> all.filter { owing(it) > 0 } }
            if (friends.isEmpty()) item {
                EmptyState(
                    if (filter == 1) "All settled up" else "No one here",
                    when (filter) { 1 -> "Nobody owes you and you owe nobody."; 2 -> "You don't owe anyone right now."; 3 -> "Nobody owes you right now."; else -> "No friends yet." },
                )
            }
            items(friends, key = { it }) { f ->
                val lines = pairs[f].orEmpty()
                val cur = lines.map { it.group.currency }.distinct()
                val total = lines.sumOf { it.cents }
                Row(
                    Modifier.fillMaxWidth().pressable(role = null) { open = f }.padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(model.fullName(f), f, 46.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(model.fullName(f), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val shared = d.groups.count { f in (d.members[it.id] ?: emptyList()) }
                        Text("$shared shared ${if (shared == 1) "group" else "groups"}", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        when {
                            lines.isEmpty() -> Text("settled up", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                            cur.size > 1 -> Text("mixed currencies", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                            total > 0 -> { Text("owes you", style = MaterialTheme.typography.labelSmall, color = sem.owed); Text(money(total, cur[0]), style = MaterialTheme.typography.titleSmall, color = sem.owed) }
                            total < 0 -> { Text("you owe", style = MaterialTheme.typography.labelSmall, color = sem.owe); Text(money(total, cur[0]), style = MaterialTheme.typography.titleSmall, color = sem.owe) }
                            else -> Text("settled up", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
    if (addPeople) AddPeopleSheet(d?.groups.orEmpty(), null) { addPeople = false }
    open?.let { f ->
        val lines = if (d != null) pairwise(me, d)[f].orEmpty() else emptyList()
        val plan = settleAllPlan(me, f, lines)
        val net = settleNet(me, plan)
        val who = firstName(model.fullName(f))
        fun netText(): String = net.entries.joinToString(" and ") { (cur, c) -> if (c >= 0) "$who pays you ${money(c, cur)}" else "you pay $who ${money(c, cur)}" }
        AppSheet({ if (!busy) { open = null; err = null } }, model.fullName(f)) {
            if (lines.isEmpty()) Text("You two are all settled up.", style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant)
            lines.forEach { l ->
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(l.group.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    Text(
                        (if (l.cents > 0) "owes you " else "you owe ") + money(l.cents, l.group.currency),
                        style = MaterialTheme.typography.titleSmall, color = if (l.cents > 0) sem.owed else sem.owe,
                    )
                }
            }
            if (plan.isNotEmpty()) {
                if (plan.size > 1) Text("Across groups: ${netText()}", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                ErrorText(err)
                // You owe them in rupees: pay now from a UPI app, or copy their number, then record it with Settle all.
                val owe = net.entries.singleOrNull()?.takeIf { it.key == "INR" && it.value < 0 }
                if (owe != null) {
                    val ctx = androidx.compose.ui.platform.LocalContext.current
                    val upi = d?.upis?.get(f); val phone = d?.phones?.get(f)
                    if (upi != null) {
                        Spacer(Modifier.height(16.dp))
                        SecondaryButton("Pay $who with a UPI app", {
                            val uri = android.net.Uri.Builder().scheme("upi").authority("pay")
                                .appendQueryParameter("pa", upi).appendQueryParameter("pn", model.fullName(f))
                                .appendQueryParameter("am", "%.2f".format(-owe.value / 100.0)).appendQueryParameter("cu", "INR")
                                .appendQueryParameter("tn", "Hissa").build()
                            try { ctx.startActivity(Intent(Intent.ACTION_VIEW, uri)) } catch (e: Exception) { err = "No UPI app found on this phone." }
                        })
                    }
                    if (phone != null) {
                        Spacer(Modifier.height(if (upi != null) 8.dp else 16.dp))
                        SecondaryButton("Copy $who's number  $phone", {
                            (ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                                .setPrimaryClip(android.content.ClipData.newPlainText("Mobile number", phone))
                            android.widget.Toast.makeText(ctx, "Copied. Paste it in Pay to number in your UPI app.", android.widget.Toast.LENGTH_LONG).show()
                        })
                    }
                    if (upi != null || phone != null) Text("Paid already? Tap Settle all to record it.", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
                Spacer(Modifier.height(20.dp))
                PrimaryButton("Settle all with $who", { confirm = true }, loading = busy)
            }
        }
        if (confirm && plan.isNotEmpty()) androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Settle up with $who?") },
            text = { Text("This records ${plan.size} ${if (plan.size == 1) "payment" else "payments"}, one in each group, so you and $who are square everywhere. ${netText().replaceFirstChar { it.uppercase() }}.") },
            confirmButton = {
                androidx.compose.material3.TextButton({
                    confirm = false
                    runSettle {
                        val r = settleAll(me, f, plan)
                        model.refresh()
                        if (r.error == null) { open = null; snack.say("All settled up with $who") }
                        else err = if (r.done == 0) r.error else "Settled ${r.done} of ${r.total} groups. ${r.error} Tap Settle all to retry the rest."
                    }
                }) { Text("Settle") }
            },
            dismissButton = { androidx.compose.material3.TextButton({ confirm = false }) { Text("Cancel") } },
        )
    }
}

/** One row per expense or payment, names resolved, ready for a spreadsheet. */
fun groupCsv(g: Group, d: GroupData, model: HomeModel): String {
    fun q(s: String) = "\"" + s.replace("\"", "\"\"") + "\""
    val rows = d.expenses.sortedBy { it.date }.map { e ->
        val who = e.splits.filter { it.shareCents > 0 }.joinToString("; ") { "${model.fullName(it.userId)} ${"%.2f".format(it.shareCents / 100.0)}" }
        listOf(e.date, q(e.description), if (e.isSettlement) "payment" else "expense", "%.2f".format(e.amountCents / 100.0), g.currency, q(model.fullName(e.paidBy)), q(who)).joinToString(",")
    }
    return (listOf("Date,Description,Type,Amount,Currency,Paid by,Split") + rows).joinToString("\n")
}

fun shareCsv(ctx: Context, g: Group, csv: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_SUBJECT, "${g.name} expenses")
        putExtra(Intent.EXTRA_TEXT, csv)
    }
    ctx.startActivity(Intent.createChooser(send, "Export ${g.name}"))
}
