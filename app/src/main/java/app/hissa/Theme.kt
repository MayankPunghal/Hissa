@file:OptIn(ExperimentalTextApi::class)

package app.hissa

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Brand: marigold on ink. Marigold is the only chromatic brand colour; red/green are reserved for money direction. */
object Brand {
    val Marigold = Color(0xFFFFB020)
    val Ink = Color(0xFF111111)
}

val Manrope = FontFamily(
    listOf(400, 500, 600, 700, 800).map { w ->
        Font(R.font.manrope, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
    },
)

private val Light = lightColorScheme(
    primary = Brand.Marigold, onPrimary = Color(0xFF1B1300),
    primaryContainer = Color(0xFFFFF0CC), onPrimaryContainer = Color(0xFF3A2700),
    secondary = Brand.Ink, onSecondary = Color.White,
    secondaryContainer = Color(0xFFF1F1EF), onSecondaryContainer = Brand.Ink,
    background = Color.White, onBackground = Brand.Ink,
    surface = Color.White, onSurface = Brand.Ink,
    surfaceVariant = Color(0xFFF3F3F1), onSurfaceVariant = Color(0xFF6A6A66),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF8F8F6),
    surfaceContainer = Color(0xFFF3F3F1), surfaceContainerHigh = Color(0xFFEDEDEA),
    surfaceContainerHighest = Color(0xFFE7E7E3),
    outline = Color(0xFF8C8C87), outlineVariant = Color(0xFFE4E4E0),
    error = Color(0xFFD93A32), onError = Color.White,
    errorContainer = Color(0xFFFDE8E6), onErrorContainer = Color(0xFF5C0F0A),
    inverseSurface = Brand.Ink, inverseOnSurface = Color.White,
)

private val Dark = darkColorScheme(
    primary = Color(0xFFFFB62E), onPrimary = Color(0xFF1B1300),
    primaryContainer = Color(0xFF3D2A00), onPrimaryContainer = Color(0xFFFFE2A0),
    secondary = Color(0xFFF2F2F0), onSecondary = Brand.Ink,
    secondaryContainer = Color(0xFF202023), onSecondaryContainer = Color(0xFFF2F2F0),
    background = Color(0xFF0B0B0C), onBackground = Color(0xFFF2F2F0),
    surface = Color(0xFF0B0B0C), onSurface = Color(0xFFF2F2F0),
    surfaceVariant = Color(0xFF18181A), onSurfaceVariant = Color(0xFFA0A09B),
    surfaceContainerLowest = Color(0xFF070708), surfaceContainerLow = Color(0xFF121214),
    surfaceContainer = Color(0xFF18181A), surfaceContainerHigh = Color(0xFF202023),
    surfaceContainerHighest = Color(0xFF28282C),
    outline = Color(0xFF77776F), outlineVariant = Color(0xFF2A2A2E),
    error = Color(0xFFFF7A72), onError = Color(0xFF3A0905),
    errorContainer = Color(0xFF33100E), onErrorContainer = Color(0xFFFFD8D4),
    inverseSurface = Color(0xFFF2F2F0), inverseOnSurface = Brand.Ink,
)

/** Money direction. Never carried by colour alone: every use also says "owe" or "owed". */
@Immutable
class Semantic(val owed: Color, val owedBg: Color, val owe: Color, val oweBg: Color)

private val LightSemantic = Semantic(Color(0xFF0B7A42), Color(0xFFE3F6EB), Color(0xFFD1342B), Color(0xFFFDE8E6))
private val DarkSemantic = Semantic(Color(0xFF3DD68C), Color(0xFF0F2A1D), Color(0xFFFF7A72), Color(0xFF33100E))
val LocalSemantic = staticCompositionLocalOf { LightSemantic }

private fun style(w: FontWeight, size: Int, line: Int, track: Double = 0.0) = TextStyle(
    fontFamily = Manrope, fontWeight = w, fontSize = size.sp, lineHeight = line.sp,
    letterSpacing = track.sp, fontFeatureSettings = "tnum",
)

private val HissaType = Typography(
    displayLarge = style(FontWeight.ExtraBold, 56, 60, -1.5),
    displayMedium = style(FontWeight.ExtraBold, 44, 48, -1.0),
    displaySmall = style(FontWeight.ExtraBold, 34, 40, -0.6),
    headlineLarge = style(FontWeight.Bold, 30, 36, -0.5),
    headlineMedium = style(FontWeight.Bold, 24, 30, -0.3),
    headlineSmall = style(FontWeight.Bold, 20, 26, -0.2),
    titleLarge = style(FontWeight.Bold, 20, 26),
    titleMedium = style(FontWeight.SemiBold, 16, 22),
    titleSmall = style(FontWeight.SemiBold, 14, 20),
    bodyLarge = style(FontWeight.Medium, 16, 24),
    bodyMedium = style(FontWeight.Medium, 14, 20),
    bodySmall = style(FontWeight.Medium, 12, 16),
    labelLarge = style(FontWeight.SemiBold, 14, 20),
    labelMedium = style(FontWeight.SemiBold, 12, 16),
    labelSmall = style(FontWeight.Medium, 11, 16),
)

private val HissaShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
)

@Composable
fun useDark(): Boolean = when (Api.themeMode) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }

@Composable
fun HissaTheme(content: @Composable () -> Unit) {
    val dark = useDark()
    CompositionLocalProvider(LocalSemantic provides if (dark) DarkSemantic else LightSemantic) {
        MaterialTheme(colorScheme = if (dark) Dark else Light, typography = HissaType, shapes = HissaShapes, content = content)
    }
}

val MaterialTheme.semantic: Semantic @Composable get() = LocalSemantic.current
