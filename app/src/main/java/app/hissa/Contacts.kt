package app.hissa

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** The message people receive: what the group is and how to get in. */
fun inviteMessage(g: Group) =
    "Join \"${g.name}\" on Hissa and split bills with me.\n\nTap to join:\n${Api.inviteLink(g.inviteCode)}\n\nInvite code:\n${g.inviteCode}"

/** Digits only, with India's +91 added when the number has no country code. Used for WhatsApp links. */
fun whatsappNumber(raw: String): String {
    val d = raw.filter(Char::isDigit)
    return if (d.length == 10) "91$d" else d.trimStart('0')
}

private fun open(ctx: Context, intent: Intent): Boolean =
    try { ctx.startActivity(intent); true } catch (e: Exception) { false }

fun inviteBySms(ctx: Context, number: String, g: Group): Boolean =
    open(ctx, Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(number))).putExtra("sms_body", inviteMessage(g)))

fun inviteByWhatsApp(ctx: Context, number: String, g: Group): Boolean =
    open(ctx, Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/${whatsappNumber(number)}?text=" + Uri.encode(inviteMessage(g)))))

/**
 * Opens the phone's own contact picker. It hands back just the one contact the user chose, so the app
 * never asks for permission to read the address book.
 */
@Composable
fun rememberContactPicker(onPicked: (name: String, number: String) -> Unit): () -> Unit {
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val uri = r.data?.data ?: return@rememberLauncherForActivityResult
        runCatching {
            ctx.contentResolver.query(
                uri, arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER), null, null, null,
            )?.use { c -> if (c.moveToFirst()) onPicked(c.getString(0).orEmpty(), c.getString(1).orEmpty()) }
        }
    }
    return { launcher.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)) }
}

/** "Add people": pick a group, then either choose someone from contacts or share the link. They are separate buttons. */
@Composable
fun AddPeopleSheet(groups: List<Group>, initial: Group?, onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    var sel by remember { mutableStateOf(initial ?: groups.firstOrNull()) }
    var picked by remember { mutableStateOf<Pair<String, String>?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    val pick = rememberContactPicker { n, num -> picked = n to num; err = null }
    AppSheet(onDismiss, "Add people") {
        val g = sel
        if (g == null) {
            Text("Create a group first, then invite friends to it.", style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant)
            return@AppSheet
        }
        if (groups.size > 1) {
            Text("Add to", style = MaterialTheme.typography.titleSmall, color = cs.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                groups.forEach { x ->
                    val on = x.id == g.id
                    Text(
                        x.name, style = MaterialTheme.typography.labelLarge, color = if (on) cs.onPrimary else cs.onSurface, maxLines = 1,
                        modifier = Modifier.clip(CircleShape).background(if (on) cs.primary else cs.surfaceContainer)
                            .pressable(role = null) { sel = x; picked = null }.padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
        }
        val p = picked
        if (p == null) {
            PrimaryButton("Choose from contacts", pick)
        } else {
            Text(p.first.ifBlank { p.second }, style = MaterialTheme.typography.titleMedium)
            if (p.first.isNotBlank()) Text(p.second, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PrimaryButton("Message", { if (inviteBySms(ctx, p.second, g)) onDismiss() else err = "No messaging app found." }, Modifier.weight(1f))
                SecondaryButton("WhatsApp", { if (inviteByWhatsApp(ctx, p.second, g)) onDismiss() else err = "WhatsApp is not installed." }, Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            SecondaryButton("Choose someone else", pick)
        }
        ErrorText(err)
        Spacer(Modifier.height(12.dp))
        SecondaryButton("Share invite link", { shareInvite(ctx, g); onDismiss() })
    }
}
