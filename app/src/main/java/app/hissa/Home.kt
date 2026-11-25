@file:OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)

package app.hissa

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.GroupAdd
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.withContext

/** Home data plus a live-change counter other screens can key on. */
class HomeModel {
    var data by mutableStateOf<HomeData?>(null); private set
    var loading by mutableStateOf(false); private set
    var failed by mutableStateOf<String?>(null); private set
    var tick by mutableIntStateOf(0); private set

    suspend fun refresh(live: Boolean = false) {
        if (!live) loading = true
        try { data = Api.loadHome(); failed = null; if (live) tick++ } catch (e: CancellationException) { throw e } catch (e: Exception) {
            if (data == null) failed = friendly(e)
        } finally { loading = false }
    }

    fun name(id: String) = if (id == Api.me) "You" else data?.names?.get(id) ?: "Friend"
    fun fullName(id: String) = data?.names?.get(id) ?: "Friend"
}

@Composable
fun Home(openServer: () -> Unit) {
    val model = remember { HomeModel() }
    var openGroup by remember { mutableStateOf<Group?>(null) }
    var tab by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        model.refresh()
        if (Api.demo) return@LaunchedEffect
        val (ch, changes) = Api.liveChannel("home")
        try {
            ch.subscribe(blockUntilSubscribed = true)
            changes.debounce(400).collect { model.refresh(live = true) }
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            // Live updates are a bonus; pull-to-refresh still works.
        } finally { withContext(NonCancellable) { runCatching { Api.closeChannel(ch) } } }
    }

    BackHandler(openGroup != null) { openGroup = null }
    AnimatedContent(
        openGroup,
        transitionSpec = {
            if (targetState != null) (slideInHorizontally(tween(280)) { it / 5 } + fadeIn(tween(280))) togetherWith fadeOut(tween(140))
            else fadeIn(tween(220)) togetherWith (slideOutHorizontally(tween(240)) { it / 5 } + fadeOut(tween(200)))
        },
        label = "screen",
    ) { g ->
        if (g == null) Tabs(model, tab, { tab = it }, { openGroup = it }, openServer)
        else GroupScreen(model.data?.groups?.firstOrNull { it.id == g.id } ?: g, model) { openGroup = null }
    }
}

