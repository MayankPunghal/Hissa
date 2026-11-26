@file:OptIn(ExperimentalMaterial3Api::class)

package app.hissa

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException


@Composable
fun GroupScreen(g: Group, model: HomeModel, focusExpense: String? = null, onBack: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val sem = MaterialTheme.semantic
    val me = Api.me ?: ""
    val snack = LocalSnack.current
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val run = rememberRunner()
    var gd by remember { mutableStateOf<GroupData?>(null) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Expense?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var range by rememberSaveable { mutableIntStateOf(0) }
    var settings by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<Expense?>(null) }
    var settle by remember { mutableStateOf<Transfer?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    // Arriving from the Activity tab: open that expense once its group has loaded.
    LaunchedEffect(gd, focusExpense) {
        val list = gd?.expenses
        if (focusExpense != null && !focused && list != null) { focused = true; detail = list.firstOrNull { it.id == focusExpense } }
    }

    suspend fun reload() { gd = Api.loadGroup(g.id) }
    LaunchedEffect(g.id, model.tick) {
        try { reload() } catch (e: CancellationException) { throw e } catch (e: Exception) { if (gd == null) snack.say(friendly(e)) }
    }
    // Repeating expenses that came due are created the first time anyone opens the group.
    LaunchedEffect(g.id) { if (Api.runRecurring(g.id) > 0) runCatching { reload() } }
    BackHandler(adding) { adding = false; editing = null }
    BackHandler(searching && !adding) { searching = false; query = "" }
    fun n(id: String) = model.name(id)

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = cs.background,
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = { adding = true }, containerColor = cs.primary, contentColor = cs.onPrimary, shape = CircleShape,
                    elevation = FloatingActionButtonDefaults.elevation(2.dp, 2.dp, 2.dp, 2.dp),
                ) {
                    Icon(Icons.Rounded.Add, null)
                    Spacer(Modifier.width(8.dp)); Text("Add expense", style = MaterialTheme.typography.titleSmall)
                }
            },
        ) { pad ->
            PullToRefreshBox(
                isRefreshing = refreshing, modifier = Modifier.padding(pad).fillMaxSize(),
                onRefresh = { run { refreshing = true; try { reload(); model.refresh(live = true) } finally { refreshing = false } } },
            ) {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 112.dp)) {
                    item {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
                            Spacer(Modifier.weight(1f))
                            if (tab == 0 && (gd?.expenses?.size ?: 0) > 3) IconButton({ searching = !searching; if (!searching) query = "" }) {
                                Icon(if (searching) Icons.Rounded.Close else Icons.Rounded.Search, contentDescription = if (searching) "Close search" else "Search expenses")
                            }
                            IconButton({ shareInvite(ctx, g) }) { Icon(Icons.Rounded.Share, contentDescription = "Share invite link") }
                            IconButton({ settings = true }) { Icon(Icons.Rounded.Settings, contentDescription = "Group settings") }
                        }
                    }
                    val d = gd
                    if (d == null) { item { Box(Modifier.fillMaxWidth().height(240.dp), Alignment.Center) { FullScreenLoading() } }; return@LazyColumn }

                    val net = d.net[me] ?: 0L
                    val spent = d.expenses.filter { !it.isSettlement }
                    item {
                        Column(Modifier.padding(horizontal = 20.dp)) {
                            Row(Modifier.pressable(role = null) { settings = true }, verticalAlignment = Alignment.CenterVertically) {
                                GroupTile(g, 52.dp)
                                Spacer(Modifier.width(14.dp))
                                Text(g.name, style = MaterialTheme.typography.headlineLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            Spacer(Modifier.height(12.dp))
                            // One line of truth about your balance, then the facts as quiet text.
                            if (spent.isEmpty()) Text("No expenses yet", style = MaterialTheme.typography.titleLarge) else BalanceHero(net, g.currency, compact = true)
                            Spacer(Modifier.height(10.dp))
                            Text(
                                (if (spent.isEmpty()) "" else "${money(spent.sumOf { it.amountCents }, g.currency)} spent  ·  ") +
                                    "${d.members.size} ${if (d.members.size == 1) "person" else "people"}",
                                style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
                            )
                            // The share icon above already invites people; the pill only appears when the group is empty of friends.
                            if (d.members.size < 2) {
                                Spacer(Modifier.height(12.dp))
                                Row(
                                    Modifier.clip(CircleShape).background(cs.surfaceContainer).pressable { shareInvite(ctx, g) }
                                        .padding(start = 14.dp, end = 18.dp, top = 9.dp, bottom = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.Rounded.PersonAdd, contentDescription = null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Invite friends", style = MaterialTheme.typography.labelLarge)
                                }
                            }
                            Spacer(Modifier.height(20.dp))
                            Segmented(listOf("Expenses", "Balances", "Insights"), tab, { tab = it })
                        }
                    }

                    if (tab == 0) {
                        if (searching) item(key = "search") {
                            val fr = remember { FocusRequester() }
                            LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
                            HissaField(
                                query, { query = it }, "Search expenses", Modifier.padding(horizontal = 20.dp, vertical = 8.dp).focusRequester(fr),
                                leading = { Icon(Icons.Rounded.Search, null) },
                            )
                        }
                        if (d.expenses.isEmpty()) item {
                            EmptyState(
                                "No expenses yet",
                                if (d.members.size < 2) "Invite friends with the button above, then add the first expense."
                                else "Add the first one and Hissa will keep the score.",
                            )
                        } else {
                            val q = query.trim().lowercase()
                            val shown = if (q.isEmpty()) d.expenses else d.expenses.filter { e ->
                                e.description.lowercase().contains(q) || (e.notes ?: "").lowercase().contains(q) || Cat.of(e.category).label.lowercase().contains(q) ||
                                    e.payerNames(::n).lowercase().contains(q) || "%.2f".format(e.amountCents / 100.0).contains(q)
                            }
                            if (shown.isEmpty()) item { EmptyState("No matches", "Nothing found for \"${query.trim()}\".") }
                            shown.groupBy { dayLabel(it.date) }.forEach { (day, list) ->
                                item(key = "h$day") { SectionTitle(day) }
                                items(list, key = { it.id }) { e ->
                                    val mine = e.netFor(me)
                                    Row(
                                        Modifier.animateItem().enterIn(list.indexOf(e)).fillMaxWidth().pressable(role = null) { detail = e }.padding(horizontal = 20.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Box(Modifier.size(44.dp).clip(CircleShape).background(if (e.isSettlement) cs.primaryContainer else cs.surfaceContainer), Alignment.Center) {
                                            Icon(
                                                if (e.isSettlement) Icons.Rounded.Check else Cat.of(e.category).icon, null,
                                                tint = if (e.isSettlement) cs.onPrimaryContainer else cs.onSurface, modifier = Modifier.size(22.dp),
                                            )
                                        }
                                        Spacer(Modifier.width(14.dp))
                                        Column(Modifier.weight(1f)) {
                                            val to = e.splits.firstOrNull { it.shareCents > 0 && it.userId != e.paidBy }?.userId
                                            Text(
                                                if (e.isSettlement) "${n(e.paidBy)} paid ${n(to ?: "")}" else e.description,
                                                style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                if (e.isSettlement) money(e.amountCents, g.currency) else "${e.payerNames(::n)} paid ${money(e.amountCents, g.currency)}",
                                                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                                            )
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            when {
                                                e.isSettlement && mine > 0 -> Text("you paid", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                                                e.isSettlement && mine < 0 -> Text("you received", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                                                mine > 0 -> { Text("you're owed", style = MaterialTheme.typography.labelSmall, color = sem.owed); Text(money(mine, g.currency), style = MaterialTheme.typography.titleSmall, color = sem.owed) }
                                                mine < 0 -> { Text("you owe", style = MaterialTheme.typography.labelSmall, color = sem.owe); Text(money(mine, g.currency), style = MaterialTheme.typography.titleSmall, color = sem.owe) }
                                                else -> Text("not involved", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else if (tab == 1) {
                        // The actionable part comes first: what to pay (yours on top), then who is owed what.
                        val plan = (if (Api.simplifyDebts(g.id)) simplify(d.net) else rawTransfers(d.expenses)).sortedByDescending { it.from == me }
                        item { SectionTitle("Settle up") }
                        if (plan.isEmpty()) item { Text("Hisaab barabar! Everyone is even.", style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) }
                        items(plan, key = { "t${it.from}${it.to}" }) { t ->
                            Row(Modifier.animateItem().enterIn(plan.indexOf(t)).fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("${n(t.from)} ${if (t.from == me) "pay" else "pays"} ${n(t.to)}", style = MaterialTheme.typography.titleMedium)
                                    Text(money(t.cents, g.currency), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                                }
                                if (t.from == me) Box(
                                    Modifier.clip(CircleShape).background(cs.primary).pressable { settle = t }.padding(horizontal = 20.dp, vertical = 10.dp),
                                ) { Text("Settle", style = MaterialTheme.typography.labelLarge, color = cs.onPrimary) }
                            }
                        }
                        item { SectionTitle("Who's owed what") }
                        items(d.members, key = { "m$it" }) { m ->
                            val v = d.net[m] ?: 0L
                            Row(Modifier.animateItem().enterIn(d.members.indexOf(m)).fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Avatar(model.fullName(m), m, 44.dp)
                                Spacer(Modifier.width(14.dp))
                                Text(n(m), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                when {
                                    v > 0 -> Text("${if (m == me) "get" else "gets"} back ${money(v, g.currency)}", style = MaterialTheme.typography.titleSmall, color = sem.owed)
                                    v < 0 -> Text("${if (m == me) "owe" else "owes"} ${money(v, g.currency)}", style = MaterialTheme.typography.titleSmall, color = sem.owe)
                                    else -> Text("settled", style = MaterialTheme.typography.titleSmall, color = cs.onSurfaceVariant)
                                }
                            }
                        }
                    } else {
                        insightItems(g, d, model, me, range, { range = it }) { shareCsv(ctx, g, groupCsv(g, d, model)) }
                    }
                }
            }
        }

        AnimatedVisibility(
            settings, Modifier.fillMaxSize(),
            enter = slideInVertically(tween(300)) { it / 6 } + fadeIn(tween(220)),
            exit = slideOutVertically(tween(240)) { it / 6 } + fadeOut(tween(180)),
        ) {
            GroupSettings(g, gd?.members ?: emptyList(), gd?.net ?: emptyMap(), model, { settings = false }) { settings = false; onBack() }
        }

        AnimatedVisibility(
            adding, Modifier.fillMaxSize(),
            enter = slideInVertically(tween(300)) { it / 6 } + fadeIn(tween(220)),
            exit = slideOutVertically(tween(240)) { it / 6 } + fadeOut(tween(180)),
        ) {
            val d = gd
            // Keyed so each add / edit starts from a clean form.
            androidx.compose.runtime.key(editing?.id ?: "new", adding) {
                if (adding) AddExpenseScreen(g, d?.members ?: emptyList(), model, editing, { adding = false; editing = null }) { x, every ->
                    val e = editing
                    if (e != null) Api.editExpense(e.id, x)
                    else {
                        Api.addExpense(g.id, x)
                        if (every != null) Api.addRecurring(g.id, x, every, if (every == "week") x.date.plusDays(7) else x.date.plusMonths(1))
                    }
                    adding = false; editing = null
                    reload()
                    snack.say(if (e != null) "Expense updated" else if (every != null) "Expense added, repeating every $every" else "Expense added")
                }
            }
        }
    }

    detail?.let { e ->
        var sure by remember(e.id) { mutableStateOf(false) }
        var err by remember(e.id) { mutableStateOf<String?>(null) }
        val del = rememberRunner(onError = { err = it })
        AppSheet({ detail = null }, if (e.isSettlement) "Payment" else e.description) {
            Text(money(e.amountCents, g.currency), style = MaterialTheme.typography.displaySmall)
            Text(
                (if (e.isSettlement) "" else Cat.of(e.category).label + "  ·  ") + dayLabel(e.date),
                style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
            )
            e.notes?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp)) }
            Spacer(Modifier.height(16.dp))
            if (!e.isSettlement) {
                Text("Paid by", style = MaterialTheme.typography.titleSmall, color = cs.onSurfaceVariant)
                e.payers.sortedByDescending { it.paidCents }.forEach { p ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(model.fullName(p.userId), p.userId, 36.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(n(p.userId), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        Text(money(p.paidCents, g.currency), style = MaterialTheme.typography.titleSmall)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Split between", style = MaterialTheme.typography.titleSmall, color = cs.onSurfaceVariant)
            } else Text("${n(e.paidBy)} paid", style = MaterialTheme.typography.titleSmall, color = cs.onSurfaceVariant)
            e.splits.filter { it.shareCents > 0 }.forEach { s ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(model.fullName(s.userId), s.userId, 36.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(if (e.isSettlement) "${n(s.userId)} received" else "${n(s.userId)} ${if (s.userId == me) "owe" else "owes"}", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    // (for a settlement the single share row is the receiver)
                    Text(money(s.shareCents, g.currency), style = MaterialTheme.typography.titleSmall)
                }
            }
            ErrorText(err)
            Spacer(Modifier.height(20.dp))
            if (!e.isSettlement) {
                SecondaryButton("Edit", { detail = null; editing = e; adding = true })
                Spacer(Modifier.height(8.dp))
            }
            SecondaryButton(if (sure) "Tap again to delete" else "Delete", danger = true, onClick = {
                if (!sure) sure = true
                else del { Api.deleteExpense(e.id); detail = null; reload(); snack.say("Deleted") }
            })
        }
    }

    settle?.let { t ->
        var busy by remember(t) { mutableStateOf(false) }
        var err by remember(t) { mutableStateOf<String?>(null) }
        val go = rememberRunner({ busy = it }, { err = it })
        AppSheet({ settle = null }, "Settle up") {
            Text(
                "Record that you paid ${n(t.to)} ${money(t.cents, g.currency)}? This adds a payment to the group.",
                style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant,
            )
            ErrorText(err)
            val upi = model.data?.upis?.get(t.to)
            if (upi != null && g.currency == "INR") {
                Spacer(Modifier.height(16.dp))
                SecondaryButton("Pay ${firstName(model.fullName(t.to))} with a UPI app", {
                    val uri = android.net.Uri.Builder().scheme("upi").authority("pay")
                        .appendQueryParameter("pa", upi).appendQueryParameter("pn", model.fullName(t.to))
                        .appendQueryParameter("am", "%.2f".format(t.cents / 100.0)).appendQueryParameter("cu", "INR")
                        .appendQueryParameter("tn", "Hissa: ${g.name}").build()
                    try { ctx.startActivity(Intent(Intent.ACTION_VIEW, uri)) } catch (e: Exception) { err = "No UPI app found on this phone." }
                })
            }
            val phone = model.data?.phones?.get(t.to)
            if (phone != null && g.currency == "INR") {
                Spacer(Modifier.height(if (upi != null) 8.dp else 16.dp))
                SecondaryButton("Copy ${firstName(model.fullName(t.to))}'s number  $phone", {
                    (ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                        .setPrimaryClip(android.content.ClipData.newPlainText("Mobile number", phone))
                    android.widget.Toast.makeText(ctx, "Copied. Paste it in Pay to number in your UPI app.", android.widget.Toast.LENGTH_LONG).show()
                })
            }
            Spacer(Modifier.height(24.dp))
            PrimaryButton("Record payment", loading = busy, onClick = {
                go {
                    Api.settle(g.id, t.from, t.to, t.cents)
                    settle = null; reload(); snack.say("Payment recorded")
                }
            })
            Spacer(Modifier.height(8.dp))
            SecondaryButton("Cancel", { settle = null })
        }
    }
}
