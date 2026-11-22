package app.bitbybit.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** Colors from docs/design-tokens.json. Green = signal HIGH / correct; amber = active / next action. */
object Bb {
    val Bg = Color(0xFF0E1116)
    val Surface = Color(0xFF171B22)
    val SurfaceSunken = Color(0xFF12161C)
    val DisplayBg = Color(0xFF0A0D10)
    val Border = Color(0xFF2A3140)
    val BorderSubtle = Color(0xFF232A36)
    val Text = Color(0xFFE8ECF2)
    val TextSecondary = Color(0xFFC5CCD6)
    val TextMuted = Color(0xFF9AA4B2)
    val TextLocked = Color(0xFF8A94A3)
    val High = Color(0xFF4ADE80)
    val HighBg = Color(0xFF12301F)
    val HighGlow = Color(0x404ADE80)
    val OnHigh = Color(0xFF0B2414)
    val Low = Color(0xFF3A4352)
    val PinOutline = Color(0xFF5B6575)
    val LedOff = Color(0xFF232A36)
    val SegmentOff = Color(0xFF18211B)
    val Accent = Color(0xFFF5B544)
    val AccentBg = Color(0xFF1D1A12)
    val AccentBorder = Color(0xFF4A3B17)
    val OnAccent = Color(0xFF1A1406)
    val Error = Color(0xFFFF8A7A)
    val ErrorText = Color(0xFFFFB4A8)
    val ErrorBg = Color(0xFF2A1715)
    val ErrorBorder = Color(0xFF6B2C25)
}

/**
 * TODO(fonts): bundle Space Grotesk, Inter and JetBrains Mono (all OFL) in res/font and swap these in.
 * Until then the platform sans-serif and monospace fonts stand in.
 */
object BbFonts {
    val Display: FontFamily = FontFamily.SansSerif
    val Body: FontFamily = FontFamily.SansSerif
    val Mono: FontFamily = FontFamily.Monospace
}

object BbType {
    val H1 = TextStyle(fontFamily = BbFonts.Display, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 32.sp, color = Bb.Text)
    val H2 = TextStyle(fontFamily = BbFonts.Display, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Bb.Text)
    val Title = TextStyle(fontFamily = BbFonts.Body, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Bb.Text)
    val Body = TextStyle(fontFamily = BbFonts.Body, fontSize = 14.sp, lineHeight = 20.sp, color = Bb.TextSecondary)
    val Caption = TextStyle(fontFamily = BbFonts.Body, fontSize = 12.sp, color = Bb.TextMuted)
    val Eyebrow = TextStyle(fontFamily = BbFonts.Mono, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 0.1.em, color = Bb.TextMuted)
    val Bit = TextStyle(fontFamily = BbFonts.Mono, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Bb.Text)
}

@Composable
fun BitByBitTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Bb.Accent,
            onPrimary = Bb.OnAccent,
            secondary = Bb.High,
            onSecondary = Bb.OnHigh,
            background = Bb.Bg,
            onBackground = Bb.Text,
            surface = Bb.Surface,
            onSurface = Bb.Text,
            outline = Bb.Border,
            error = Bb.Error,
        ),
        typography = Typography(bodyMedium = BbType.Body, titleMedium = BbType.Title),
        content = content,
    )
}