@Composable
private fun Tabs(model: HomeModel, tab: Int, setTab: (Int) -> Unit, onOpen: (Group) -> Unit, openServer: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    var sheet by remember { mutableStateOf("") } // "", "new", "join"
    // A tapped invite link opens the join sheet with its code filled in.
    LaunchedEffect(Api.pendingInvite) { if (Api.pendingInvite != null) sheet = "join" }
    Scaffold(
        containerColor = cs.background,
        bottomBar = {
            Column {
                Divider()
                NavigationBar(containerColor = cs.background, tonalElevation = 0.dp) {
                    listOf(
                        Triple("Groups", Icons.Rounded.Groups, 0),
                        Triple("Friends", Icons.Rounded.Person, 1),
                        Triple("Activity", Icons.AutoMirrored.Rounded.ReceiptLong, 2),
                        Triple("Account", Icons.Rounded.AccountCircle, 3),
                    ).forEach { (label, icon, i) ->
                        NavigationBarItem(
                            selected = tab == i, onClick = { setTab(i) },
                            icon = { Icon(icon, contentDescription = null) },
                            label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = cs.primaryContainer, selectedIconColor = cs.onPrimaryContainer,
                                selectedTextColor = cs.onSurface, unselectedIconColor = cs.onSurfaceVariant, unselectedTextColor = cs.onSurfaceVariant,
                            ),
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (tab == 0) ExtendedFloatingActionButton(
                onClick = { sheet = "new" }, containerColor = cs.primary, contentColor = cs.onPrimary,
                shape = CircleShape, elevation = androidx.compose.material3.FloatingActionButtonDefaults.elevation(2.dp, 2.dp, 2.dp, 2.dp),
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("New group", style = MaterialTheme.typography.titleSmall)
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            Crossfade(tab, animationSpec = tween(180), label = "tab") { t ->
                when (t) {
                    0 -> GroupsTab(model, onOpen) { sheet = "join" }
                    1 -> FriendsTab(model)
                    2 -> ActivityTab(model)
                    else -> AccountTab(model, openServer)
                }
            }
        }
    }
    when (sheet) {
        "new" -> NewGroupSheet({ sheet = "" }, model)
        "join" -> JoinSheet({ sheet = ""; Api.pendingInvite = null }, model, Api.pendingInvite.orEmpty())
    }
}

// ───────────────────────────── Groups ─────────────────────────────

@Composable
private fun GroupsTab(model: HomeModel, onOpen: (Group) -> Unit, onJoin: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val run = rememberRunner()
    val d = model.data
    val me = Api.me
    PullToRefreshBox(isRefreshing = model.loading && d != null, onRefresh = { run { model.refresh() } }, modifier = Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 112.dp)) {
            item {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        val who = d?.names?.get(me) ?: ""
                        if (who.isNotBlank()) Text("Hi, ${firstName(who)}", style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant)
                        Text("Groups", style = MaterialTheme.typography.headlineLarge)
                    }
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).background(cs.surfaceContainer).pressable(onClick = onJoin),
                        Alignment.Center,
                    ) { Icon(Icons.Rounded.GroupAdd, contentDescription = "Join with a code", Modifier.size(22.dp)) }
                }
            }
            when {
                d == null && model.failed != null -> item {
                    EmptyState("Couldn't load your groups", model.failed ?: "", action = { SecondaryButton("Try again", { run { model.refresh() } }, Modifier.width(180.dp)) })
                }
                d == null -> item { Box(Modifier.fillMaxWidth().height(240.dp), Alignment.Center) { FullScreenLoading() } }
                else -> {
                    if (d.offline) item { OfflineNote() }
                    item { Summary(d) }
                    if (d.groups.isEmpty()) item {
                        EmptyState(
                            "No groups yet",
                            "Start one for your next trip, flat or dinner, or join a friend's with their invite code.",
                        )
                    }
                    items(d.groups, key = { it.id }) { g ->
                        GroupCard(g, d, Modifier.animateItem()) { onOpen(g) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Summary(d: HomeData) {
    val sem = MaterialTheme.semantic
    val cs = MaterialTheme.colorScheme
    val cur = d.groups.firstOrNull()?.currency ?: "INR"
    val net = d.groups.filter { it.currency == cur }.sumOf { d.myNet[it.id] ?: 0L }
    Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
        Row {
            Text(
                when { d.groups.isEmpty() -> "No balances yet"; net > 0 -> "Overall, you're owed "; net < 0 -> "Overall, you owe "; else -> "You're all settled up" },
                style = MaterialTheme.typography.titleLarge,
            )
            if (net != 0L) Text(money(net, cur), style = MaterialTheme.typography.titleLarge, color = if (net > 0) sem.owed else sem.owe)
        }
        if (d.groups.any { it.currency != cur }) {
            InfoTip("Groups in other currencies are not included.")
        }
    }
}

@Composable
private fun GroupCard(g: Group, d: HomeData, modifier: Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val sem = MaterialTheme.semantic
    val net = d.myNet[g.id] ?: 0L
    val count = d.members[g.id]?.size ?: 0
    Row(
        modifier.fillMaxWidth().pressable(role = null, onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GroupTile(g, 56.dp)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(g.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("$count ${if (count == 1) "person" else "people"}", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            when {
                net > 0 -> { Text("you're owed", style = MaterialTheme.typography.labelSmall, color = sem.owed); Text(money(net, g.currency), style = MaterialTheme.typography.titleMedium, color = sem.owed) }
                net < 0 -> { Text("you owe", style = MaterialTheme.typography.labelSmall, color = sem.owe); Text(money(net, g.currency), style = MaterialTheme.typography.titleMedium, color = sem.owe) }
                else -> Text("settled up", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
            }
        }
    }
}

// ───────────────────────────── Activity ─────────────────────────────

@Composable
private fun ActivityTab(model: HomeModel) {
    val cs = MaterialTheme.colorScheme
    val sem = MaterialTheme.semantic
    val run = rememberRunner()
    val d = model.data
    val me = Api.me ?: ""
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    val q = query.trim().lowercase()
    val shown = if (q.isEmpty()) d?.recent.orEmpty() else d?.recent.orEmpty().filter { e ->
        e.description.lowercase().contains(q) || Cat.of(e.category).label.lowercase().contains(q) || e.payerNames(model::name).lowercase().contains(q) ||
            (d?.groups?.firstOrNull { it.id == e.groupId }?.name ?: "").lowercase().contains(q)
    }
    PullToRefreshBox(isRefreshing = model.loading && d != null, onRefresh = { run { model.refresh() } }, modifier = Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Activity", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
                    if ((d?.recent?.size ?: 0) > 3) IconButton({ searching = !searching; if (!searching) query = "" }) {
                        Icon(if (searching) Icons.Rounded.Close else Icons.Rounded.Search, contentDescription = if (searching) "Close search" else "Search activity")
                    }
                }
                if (searching) { val fr = remember { androidx.compose.ui.focus.FocusRequester() }; androidx.compose.runtime.LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }; HissaField(query, { query = it }, "Search activity", Modifier.padding(horizontal = 20.dp, vertical = 4.dp).focusRequester(fr), leading = { Icon(Icons.Rounded.Search, null) }) }
                if (d?.offline == true) OfflineNote()
            }
            if (d == null) item { Box(Modifier.fillMaxWidth().height(240.dp), Alignment.Center) { FullScreenLoading() } }
            else if (d.recent.isEmpty()) item { EmptyState("Nothing yet", "Expenses and payments from all your groups will show up here.") }
            else if (shown.isEmpty()) item { EmptyState("No matches", "Nothing found for \"${query.trim()}\".") }
            else {
                val days = shown.groupBy { dayLabel(it.date) }
                days.forEach { (day, list) ->
                    item(key = "h$day") { SectionTitle(day) }
                    items(list, key = { it.id }) { e ->
                        val group = d.groups.firstOrNull { it.id == e.groupId }
                        val cur = group?.currency ?: "INR"
                        val net = e.netFor(me)
                        Row(Modifier.animateItem().fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (e.isSettlement) Box(Modifier.size(44.dp).clip(CircleShape).background(cs.primaryContainer), Alignment.Center) {
                                Icon(Icons.Rounded.Check, null, tint = cs.onPrimaryContainer)
                            } else Box(Modifier.size(44.dp).clip(CircleShape).background(cs.surfaceContainer), Alignment.Center) { Icon(Cat.of(e.category).icon, null, Modifier.size(22.dp)) }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                val to = e.splits.firstOrNull { it.shareCents > 0 && it.userId != e.paidBy }?.userId
                                Text(
                                    if (e.isSettlement) "${model.name(e.paidBy)} paid ${model.name(to ?: "")}" else e.description,
                                    style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    if (e.isSettlement) "in ${group?.name ?: "a group"}, ${agoLabel(e.createdAt)}"
                                    else "${e.payerNames(model::name)} paid, in ${group?.name ?: "a group"}",
                                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Column(horizontalAlignment = Alignment.End) {
                                Text(money(e.amountCents, cur), style = MaterialTheme.typography.titleMedium)
                                when {
                                    e.isSettlement && net > 0 -> Text("you paid", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                                    e.isSettlement && net < 0 -> Text("you received", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                                    net > 0 -> Text("you're owed ${money(net, cur)}", style = MaterialTheme.typography.labelSmall, color = sem.owed)
                                    net < 0 -> Text("you owe ${money(net, cur)}", style = MaterialTheme.typography.labelSmall, color = sem.owe)
                                    else -> Text("not involved", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ───────────────────────────── Account ─────────────────────────────

@Composable
private fun SettingsRow(icon: ImageVector, title: String, subtitle: String? = null, danger: Boolean = false, onClick: (() -> Unit)? = null) {
    val cs = MaterialTheme.colorScheme
    val tint = if (danger) cs.error else cs.onSurface
    Row(
        Modifier.fillMaxWidth().let { if (onClick != null) it.pressable(role = null, onClick = onClick) else it }.padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(if (danger) cs.errorContainer else cs.surfaceContainer), Alignment.Center) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = tint)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun AccountTab(model: HomeModel, openServer: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val run = rememberRunner()
    var password by remember { mutableStateOf(false) }
    var appearance by remember { mutableStateOf(false) }
    var upiSheet by remember { mutableStateOf(false) }
    var upi by remember { mutableStateOf("") }
    androidx.compose.runtime.LaunchedEffect(Unit) { upi = runCatching { Api.myUpi() }.getOrDefault("") }
    val me = Api.me ?: ""
    val name = model.data?.names?.get(me) ?: "You"
    val email = Api.email
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Column(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Avatar(name, me, 92.dp)
                Spacer(Modifier.height(16.dp))
                Text(name, style = MaterialTheme.typography.headlineMedium)
                Text(email, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            }
            Divider()
            Text("Preferences", style = MaterialTheme.typography.titleSmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp))
            SettingsRow(Icons.Rounded.Palette, "Appearance", listOf("Match phone", "Light", "Dark")[Api.themeMode]) { appearance = true }
            SettingsRow(Icons.Rounded.Payments, "UPI ID", upi.ifEmpty { "Add yours so friends can pay you" }) { upiSheet = true }
            SettingsRow(Icons.Rounded.Lock, "Change password") { password = true }
            Text("About", style = MaterialTheme.typography.titleSmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp))
            if (BuildConfig.SUPABASE_URL.isBlank()) SettingsRow(Icons.Rounded.Cloud, "Server", Api.serverUrl.removePrefix("https://"), onClick = openServer)
            SettingsRow(Icons.Rounded.Info, "Hissa ${BuildConfig.VERSION_NAME}", "Free and open source")
            Divider()
            SettingsRow(Icons.AutoMirrored.Rounded.Logout, "Sign out", danger = true) { run { Api.signOut() } }
        }
    }
    if (password) PasswordSheet { password = false }
    if (upiSheet) UpiSheet({ upiSheet = false }, upi) { upi = it }
    if (appearance) AppSheet({ appearance = false }, "Appearance") {
        Segmented(listOf("Match phone", "Light", "Dark"), Api.themeMode, { Api.setTheme(it) })
        Spacer(Modifier.height(8.dp))
    }
}

// ───────────────────────────── Sheets ─────────────────────────────

@Composable
fun AppSheet(onDismiss: () -> Unit, title: String, content: @Composable () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(Modifier.navigationBarsPadding().imePadding().padding(horizontal = 24.dp).padding(bottom = 20.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
private fun NewGroupSheet(onDismiss: () -> Unit, model: HomeModel) {
    var name by remember { mutableStateOf("") }
    var cur by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    val codes = listOf("INR", "USD", "EUR", "GBP")
    var err by remember { mutableStateOf<String?>(null) }
    val run = rememberRunner({ busy = it }, { err = it })
    AppSheet(onDismiss, "New group") {
        HissaField(name, { name = it.take(80); err = null }, "Group name, like Goa trip", error = Check.groupName(name))
        Spacer(Modifier.height(16.dp))
        Text("Currency", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Segmented(codes.map { "${symbol(it).trim()} $it" }, cur, { cur = it })
        ErrorText(err)
        Spacer(Modifier.height(24.dp))
        PrimaryButton("Create group", enabled = name.trim().length >= 2, loading = busy, onClick = {
            run { Api.createGroup(name.trim(), codes[cur]); model.refresh(); onDismiss() }
        })
    }
}

@Composable
private fun JoinSheet(onDismiss: () -> Unit, model: HomeModel, initial: String = "") {
    var code by remember { mutableStateOf(initial) }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    val run = rememberRunner({ busy = it }, { err = it })
    AppSheet(onDismiss, "Join with a code") {
        if (initial.isNotEmpty()) Text("You were invited to a group. Join it?", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        else Row(verticalAlignment = Alignment.CenterVertically) { Text("Invite code", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant); InfoTip("Paste the code or the whole invite message. You can also just open a friend's invite link.") }
        Spacer(Modifier.height(16.dp))
        val clip = androidx.compose.ui.platform.LocalClipboardManager.current
        // Pasting a whole invite message (link, "Code: ..." text) is fine: the code is picked out of it.
        HissaField(code, { code = Api.extractCode(it); err = null }, "Invite code", centered = true)
        ErrorText(err)
        Spacer(Modifier.height(12.dp))
        SecondaryButton("Paste from clipboard", { clip.getText()?.text?.let { code = Api.extractCode(it); err = null } })
        Spacer(Modifier.height(12.dp))
        PrimaryButton("Join group", enabled = code.length >= 6, loading = busy, onClick = {
            run { Api.joinGroup(code); model.refresh(); onDismiss() }
        })
    }
}

@Composable
private fun PasswordSheet(onDismiss: () -> Unit) {
    var p1 by remember { mutableStateOf("") }
    var p2 by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val snack = LocalSnack.current
    var err by remember { mutableStateOf<String?>(null) }
    val run = rememberRunner({ busy = it }, { err = it })
    AppSheet(onDismiss, "Change password") {
        HissaField(p1, { p1 = it.take(72) }, "New password (8+)", password = true, error = Check.password(p1))
        Spacer(Modifier.height(12.dp))
        HissaField(p2, { p2 = it }, "Repeat new password", password = true)
        if (p2.isNotEmpty() && p1 != p2) Text("Passwords don't match.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp), textAlign = TextAlign.Start)
        ErrorText(err)
        Spacer(Modifier.height(24.dp))
        PrimaryButton("Update password", enabled = p1.isNotEmpty() && Check.password(p1) == null && p1 == p2, loading = busy, onClick = {
            run { Api.changePassword(p1); onDismiss(); snack.say("Password updated") }
        })
    }
}

@Composable
fun OfflineNote() {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp).clip(RoundedCornerShape(14.dp)).background(cs.surfaceContainer).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.CloudOff, null, Modifier.size(20.dp), tint = cs.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Text("Offline. Showing saved data.", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, modifier = Modifier.weight(1f)); InfoTip("Changes need a connection. What you see was saved on this phone.")
    }
}

@Composable
private fun UpiSheet(onDismiss: () -> Unit, current: String, onSaved: (String) -> Unit) {
    var v by remember { mutableStateOf(current) }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    val run = rememberRunner({ busy = it }, { err = it })
    AppSheet(onDismiss, "UPI ID") {
        Row(verticalAlignment = Alignment.CenterVertically) { Text("Your UPI ID", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant); InfoTip("Friends who owe you can pay from a UPI app when they settle up. Leave empty to remove it.") }
        Spacer(Modifier.height(16.dp))
        HissaField(v, { v = it.trim().take(60); err = null }, "UPI ID (for example name@bank)")
        ErrorText(err)
        Spacer(Modifier.height(24.dp))
        val ok = v.isEmpty() || Regex("""^[\w.\-]{2,}@[A-Za-z][\w.\-]{1,}$""").matches(v)
        PrimaryButton("Save", enabled = ok, loading = busy, onClick = { run { Api.setUpi(v); onSaved(v); onDismiss() } })
    }
}
