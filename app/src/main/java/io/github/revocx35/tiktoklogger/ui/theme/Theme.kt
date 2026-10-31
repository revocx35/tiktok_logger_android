package io.github.revocx35.tiktoklogger.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import io.github.revocx35.tiktoklogger.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Design tokens of the web UI (`web/src/styles.css` :root and its dark-mode override), so the app
 * looks the same. Light/dark follows the system, like the web UI.
 */
@Immutable
data class WaColors(
    val bg: Color,
    val panel: Color,
    val panel2: Color,
    val head: Color,
    val rail: Color,
    val chatBg: Color,
    val bubbleIn: Color,
    val bubbleOut: Color,
    /** Text on the owner's (blue) bubbles. */
    val bubbleOutText: Color,
    val text: Color,
    val text2: Color,
    val muted: Color,
    val border: Color,
    val hover: Color,
    val active: Color,
    val accent: Color,
    val accent2: Color,
    val accentText: Color,
    val link: Color,
    val danger: Color,
    val dangerBg: Color,
    val warn: Color,
    val warnBg: Color,
    val ok: Color,
    val system: Color,
    val quoteBg: Color,
    val tickRead: Color,
    /** Accent used for text on neutral backgrounds (chip label, quote sender): accent-2 light, ok dark. */
    val accentOnPanel: Color,
    val authBanner: Color,
    val avatarIconBg: Color,
    val avatarIconFg: Color,
    val shadow: Color,
    /** The active sidebar/tab item (TikTok's red; a lighter red in dark mode). */
    val accentNav: Color,
    /** The bottom tab bar (white / black, like the TikTok app). */
    val tabbar: Color,
    val isDark: Boolean,
)

val LightWaColors = WaColors(
    bg = Color(0xFFFFFFFF),
    panel = Color(0xFFFFFFFF),
    panel2 = Color(0xFFF1F1F2), // TikTok's rgba(22,24,35,.06) on white: search, chips, fields, incoming bubbles
    head = Color(0xFFFFFFFF),
    rail = Color(0xFFFFFFFF),
    chatBg = Color(0xFFFFFFFF),
    bubbleIn = Color(0xFFF1F1F2),
    bubbleOut = Color(0xFF2F80ED), // = web --out
    bubbleOutText = Color(0xFFFFFFFF),
    text = Color(0xFF161823),
    text2 = Color(0xBF161823),
    muted = Color(0x80161823),
    border = Color(0x1F161823),
    hover = Color(0x08161823),
    active = Color(0x0F161823),
    accent = Color(0xFFFE2C55),
    accent2 = Color(0xFFEF2950),
    accentText = Color(0xFFFFFFFF),
    link = Color(0xFF2B5DB9),
    danger = Color(0xFFFE2C55),
    dangerBg = Color(0x14FE2C55),
    warn = Color(0xFFF5A623),
    warnBg = Color(0xFFFFF4D6),
    ok = Color(0xFF20D5EC),
    system = Color(0x00FFFFFF),
    quoteBg = Color(0x0F161823),
    tickRead = Color(0xFF20D5EC),
    accentOnPanel = Color(0xFF161823),
    authBanner = Color(0xFFFE2C55),
    avatarIconBg = Color(0xFFF1F1F2),
    avatarIconFg = Color(0x80161823),
    shadow = Color(0x00000000),
    accentNav = Color(0xFFFE2C55),
    tabbar = Color(0xFFFFFFFF),
    isDark = false,
)

