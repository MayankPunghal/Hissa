@file:OptIn(ExperimentalMaterial3Api::class)

package app.hissa

import androidx.compose.animation.core.Spring
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.PlainTooltip
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

val LocalSnack = staticCompositionLocalOf<SnackbarHostState> { error("no snackbar host") }

/** Returns a launcher that runs [block], turns failures into a snackbar, and flips [busy] while it runs. */
@Composable
fun rememberRunner(busy: ((Boolean) -> Unit)? = null, onError: ((String?) -> Unit)? = null): (suspend () -> Unit) -> Unit {
    val scope = rememberCoroutineScope()
    val snack = LocalSnack.current
    return remember(scope, snack) {
        { block ->
            scope.launch {
                busy?.invoke(true)
                onError?.invoke(null)
                try { block() } catch (e: CancellationException) { throw e } catch (e: Exception) {
                    // Sheets sit above the snackbar, so they pass onError and show the message inline.
                    if (onError != null) onError(friendly(e)) else {
                        snack.currentSnackbarData?.dismiss()
                        snack.say(friendly(e))
                    }
                } finally { busy?.invoke(false) }
            }
            Unit
        }
    }
}

@Composable
fun ErrorText(msg: String?) {
    if (msg != null) Text(
        msg, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(top = 12.dp).semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/** Press feedback: a quick spring scale, no ripple. Reads like the big social apps. */
fun Modifier.pressable(enabled: Boolean = true, role: Role? = Role.Button, onClick: () -> Unit): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.97f else 1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), label = "press",
    )
    graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = src, indication = null, enabled = enabled, role = role, onClick = onClick)
}

@Composable
fun LogoMark(size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(RoundedCornerShape(size * 0.24f)).background(Brand.Ink), Alignment.Center) {
        Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = "Hissa", modifier = Modifier.fillMaxSize())
    }
}

private val avatarPalette = listOf(0xFF2F80ED, 0xFFE5484D, 0xFF12A18A, 0xFFE9692C, 0xFFD6409F, 0xFF7C5CFF, 0xFF5B6B7A, 0xFF7A8F00).map(::Color)
fun avatarColor(key: String) = avatarPalette[(key.hashCode() and Int.MAX_VALUE) % avatarPalette.size]

