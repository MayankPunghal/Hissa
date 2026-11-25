package app.hissa

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.ImageBitmap
import java.io.ByteArrayOutputStream

// ───────────────────────────── group picture helpers ─────────────────────────────

/** Centre-crops the picked photo to a 256 px square JPEG and returns it as a data URL (about 15 to 30 KB). */
fun encodeGroupImage(ctx: Context, uri: Uri): String? = try {
    val src = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = 4 }) }
        ?: ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
    src?.let { b ->
        val side = minOf(b.width, b.height)
        val sq = Bitmap.createBitmap(b, (b.width - side) / 2, (b.height - side) / 2, side, side)
        val out = Bitmap.createScaledBitmap(sq, 256, 256, true)
        val bytes = ByteArrayOutputStream().also { out.compress(Bitmap.CompressFormat.JPEG, 80, it) }.toByteArray()
        "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
} catch (e: Exception) { null }

private val decoded = HashMap<Int, ImageBitmap?>()
fun decodeGroupImage(dataUrl: String?): ImageBitmap? {
    if (dataUrl.isNullOrBlank()) return null
    return decoded.getOrPut(dataUrl.hashCode()) {
        try {
            val bytes = Base64.decode(dataUrl.substringAfter("base64,"), Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        } catch (e: Exception) { null }
    }
}

/** The group's picture, or a coloured letter tile when it has none. */
@Composable
fun GroupTile(g: Group, size: Dp, image: String? = g.image, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(size * 0.28f)
    val bmp = decodeGroupImage(image)
    if (bmp != null) Image(bmp, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier.size(size).clip(shape))
    else Box(modifier.size(size).clip(shape).background(avatarColor(g.id)), Alignment.Center) {
        Text(initials(g.name).take(1), style = MaterialTheme.typography.titleLarge, color = Color.White)
    }
}

fun shareInvite(ctx: Context, g: Group) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, inviteMessage(g))
    }
    ctx.startActivity(Intent.createChooser(send, "Invite friends"))
}

// ───────────────────────────── settings screen ─────────────────────────────

