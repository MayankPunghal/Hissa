@file:OptIn(ExperimentalMaterial3Api::class)

package app.hissa

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.Image
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

fun cleanAmount(s: String): String {
    val t = s.filter { it.isDigit() || it == '.' }
    val i = t.indexOf('.')
    return if (i < 0) t.take(9) else t.take(i).take(9) + "." + t.drop(i + 1).filter(Char::isDigit).take(2)
}

/** Who is in and what was typed for them, for either "who paid" or "who owes". */
class SplitState(chosen: Set<String>) {
    var mode by mutableStateOf(Mode.EQUAL)
    var chosen by mutableStateOf(chosen)
    var vals by mutableStateOf<Map<String, String>>(emptyMap())
    fun picked(members: List<String>) = members.filter { it in chosen }
    fun result(total: Long, members: List<String>) = buildSplit(mode, total, picked(members), vals)

    /** Stores what was typed for [id] and, for exact amounts and percentages, fills the last other person with whatever is left. */
    fun edit(id: String, text: String, members: List<String>, total: Long?) {
        vals = vals + (id to text)
        val whole = when (mode) { Mode.EXACT -> total ?: return; Mode.PERCENT -> 10000L; else -> return }
        val ps = picked(members)
        if (ps.size < 2 || id !in ps) return
        val target = (ps - id).last()
        val fixed = ps - target
        if (fixed.any { vals[it].isNullOrBlank() }) return
        val rest = (whole - fixed.sumOf { parseCents(vals[it].orEmpty()) ?: 0L }).coerceAtLeast(0L)
        vals = vals + (target to if (rest % 100 == 0L) (rest / 100).toString() else "%d.%02d".format(rest / 100, rest % 100))
    }
}

private class ItemRow(val name: String = "", val price: String = "", val who: Set<String> = emptySet())

@Composable
private fun AmountBox(value: String, onChange: (String) -> Unit, hint: String) {
    val cs = MaterialTheme.colorScheme
    BasicTextField(
        value = value, onValueChange = { onChange(cleanAmount(it)) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        textStyle = MaterialTheme.typography.titleSmall.copy(color = cs.onSurface, textAlign = TextAlign.End),
        cursorBrush = SolidColor(cs.primary),
        decorationBox = { inner ->
            Box(Modifier.width(96.dp).height(44.dp).clip(RoundedCornerShape(14.dp)).background(cs.surfaceContainer).padding(horizontal = 14.dp), Alignment.CenterEnd) {
                if (value.isEmpty()) Text(hint, style = MaterialTheme.typography.titleSmall, color = cs.outline)
                inner()
            }
        },
    )
}

@Composable
private fun PeopleRows(
    members: List<String>, model: HomeModel, st: SplitState, total: Long?, cur: String,
    modes: List<Mode> = Mode.entries,
) {
    Segmented(modes.map { it.label }, modes.indexOf(st.mode).coerceAtLeast(0), { st.mode = modes[it] })
    Spacer(Modifier.height(8.dp))
    PeopleRowsBody(members, model, st, total, cur)
}

/** What the "Items" mode needs: one row per receipt line with who shared it. */
@Composable
private fun ItemsEditor(members: List<String>, model: HomeModel, items: MutableList<ItemRow>, cur: String) {
    val cs = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Items", style = MaterialTheme.typography.titleSmall, color = cs.onSurfaceVariant)
        InfoTip("Add each item and tick who shared it. Tax, tip and fees are shared in proportion.")
    }
    Spacer(Modifier.height(8.dp))
    items.forEachIndexed { i, it ->
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp).clip(RoundedCornerShape(16.dp)).background(cs.surfaceContainerLow).padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { HissaField(it.name, { v -> items[i] = ItemRow(v.take(60), it.price, it.who) }, "Item") }
                Spacer(Modifier.width(8.dp))
                AmountBox(it.price, { v -> items[i] = ItemRow(it.name, v, it.who) }, "0.00")
                IconButton({ items.removeAt(i) }) { Icon(Icons.Rounded.Close, contentDescription = "Remove item") }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                members.forEach { id ->
                    SelectableAvatar(model.fullName(id), id, id in it.who, if (id == Api.me) "You" else firstName(model.fullName(id))) {
                        items[i] = ItemRow(it.name, it.price, if (id in it.who) it.who - id else it.who + id)
                    }
                }
            }
        }
    }
    Box(Modifier.fillMaxWidth().padding(top = 4.dp)) { SecondaryButton("Add item", { items.add(ItemRow(who = members.toSet())) }) }
}