@Composable
fun Avatar(name: String, key: String, size: Dp = 40.dp, modifier: Modifier = Modifier) {
    val photo = decodeGroupImage(Api.photos[key])
    if (photo != null) Image(photo, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = modifier.size(size).clip(CircleShape))
    else Box(modifier.size(size).clip(CircleShape).background(avatarColor(key)), Alignment.Center) {
        Text(initials(name), color = Color.White, fontSize = (size.value * 0.38f).sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun AvatarStack(people: List<Pair<String, String>>, size: Dp = 28.dp, max: Int = 4) {
    val shown = people.take(max)
    Row(horizontalArrangement = Arrangement.spacedBy(-(size * 0.22f))) {
        shown.forEach { (name, key) ->
            Avatar(name, key, size, Modifier.border(2.dp, MaterialTheme.colorScheme.surfaceContainerLow, CircleShape))
        }
        if (people.size > max) {
            Box(
                Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .border(2.dp, MaterialTheme.colorScheme.surfaceContainerLow, CircleShape),
                Alignment.Center,
            ) { Text("+${people.size - max}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, loading: Boolean = false) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(14.dp))
            .background(if (enabled) cs.primary else cs.surfaceContainerHigh)
            .pressable(enabled = enabled && !loading, onClick = onClick),
        Alignment.Center,
    ) {
        if (loading) CircularProgressIndicator(Modifier.size(22.dp), color = cs.onPrimary, strokeWidth = 2.5.dp)
        else Text(text, style = MaterialTheme.typography.titleMedium, color = if (enabled) cs.onPrimary else cs.onSurfaceVariant)
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, danger: Boolean = false, muted: Boolean = false) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(14.dp)).background(if (danger) cs.errorContainer else cs.surfaceContainerHigh).pressable(onClick = onClick),
        Alignment.Center,
    ) { Text(text, style = MaterialTheme.typography.titleMedium, color = if (danger) cs.error else if (muted) cs.onSurfaceVariant else cs.onSurface) }
}

@Composable
fun HissaField(
    value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier,
    keyboard: KeyboardType = KeyboardType.Text, password: Boolean = false, singleLine: Boolean = true,
    trailing: @Composable (() -> Unit)? = null, leading: @Composable (() -> Unit)? = null, centered: Boolean = false, error: String? = null,
) {
    val cs = MaterialTheme.colorScheme
    OutlinedTextField(
        value = value, onValueChange = onChange, modifier = modifier.fillMaxWidth(), isError = error != null,
        supportingText = error?.let { { Text(it) } },
        label = { Text(label) }, singleLine = singleLine, shape = RoundedCornerShape(16.dp),
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboard),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        trailingIcon = trailing, leadingIcon = leading,
        textStyle = MaterialTheme.typography.bodyLarge.copy(textAlign = if (centered) TextAlign.Center else TextAlign.Start),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = cs.onSurface, unfocusedBorderColor = cs.outlineVariant,
            focusedContainerColor = cs.surface, unfocusedContainerColor = cs.surfaceContainerLow,
            cursorColor = cs.primary, focusedLabelColor = cs.onSurfaceVariant, unfocusedLabelColor = cs.onSurfaceVariant,
        ),
    )
}

/** Pill-shaped segmented control: used for sign in / sign up, Expenses / Balances and the split modes. */
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth().clip(CircleShape).background(cs.surfaceContainer).padding(4.dp)) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier.weight(1f).height(40.dp).clip(CircleShape)
                    .background(if (on) cs.surface else Color.Transparent)
                    .pressable(role = Role.Tab) { onSelect(i) },
                Alignment.Center,
            ) {
                Text(
                    label, style = MaterialTheme.typography.labelLarge,
                    color = if (on) cs.onSurface else cs.onSurfaceVariant, maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = 20.dp).padding(top = 20.dp, bottom = 8.dp),
    )
}

@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        LogoMark(64.dp)
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (action != null) { Spacer(Modifier.height(20.dp)); action() }
    }
}

/** Small tonal chip, used for balance status. */
@Composable
fun StatusPill(text: String, fg: Color, bg: Color) {
    Box(Modifier.clip(CircleShape).background(bg).padding(horizontal = 10.dp, vertical = 4.dp)) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = fg)
    }
}

@Composable
fun Divider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
}

@Composable
fun FullScreenLoading() {
    Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }
}

@Composable
fun Modifier.screenPadding() = this.padding(horizontal = 20.dp)

@Composable
fun SelectableAvatar(name: String, key: String, selected: Boolean, label: String = firstName(name), onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.width(64.dp).pressable(role = Role.RadioButton, onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(56.dp).border(if (selected) 2.5.dp else 0.dp, if (selected) cs.primary else Color.Transparent, CircleShape).padding(4.dp),
            Alignment.Center,
        ) { Avatar(name, key, 46.dp) }
        Spacer(Modifier.height(4.dp))
        Text(
            label, style = MaterialTheme.typography.labelMedium, maxLines = 1,
            color = if (selected) cs.onSurface else cs.onSurfaceVariant,
        )
    }
}

private val snackScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main)

/** Shows a message without waiting for it to disappear, and keeps showing it if the screen that asked has closed. */
fun SnackbarHostState.say(message: String) {
    currentSnackbarData?.dismiss()
    snackScope.launch { showSnackbar(message) }
}

/** Small (i) button that shows a one-line explanation on tap. Keeps helper text out of the layout. */
@Composable
fun InfoTip(text: String, modifier: Modifier = Modifier) {
    val state = androidx.compose.material3.rememberTooltipState(isPersistent = true)
    val scope = rememberCoroutineScope()
    androidx.compose.material3.TooltipBox(
        positionProvider = androidx.compose.material3.TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(text) } },
        state = state, modifier = modifier,
    ) {
        androidx.compose.material3.IconButton({ scope.launch { if (state.isVisible) state.dismiss() else state.show() } }, Modifier.size(32.dp)) {
            Icon(androidx.compose.material.icons.Icons.Outlined.Info, contentDescription = "More info: $text", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