@Composable
private fun ActionRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, accent: Boolean = false, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).pressable(role = null, onClick = onClick).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(if (accent) cs.primaryContainer else cs.surfaceContainer), Alignment.Center) { Icon(icon, null, Modifier.size(22.dp), tint = if (accent) cs.onPrimaryContainer else cs.onSurface) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun GroupSettings(g: Group, members: List<String>, net: Map<String, Long>, model: HomeModel, onClose: () -> Unit, onGone: () -> Unit) {
    val sem = MaterialTheme.semantic
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val snack = LocalSnack.current
    val me = Api.me ?: ""
    var name by remember(g.id) { mutableStateOf(g.name) }
    var image by remember(g.id) { mutableStateOf(g.image) }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    var sureLeave by remember { mutableStateOf(false) }
    var addPeople by remember { mutableStateOf(false) }
    var sureDelete by remember { mutableStateOf(false) }
    val run = rememberRunner({ busy = it }, { err = it })
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val enc = encodeGroupImage(ctx, uri)
            if (enc == null) err = "That picture couldn't be read. Try another one." else { image = enc; err = null }
        }
    }
    var rec by remember(g.id) { mutableStateOf<List<RecurringRow>>(emptyList()) }
    androidx.compose.runtime.LaunchedEffect(g.id) { rec = runCatching { Api.recurring(g.id) }.getOrDefault(emptyList()) }
    var plan by remember { mutableStateOf<ImportPlan?>(null) }
    var importing by remember { mutableStateOf(false) }
    val csvPick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = runCatching { ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }.getOrNull()
            if (text == null) err = "That file couldn't be read."
            else {
                val p = planImport(text, members, members.associateWith { m -> listOf(model.fullName(m), firstName(model.fullName(m))) }, me)
                if (p.rows.isEmpty()) err = "No expenses found. The file needs Date, Description and Amount columns." else { plan = p; err = null }
            }
        }
    }
    BackHandler(onBack = onClose)
    val dirty = name.trim() != g.name || image != g.image
    val link = Api.inviteLink(g.inviteCode)

    Column(Modifier.fillMaxSize().background(cs.background).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClose) { Icon(Icons.Rounded.Close, contentDescription = "Close") }
            Text("Group settings", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            TextButton(
                onClick = { run { Api.updateGroup(g.id, name, image); model.refresh(); onClose() } },
                enabled = dirty && name.trim().length >= 2 && !busy,
            ) { Text("Save", style = MaterialTheme.typography.titleSmall) }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(12.dp))
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.clip(RoundedCornerShape(32.dp)).pressable(role = null) {
                    pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) {
                    GroupTile(g, 112.dp, image)
                    Box(
                        Modifier.align(Alignment.BottomEnd).padding(6.dp).size(32.dp).clip(CircleShape).background(cs.primary),
                        Alignment.Center,
                    ) { Icon(Icons.Rounded.CameraAlt, contentDescription = "Change group picture", tint = cs.onPrimary, modifier = Modifier.size(18.dp)) }
                }
                if (image != null) TextButton({ image = null }) { Text("Remove picture", color = cs.onSurfaceVariant) }
            }
            Spacer(Modifier.height(12.dp))
            HissaField(name, { name = it.take(80); err = null }, "Group name", error = Check.groupName(name))

            SettingsTitle("Group members")
            ActionRow(Icons.Rounded.PersonAdd, "Add from contacts", "Pick someone and send them the invite", accent = true) { addPeople = true }
            ActionRow(Icons.Rounded.Share, "Share invite link", "By message, email or chat", accent = true) { shareInvite(ctx, g) }
            if (addPeople) AddPeopleSheet(listOf(g), g) { addPeople = false }
            ActionRow(Icons.Rounded.Link, "Copy invite link", link) {
                clipboard.setText(AnnotatedString(link)); run { snack.say("Link copied") }
            }
            ActionRow(Icons.Rounded.ContentCopy, "Copy invite code", g.inviteCode) {
                clipboard.setText(AnnotatedString(g.inviteCode)); run { snack.say("Code copied") }
            }
            members.forEach { m ->
                val v = net[m] ?: 0L
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(model.fullName(m), m, 44.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(model.fullName(m) + if (m == me) " (you)" else "", style = MaterialTheme.typography.titleMedium)
                        if (m == g.createdBy) Text("Group creator", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    }
                    when {
                        v > 0 -> Column(horizontalAlignment = Alignment.End) { Text("gets back", style = MaterialTheme.typography.labelSmall, color = sem.owed); Text(money(v, g.currency), style = MaterialTheme.typography.titleSmall, color = sem.owed) }
                        v < 0 -> Column(horizontalAlignment = Alignment.End) { Text("owes", style = MaterialTheme.typography.labelSmall, color = sem.owe); Text(money(v, g.currency), style = MaterialTheme.typography.titleSmall, color = sem.owe) }
                        else -> Text("settled up", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                    }
                }
            }

            SettingsTitle("Advanced settings")
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Simplify group debts", style = MaterialTheme.typography.titleMedium)
                        InfoTip("Combines debts so fewer payments clear everyone. If you owe Asha and Asha owes Ravi, you pay Ravi directly. Kept on this phone.")
                    }
                    Text("Fewer payments to settle up", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
                Switch(Api.simplifyDebts(g.id), { Api.setSimplifyDebts(g.id, it) })
            }

            if (rec.isNotEmpty()) {
                SettingsTitle("Repeating expenses")
                rec.forEach { r ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(r.description, style = MaterialTheme.typography.titleMedium)
                            Text("${money(r.amountCents, g.currency)} every ${r.every}, next ${dayLabel(r.nextDate)}", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                        }
                        TextButton({ run { Api.deleteRecurring(r.id); rec = rec.filter { it.id != r.id }; snack.say("Stopped repeating") } }) { Text("Stop", color = cs.error) }
                    }
                }
            }

            SettingsTitle("Import")
            ActionRow(Icons.Rounded.FileUpload, "Import expenses from a file", "A Splitwise export or any CSV with Date, Description and Amount") {
                csvPick.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "application/vnd.ms-excel", "application/csv"))
            }

            ErrorText(err)
            SettingsTitle("Leave or delete")
            SecondaryButton(if (sureLeave) "Tap again to leave" else "Leave group", muted = true, onClick = {
                if (!sureLeave) sureLeave = true
                else run { Api.leaveGroup(g.id); model.refresh(); snack.say("You left ${g.name}"); onGone() }
            })
            Text("You can leave once you are settled up.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp, start = 4.dp))
            if (g.createdBy == me) {
                Spacer(Modifier.height(12.dp))
                SecondaryButton(if (sureDelete) "Tap again to delete for everyone" else "Delete group", danger = true, onClick = {
                    if (!sureDelete) sureDelete = true
                    else run { Api.deleteGroup(g.id); model.refresh(); snack.say("Group deleted"); onGone() }
                })
                Text("Deletes the group and all its expenses for every member. This can't be undone.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp, start = 4.dp))
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    plan?.let { p ->
        var done by remember(p) { mutableStateOf(0) }
        var failed by remember(p) { mutableStateOf<String?>(null) }
        val go = rememberRunner({ importing = it }, { failed = it })
        AppSheet({ if (!importing) plan = null }, "Import expenses") {
            Text(
                "Found ${p.rows.size} ${if (p.rows.size == 1) "expense" else "expenses"} to add to ${g.name}." +
                    if (p.skipped > 0) " ${p.skipped} rows were skipped because they were empty or didn't add up." else "",
                style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant,
            )
            if (importing) Text("Added $done of ${p.rows.size}…", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
            ErrorText(failed)
            Spacer(Modifier.height(20.dp))
            PrimaryButton("Import ${p.rows.size}", loading = importing, onClick = {
                go {
                    p.rows.forEach { Api.addExpense(g.id, it.input, it.settlement); done++ }
                    model.refresh(live = true); plan = null; snack.say("Imported ${p.rows.size} expenses"); onClose()
                }
            })
            Spacer(Modifier.height(8.dp))
            SecondaryButton("Cancel", { plan = null })
        }
    }
}

@Composable
private fun SettingsTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 24.dp, bottom = 6.dp))
}