/** Per-person totals from the items, with the leftover (tax, tip, fees) spread in proportion. */
private fun itemsSplit(total: Long, items: List<ItemRow>): Result<Map<String, Long>> = runCatching {
    require(items.isNotEmpty()) { "Add at least one item" }
    val sub = HashMap<String, Long>()
    var sum = 0L
    items.forEach { r ->
        val p = parseCents(r.price) ?: error("Enter a price for every item")
        require(r.who.isNotEmpty()) { "Tick who shared each item" }
        sum += p
        equalSplit(p, r.who.sorted()).forEach { (u, c) -> sub.merge(u, c, Long::plus) }
    }
    require(sum <= total) { "Items add up to ${money(sum - total)} more than the total" }
    weightedSplit(total, sub)
}

// ───────────────────────────── the screen ─────────────────────────────

/**
 * Add or edit an expense. Several people can pay (equally, by amount, percent or shares) and the cost can be split
 * the same ways, or item by item from a receipt. [initial] turns it into an edit.
 */
@Composable
fun AddExpenseScreen(
    g: Group, members: List<String>, model: HomeModel, initial: Expense?, onClose: () -> Unit,
    onSave: suspend (Api.ExpenseInput, String?) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val snack = LocalSnack.current
    val scope = rememberCoroutineScope()
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val me = Api.me ?: ""
    val editing = initial != null

    var amount by remember { mutableStateOf(initial?.let { "%.2f".format(it.amountCents / 100.0) } ?: "") }
    var desc by remember { mutableStateOf(initial?.description.orEmpty()) }
    var category by remember { mutableStateOf(Cat.of(initial?.category)) }
    var catPicked by remember { mutableStateOf(initial?.category != null) } // once the user picks one, stop guessing
    var date by remember { mutableStateOf(initial?.let { runCatching { LocalDate.parse(it.date) }.getOrNull() } ?: LocalDate.now()) }
    var notes by remember { mutableStateOf(initial?.notes.orEmpty()) }
    var showNotes by remember { mutableStateOf(!initial?.notes.isNullOrBlank()) }
    var showDate by remember { mutableStateOf(false) }
    var showCats by remember { mutableStateOf(false) }
    var repeat by remember { mutableStateOf(0) } // 0 never, 1 weekly, 2 monthly
    var keepSplit by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var photo by remember { mutableStateOf<String?>(null) }
    var photoChanged by remember { mutableStateOf(false) }
    var photoBusy by remember { mutableStateOf(false) }
    var viewPhoto by remember { mutableStateOf(false) }
    var photoMenu by remember { mutableStateOf(false) }
    val items = remember { mutableStateListOf<ItemRow>() }
    var itemsMode by remember { mutableStateOf(false) }

    val owes = remember(members) { SplitState(members.toSet()) }
    val paid = remember(members) { SplitState(setOf(if (me in members) me else members.firstOrNull().orEmpty())) }
    var multi by remember { mutableStateOf(false) }
    var payer by remember(members) { mutableStateOf(if (me in members) me else members.firstOrNull().orEmpty()) }

    // Start from the saved default split for this group, or from the expense being edited.
    LaunchedEffect(members, initial?.id) {
        if (members.isEmpty()) return@LaunchedEffect
        if (initial != null) {
            if (initial.payers.size > 1) {
                multi = true; paid.mode = Mode.EXACT; paid.chosen = initial.payers.map { it.userId }.toSet()
                paid.vals = initial.payers.associate { it.userId to "%.2f".format(it.paidCents / 100.0) }
            } else payer = initial.paidBy
            owes.mode = Mode.EXACT; owes.chosen = initial.splits.filter { it.shareCents > 0 }.map { it.userId }.toSet()
            owes.vals = initial.splits.associate { it.userId to "%.2f".format(it.shareCents / 100.0) }
        } else Api.splitDefault(g.id)?.let { d ->
            val parts = d.split("|")
            val m = Mode.entries.firstOrNull { it.name == parts.getOrNull(0) } ?: return@let
            val chosen = parts.getOrNull(1).orEmpty().split(",").filter { it in members }.toSet()
            if (chosen.isEmpty()) return@let
            owes.mode = m; owes.chosen = chosen
            owes.vals = parts.getOrNull(2).orEmpty().split(",").mapNotNull { it.split(":").takeIf { p -> p.size == 2 }?.let { p -> p[0] to p[1] } }.toMap()
            keepSplit = true
        }
    }

    val cents = parseCents(amount)?.takeIf { it <= 10_00_00_000_00L } // up to 10 crore rupees
    val ownRes: Result<Map<String, Long>>? = cents?.let { if (itemsMode) itemsSplit(it, items) else owes.result(it, members) }
    val payRes: Result<Map<String, Long>>? = cents?.let { if (multi) paid.result(it, members) else Result.success(mapOf(payer to it)) }

    fun attach(uri: Uri) {
        photoBusy = true
        scope.launch {
            val enc = encodePhoto(ctx, uri)
            if (enc == null) snack.say("Couldn't use that photo.") else { photo = enc; photoChanged = true }
            photoBusy = false
        }
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { it?.let(::attach) }
    var camUri by remember { mutableStateOf<Uri?>(null) }
    val cam = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) camUri?.let(::attach) }
    // Editing: show the photo already saved with this expense.
    LaunchedEffect(initial?.id) { if (initial != null) runCatching { Api.expensePhoto(initial.id) }.getOrNull()?.let { if (!photoChanged) photo = it } }

    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (!editing) runCatching { focus.requestFocus() } }

    val hint: String? = when {
        cents == null || cents <= 0L -> null
        desc.isBlank() -> null
        multi && paid.picked(members).isEmpty() -> "Choose who paid"
        multi && payRes?.isFailure == true -> "Paid by: " + payRes.exceptionOrNull()?.message
        !itemsMode && owes.picked(members).isEmpty() -> "Choose at least one person to split with"
        ownRes?.isFailure == true -> if (owes.mode == Mode.EXACT && !itemsMode) {
            val e = owes.picked(members).sumOf { parseCents(owes.vals[it].orEmpty()) ?: 0L }
            if (e < cents) "${money(cents - e, g.currency)} left to assign" else if (e > cents) "${money(e - cents, g.currency)} over the total" else ownRes.exceptionOrNull()?.message
        } else ownRes.exceptionOrNull()?.message
        else -> null
    }
    val ready = ownRes?.isSuccess == true && payRes?.isSuccess == true && desc.isNotBlank() && !saving

    Column(Modifier.fillMaxSize().background(cs.background).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClose) { Icon(Icons.Rounded.Close, contentDescription = "Close") }
            Text(if (editing) "Edit expense" else "New expense", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            Box {
                IconButton({ photoMenu = true }, enabled = !photoBusy) { Icon(Icons.Rounded.AddAPhoto, contentDescription = "Add a photo") }
                DropdownMenu(photoMenu, { photoMenu = false }) {
                    DropdownMenuItem(
                        { Text("Take a photo") }, leadingIcon = { Icon(Icons.Rounded.AddAPhoto, null) },
                        onClick = {
                            photoMenu = false
                            val f = java.io.File(ctx.cacheDir, "receipts").apply { mkdirs() }.let { java.io.File.createTempFile("receipt", ".jpg", it) }
                            val u = androidx.core.content.FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
                            camUri = u; cam.launch(u)
                        },
                    )
                    DropdownMenuItem(
                        { Text("Choose a photo") }, leadingIcon = { Icon(Icons.Rounded.PhotoLibrary, null) },
                        onClick = { photoMenu = false; pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    )
                }
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), Arrangement.Center, Alignment.CenterVertically) {
                Text(symbol(g.currency).trim(), style = MaterialTheme.typography.displaySmall, color = cs.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                BasicTextField(
                    value = amount, onValueChange = { amount = cleanAmount(it) }, singleLine = true,
                    modifier = Modifier.focusRequester(focus).width(220.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    textStyle = MaterialTheme.typography.displayLarge.copy(color = cs.onSurface, textAlign = TextAlign.Start),
                    cursorBrush = SolidColor(cs.primary),
                    decorationBox = { inner ->
                        Box { if (amount.isEmpty()) Text("0", style = MaterialTheme.typography.displayLarge, color = cs.outlineVariant); inner() }
                    },
                )
            }
            Spacer(Modifier.height(20.dp))
            HissaField(desc, {
                desc = it.take(200)
                if (!catPicked) category = guessCategory(desc) ?: Cat.GENERAL
            }, "What was it for?")

            Spacer(Modifier.height(16.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.clip(CircleShape).background(if (category == Cat.GENERAL) cs.surfaceContainer else cs.primaryContainer).pressable(role = null) { showCats = true }
                        .padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(category.icon, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(category.label, style = MaterialTheme.typography.labelLarge)
                }
                Row(
                    Modifier.clip(CircleShape).background(cs.surfaceContainer).pressable(role = null) { showDate = true }.padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.CalendarMonth, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(dayLabel(date.toString()), style = MaterialTheme.typography.labelLarge)
                }
                if (!showNotes) Row(
                    Modifier.clip(CircleShape).background(cs.surfaceContainer).pressable(role = null) { showNotes = true }.padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add a note", style = MaterialTheme.typography.labelLarge)
                }
            }
            if (showNotes) { Spacer(Modifier.height(12.dp)); HissaField(notes, { notes = it.take(500) }, "Note", singleLine = false) }
            if (photo != null || photoBusy) {
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(cs.surfaceContainer).padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val bmp = decodeGroupImage(photo)
                    if (bmp != null) Image(bmp, contentDescription = "Attached photo", contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).pressable(role = null) { viewPhoto = true })
                    else Box(Modifier.size(56.dp), Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp), color = cs.primary, strokeWidth = 2.5.dp) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (photoBusy) "Adding photo…" else "Photo attached", style = MaterialTheme.typography.titleSmall)
                        if (!photoBusy) Text("Tap to view", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    }
                    IconButton({ photo = null; photoChanged = true }, enabled = !photoBusy) { Icon(Icons.Rounded.Close, contentDescription = "Remove photo") }
                }
            }

            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Paid by", style = MaterialTheme.typography.titleSmall, color = cs.onSurfaceVariant, modifier = Modifier.weight(1f))
                Text(
                    if (multi) "Just one person" else "Several people", style = MaterialTheme.typography.labelLarge, color = cs.onSurface,
                    modifier = Modifier.clip(CircleShape).background(cs.surfaceContainer).pressable(role = null) {
                        multi = !multi; if (multi && paid.chosen.isEmpty()) paid.chosen = setOf(payer)
                    }.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            if (!multi) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                members.forEach { id ->
                    SelectableAvatar(model.fullName(id), id, payer == id, if (id == me) "You" else firstName(model.fullName(id))) { payer = id }
                }
            }
            if (multi) PeopleRows(members, model, paid, cents, g.currency)

            Spacer(Modifier.height(24.dp))
            Text("Split between", style = MaterialTheme.typography.titleSmall, color = cs.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            val labels = Mode.entries.map { it.label } + "Items"
            Segmented(labels, if (itemsMode) labels.lastIndex else owes.mode.ordinal, { i ->
                if (i == labels.lastIndex) { itemsMode = true; if (items.isEmpty()) items.add(ItemRow(who = members.toSet())) }
                else { itemsMode = false; owes.mode = Mode.entries[i] }
            })
            Spacer(Modifier.height(8.dp))
            if (itemsMode) {
                ItemsEditor(members, model, items, g.currency)
                ownRes?.getOrNull()?.let { r ->
                    Spacer(Modifier.height(8.dp))
                    members.filter { (r[it] ?: 0L) > 0 }.forEach { id ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text(model.name(id), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            Text(money(r.getValue(id), g.currency), style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
            } else PeopleRowsBody(members, model, owes, cents, g.currency)

            if (!editing) {
                Spacer(Modifier.height(24.dp))
                Text("Repeat", style = MaterialTheme.typography.titleSmall, color = cs.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Segmented(listOf("Never", "Every week", "Every month"), repeat, { repeat = it })
                if (!itemsMode) Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(keepSplit, { keepSplit = it }, colors = CheckboxDefaults.colors(checkedColor = cs.primary, checkmarkColor = cs.onPrimary, uncheckedColor = cs.outline))
                    Text("Use this split by default in ${g.name}", style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            if (hint != null) Text(
                hint, style = MaterialTheme.typography.bodyMedium,
                color = if (cents != null && desc.isNotBlank() && ownRes?.isFailure == true) cs.error else cs.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp).semantics { liveRegion = LiveRegionMode.Polite },
                textAlign = TextAlign.Center,
            )
            val run = rememberRunner(busy = { saving = it })
            PrimaryButton(
                if (editing) "Save changes" else "Save expense", loading = saving, enabled = ready,
                onClick = {
                    val x = Api.ExpenseInput(desc.trim(), cents!!, payRes!!.getOrThrow(), ownRes!!.getOrThrow(), date, category.id.ifEmpty { null }, notes.trim().ifEmpty { null }, photo, photoChanged)
                    if (!editing && !itemsMode) Api.setSplitDefault(g.id, if (keepSplit) owes.mode.name + "|" + owes.picked(members).joinToString(",") + "|" + owes.vals.entries.joinToString(",") { it.key + ":" + it.value } else null)
                    run { onSave(x, listOf(null, "week", "month")[repeat]) }
                },
            )
        }
    }

    if (viewPhoto) photo?.let { PhotoViewer(it) { viewPhoto = false } }
    if (showCats) CategorySheet(category, { category = it; catPicked = true; showCats = false }, { showCats = false })

    if (showDate) {
        val st = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        DatePickerDialog(
            { showDate = false },
            confirmButton = { TextButton({ st.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }; showDate = false }) { Text("OK") } },
            dismissButton = { TextButton({ showDate = false }) { Text("Cancel") } },
        ) { DatePicker(st) }
    }
}

/** The "who owes" rows without a second mode switch (the screen already shows one with the Items tab). */
@Composable
private fun PeopleRowsBody(members: List<String>, model: HomeModel, st: SplitState, total: Long?, cur: String) {
    val cs = MaterialTheme.colorScheme
    val res = total?.let { st.result(it, members) }?.getOrNull()
    val unit = when (st.mode) { Mode.EXACT -> ""; Mode.PERCENT -> "%"; else -> "×" }
    members.forEach { id ->
        val on = id in st.chosen
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).pressable(role = null) { st.chosen = if (on) st.chosen - id else st.chosen + id }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                on, { st.chosen = if (it) st.chosen + id else st.chosen - id },
                colors = CheckboxDefaults.colors(checkedColor = cs.primary, checkmarkColor = cs.onPrimary, uncheckedColor = cs.outline),
            )
            Avatar(model.fullName(id), id, 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(model.name(id), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val mine = res?.get(id)
                if (on && mine != null) Text(money(mine, cur), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
            if (on && st.mode != Mode.EQUAL) AmountBox(st.vals[id].orEmpty(), { st.edit(id, it, members, total) }, if (st.mode == Mode.EXACT) "0.00" else unit)
        }
    }
}

@Composable
private fun CategorySheet(current: Cat, onPick: (Cat) -> Unit, onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    AppSheet(onDismiss, "Category") {
        Cat.entries.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { c ->
                    val on = c == current
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(if (on) cs.primaryContainer else cs.surfaceContainer)
                            .pressable(role = null) { onPick(c) }.padding(vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(c.icon, null, Modifier.size(26.dp))
                        Spacer(Modifier.height(6.dp))
                        Text(c.label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