val DarkWaColors = WaColors(
    bg = Color(0xFF121212),
    panel = Color(0xFF121212),
    panel2 = Color(0xFF2F2F2F), // rgba(255,255,255,.12) on #121212
    head = Color(0xFF121212),
    rail = Color(0xFF121212),
    chatBg = Color(0xFF121212),
    bubbleIn = Color(0xFF2F2F2F),
    bubbleOut = Color(0xFF2F80ED),
    bubbleOutText = Color(0xFFFFFFFF),
    text = Color(0xE6FFFFFF),
    text2 = Color(0xBFFFFFFF),
    muted = Color(0x80FFFFFF),
    border = Color(0x1FFFFFFF),
    hover = Color(0x0AFFFFFF),
    active = Color(0x14FFFFFF),
    accent = Color(0xFFFE2C55),
    accent2 = Color(0xFFEF2950),
    accentText = Color(0xFFFFFFFF),
    link = Color(0xFF8AB4F8),
    danger = Color(0xFFFF3B5C),
    dangerBg = Color(0x1FFF3B5C),
    warn = Color(0xFFF5A623),
    warnBg = Color(0xFF3B3218),
    ok = Color(0xFF20D5EC),
    system = Color(0x00000000),
    quoteBg = Color(0x14FFFFFF),
    tickRead = Color(0xFF20D5EC),
    accentOnPanel = Color(0xE6FFFFFF),
    authBanner = Color(0xFFFE2C55),
    avatarIconBg = Color(0xFF2F2F2F),
    avatarIconFg = Color(0x80FFFFFF),
    shadow = Color(0x00000000),
    accentNav = Color(0xFFFF3B5C),
    tabbar = Color(0xFF000000),
    isDark = true,
)

val LocalWaColors = staticCompositionLocalOf { LightWaColors }

/** TikTok Sans (SIL OFL, bundled in res/font; `third_party/TikTokSans-OFL.txt`), as in the web UI. */
val TikTokSans = FontFamily(
    Font(R.font.tiktok_sans_regular, FontWeight.Normal),
    Font(R.font.tiktok_sans_medium, FontWeight.Medium),
    Font(R.font.tiktok_sans_semibold, FontWeight.SemiBold),
    Font(R.font.tiktok_sans_bold, FontWeight.Bold),
    Font(R.font.tiktok_sans_extrabold, FontWeight.ExtraBold),
)

/** Type scale of the web UI (15px base, TikTok's bold titles). */
object WaType {
    val base = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontFamily = TikTokSans)
    val bubble = TextStyle(fontSize = 15.sp, lineHeight = 21.sp, fontFamily = TikTokSans)
    val small = TextStyle(fontSize = 13.sp, lineHeight = 17.sp, fontFamily = TikTokSans)
    val tiny = TextStyle(fontSize = 11.25.sp, lineHeight = 14.sp, fontFamily = TikTokSans)
    val meta = TextStyle(fontSize = 10.5.sp, lineHeight = 13.sp, fontFamily = TikTokSans)
    val chatName = TextStyle(fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold, fontFamily = TikTokSans)
    val paneTitle = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold, fontFamily = TikTokSans)
    val pageTitle = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, fontFamily = TikTokSans)
    val sectionTitle = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold, fontFamily = TikTokSans)
    val authTitle = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, fontFamily = TikTokSans)
    val wordmark = TextStyle(fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp, fontFamily = TikTokSans)
    val tabLabel = TextStyle(fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.SemiBold, fontFamily = TikTokSans)
    val mono = TextStyle(fontFamily = FontFamily.Monospace)
}

object Wa {
    val colors: WaColors
        @Composable get() = LocalWaColors.current
}

@Composable
fun WaTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val c = if (dark) DarkWaColors else LightWaColors
    val scheme = if (dark) {
        darkColorScheme(
            primary = c.accent, onPrimary = c.accentText, secondary = c.accent2, background = c.bg, onBackground = c.text,
            surface = c.panel, onSurface = c.text, surfaceVariant = c.panel2, onSurfaceVariant = c.text2, outline = c.border,
            error = c.danger, surfaceContainerHigh = c.panel, surfaceContainerHighest = c.panel2, surfaceContainer = c.panel,
        )
    } else {
        lightColorScheme(
            primary = c.accent, onPrimary = c.accentText, secondary = c.accent2, background = c.bg, onBackground = c.text,
            surface = c.panel, onSurface = c.text, surfaceVariant = c.panel2, onSurfaceVariant = c.text2, outline = c.border,
            error = c.danger, surfaceContainerHigh = c.panel, surfaceContainerHighest = c.panel2, surfaceContainer = c.panel,
        )
    }
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(
            LocalWaColors provides c,
            androidx.compose.foundation.text.selection.LocalTextSelectionColors provides TextSelectionColors(c.accent, c.accent.copy(alpha = 0.3f)),
            androidx.compose.material3.LocalTextStyle provides WaType.base.copy(color = c.text),
            androidx.compose.material3.LocalContentColor provides c.text,
            content = content,
        )
    }
}
