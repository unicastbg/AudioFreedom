package com.svetlio.audiofreedom

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

/**
 * Root theme for every AudioFreedom screen.
 *
 * [mode] (System / Light / Dark) and [theme] (Classic / Mocha / Daylight / Studio) are
 * independent settings. Both are read from Compose state, so changing either one in
 * Settings recolours the app immediately; no restart is needed.
 *
 * @param controlSystemBars false for floating windows (the voice widget) that must not
 *        change the status/navigation bar icon colour of the app underneath.
 */
@Composable
internal fun AudioFreedomTheme(
    mode: ThemePreference,
    theme: AppTheme = AppTheme.Classic,
    controlSystemBars: Boolean = true,
    content: @Composable () -> Unit,
) {
    val dark = isDark(mode, theme, isSystemInDarkTheme())
    val colorScheme = colorSchemeFor(theme, dark)

    if (controlSystemBars) {
        val view = LocalView.current
        if (!view.isInEditMode) {
            SideEffect {
                // Android 15 draws the app edge to edge, so the bars are transparent and
                // only their icon colour needs to follow the theme.
                val window = view.context.findActivity()?.window ?: return@SideEffect
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
        }
    }

    CompositionLocalProvider(LocalLayoutStyle provides theme.layoutStyle) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = shapesFor(theme),
            typography = typographyFor(theme),
            content = content,
        )
    }
}

/**
 * How the main screen is laid out. Classic keeps the original flat list; the other
 * themes use the redesigned screen (power card, live meters, curve equalizer) with
 * their own arrangement of the effect sections.
 */
internal enum class LayoutStyle {
    /** Original layout, untouched. */
    Classic,
    /** Mocha: two-column effect tiles; tapping a tile opens its controls in a sheet. */
    Tiles,
    /** Daylight: one grouped card per effect with an icon badge; expands in place. */
    Cards,
    /** Studio: numbered rack rows with LEDs and monospaced readouts. */
    Rack,
    ;

    val modern: Boolean get() = this != Classic
}

internal val AppTheme.layoutStyle: LayoutStyle
    get() = when (this) {
        AppTheme.Classic -> LayoutStyle.Classic
        AppTheme.Mocha -> LayoutStyle.Tiles
        AppTheme.Daylight -> LayoutStyle.Cards
        AppTheme.Studio -> LayoutStyle.Rack
    }

internal val LocalLayoutStyle = staticCompositionLocalOf { LayoutStyle.Classic }

internal fun isDark(mode: ThemePreference, theme: AppTheme, systemDark: Boolean): Boolean =
    if (!theme.supportsLight) {
        true
    } else {
        when (mode) {
            ThemePreference.System -> systemDark
            ThemePreference.Light -> false
            ThemePreference.Dark -> true
        }
    }

/** Three colours (background, card, accent) for the theme picker's preview chips. */
internal fun themeSwatch(theme: AppTheme, dark: Boolean): List<Color> {
    val scheme = colorSchemeFor(theme, dark || !theme.supportsLight)
    return listOf(scheme.background, scheme.surfaceContainerHighest, scheme.primary)
}

internal fun colorSchemeFor(theme: AppTheme, dark: Boolean): ColorScheme = when (theme) {
    AppTheme.Classic -> if (dark) ClassicDark else ClassicLight
    AppTheme.Mocha -> if (dark) MochaDark else MochaLight
    AppTheme.Daylight -> if (dark) DaylightDark else DaylightLight
    AppTheme.Studio -> StudioDark
}

private fun shapesFor(theme: AppTheme): Shapes = when (theme) {
    AppTheme.Classic -> Shapes()
    AppTheme.Mocha -> Shapes(
        extraSmall = RoundedCornerShape(8.dp),
        small = RoundedCornerShape(12.dp),
        medium = RoundedCornerShape(16.dp),
        large = RoundedCornerShape(24.dp),
        extraLarge = RoundedCornerShape(32.dp),
    )
    AppTheme.Daylight -> Shapes(
        extraSmall = RoundedCornerShape(6.dp),
        small = RoundedCornerShape(10.dp),
        medium = RoundedCornerShape(16.dp),
        large = RoundedCornerShape(24.dp),
        extraLarge = RoundedCornerShape(28.dp),
    )
    AppTheme.Studio -> Shapes(
        extraSmall = RoundedCornerShape(2.dp),
        small = RoundedCornerShape(3.dp),
        medium = RoundedCornerShape(4.dp),
        large = RoundedCornerShape(6.dp),
        extraLarge = RoundedCornerShape(8.dp),
    )
}

private fun typographyFor(theme: AppTheme): Typography {
    val base = Typography()
    if (theme != AppTheme.Studio) return base
    // Studio: monospaced labels and readouts, like front-panel legends.
    val mono = FontFamily.Monospace
    return base.copy(
        labelLarge = base.labelLarge.copy(fontFamily = mono),
        labelMedium = base.labelMedium.copy(fontFamily = mono),
        labelSmall = base.labelSmall.copy(fontFamily = mono),
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

// ---------------------------------------------------------------- Classic (unchanged)

private val ClassicDark = darkColorScheme(
    primary = Color(0xFF68DBB7),
    secondary = Color(0xFFB1CCC1),
    background = Color(0xFF101412),
    surface = Color(0xFF101412),
    onSurface = Color(0xFFE0E4DF),
)

private val ClassicLight = lightColorScheme(
    primary = Color(0xFF006B55),
    secondary = Color(0xFF4E635B),
    surface = Color(0xFFF7F8F5),
    background = Color(0xFFF7F8F5),
    onSurface = Color(0xFF1A1C1A),
)

// ---------------------------------------------------------------- Mocha
// Catppuccin palette (MIT licence): Mocha for dark, Latte for light. Every role is set
// so no Material default colours leak through.

private val MochaDark = darkColorScheme(
    primary = Color(0xFF94E2D5),
    onPrimary = Color(0xFF11111B),
    primaryContainer = Color(0xFF2B4A47),
    onPrimaryContainer = Color(0xFFB5EDE4),
    inversePrimary = Color(0xFF0F7378),
    secondary = Color(0xFFA6E3A1),
    onSecondary = Color(0xFF11111B),
    secondaryContainer = Color(0xFF45475A),
    onSecondaryContainer = Color(0xFFCDD6F4),
    tertiary = Color(0xFFFAB387),
    onTertiary = Color(0xFF11111B),
    tertiaryContainer = Color(0xFF4A3A2E),
    onTertiaryContainer = Color(0xFFFAB387),
    background = Color(0xFF1E1E2E),
    onBackground = Color(0xFFCDD6F4),
    surface = Color(0xFF1E1E2E),
    onSurface = Color(0xFFCDD6F4),
    surfaceVariant = Color(0xFF313244),
    onSurfaceVariant = Color(0xFFA6ADC8),
    surfaceTint = Color(0xFF94E2D5),
    inverseSurface = Color(0xFFCDD6F4),
    inverseOnSurface = Color(0xFF1E1E2E),
    error = Color(0xFFF38BA8),
    onError = Color(0xFF11111B),
    errorContainer = Color(0xFF5A2A3A),
    onErrorContainer = Color(0xFFF5C2D0),
    outline = Color(0xFF6C7086),
    outlineVariant = Color(0xFF45475A),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF3A3B4E),
    surfaceDim = Color(0xFF11111B),
    surfaceContainerLowest = Color(0xFF11111B),
    surfaceContainerLow = Color(0xFF181825),
    surfaceContainer = Color(0xFF232334),
    surfaceContainerHigh = Color(0xFF2A2B3C),
    surfaceContainerHighest = Color(0xFF313244),
)

private val MochaLight = lightColorScheme(
    primary = Color(0xFF0F7378),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCDEBEC),
    onPrimaryContainer = Color(0xFF0A4F53),
    inversePrimary = Color(0xFF94E2D5),
    secondary = Color(0xFF2F7A1F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDCE0E8),
    onSecondaryContainer = Color(0xFF4C4F69),
    tertiary = Color(0xFFB4500A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFCE1CF),
    onTertiaryContainer = Color(0xFF6B2E05),
    background = Color(0xFFEFF1F5),
    onBackground = Color(0xFF4C4F69),
    surface = Color(0xFFEFF1F5),
    onSurface = Color(0xFF4C4F69),
    surfaceVariant = Color(0xFFDCE0E8),
    onSurfaceVariant = Color(0xFF5C5F77),
    surfaceTint = Color(0xFF0F7378),
    inverseSurface = Color(0xFF4C4F69),
    inverseOnSurface = Color(0xFFEFF1F5),
    error = Color(0xFFD20F39),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9D3DB),
    onErrorContainer = Color(0xFF7A0920),
    outline = Color(0xFF8C8FA1),
    outlineVariant = Color(0xFFCCD0DA),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFF5F6F9),
    surfaceDim = Color(0xFFDCE0E8),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFE9ECF1),
    surfaceContainer = Color(0xFFE6E9EF),
    surfaceContainerHigh = Color(0xFFDFE3EA),
    surfaceContainerHighest = Color(0xFFDCE0E8),
)

// ---------------------------------------------------------------- Daylight

private val DaylightLight = lightColorScheme(
    primary = Color(0xFF1F6B55),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE2EEE8),
    onPrimaryContainer = Color(0xFF0C3A2D),
    inversePrimary = Color(0xFF8FD3B8),
    secondary = Color(0xFF3E4C46),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD6E4DC),
    onSecondaryContainer = Color(0xFF17211D),
    tertiary = Color(0xFF9A5B12),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF6E3CC),
    onTertiaryContainer = Color(0xFF4A2A05),
    background = Color(0xFFF3F5F2),
    onBackground = Color(0xFF17211D),
    surface = Color(0xFFF3F5F2),
    onSurface = Color(0xFF17211D),
    surfaceVariant = Color(0xFFE4E9E5),
    onSurfaceVariant = Color(0xFF56635D),
    surfaceTint = Color(0xFF1F6B55),
    inverseSurface = Color(0xFF2B3631),
    inverseOnSurface = Color(0xFFEEF2EF),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    outline = Color(0xFF7A8781),
    outlineVariant = Color(0xFFDDE3DF),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFDCE2DE),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFAFBF9),
    surfaceContainer = Color(0xFFEEF2EF),
    surfaceContainerHigh = Color(0xFFE8EDEA),
    surfaceContainerHighest = Color(0xFFE2E8E4),
)

private val DaylightDark = darkColorScheme(
    primary = Color(0xFF7FD1B3),
    onPrimary = Color(0xFF00382A),
    primaryContainer = Color(0xFF1D4A3C),
    onPrimaryContainer = Color(0xFFBDEBD9),
    inversePrimary = Color(0xFF1F6B55),
    secondary = Color(0xFFB8CCC2),
    onSecondary = Color(0xFF233530),
    secondaryContainer = Color(0xFF2C3B35),
    onSecondaryContainer = Color(0xFFD4E6DC),
    tertiary = Color(0xFFE8B57A),
    onTertiary = Color(0xFF4A2A05),
    tertiaryContainer = Color(0xFF5E3E14),
    onTertiaryContainer = Color(0xFFF6E3CC),
    background = Color(0xFF111714),
    onBackground = Color(0xFFE3EAE6),
    surface = Color(0xFF111714),
    onSurface = Color(0xFFE3EAE6),
    surfaceVariant = Color(0xFF26302B),
    onSurfaceVariant = Color(0xFFA9B6AF),
    surfaceTint = Color(0xFF7FD1B3),
    inverseSurface = Color(0xFFE3EAE6),
    inverseOnSurface = Color(0xFF2B3631),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    outline = Color(0xFF77847D),
    outlineVariant = Color(0xFF3A4540),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF333E39),
    surfaceDim = Color(0xFF111714),
    surfaceContainerLowest = Color(0xFF0C110F),
    surfaceContainerLow = Color(0xFF161D1A),
    surfaceContainer = Color(0xFF1A221E),
    surfaceContainerHigh = Color(0xFF212A26),
    surfaceContainerHighest = Color(0xFF2B3530),
)

// ---------------------------------------------------------------- Studio (dark only)

private val StudioDark = darkColorScheme(
    primary = Color(0xFFFFB020),
    onPrimary = Color(0xFF0E0F10),
    primaryContainer = Color(0xFF3A2A08),
    onPrimaryContainer = Color(0xFFFFD58A),
    inversePrimary = Color(0xFF8A5A00),
    secondary = Color(0xFF5BE37D),
    onSecondary = Color(0xFF0E0F10),
    secondaryContainer = Color(0xFF2A2D30),
    onSecondaryContainer = Color(0xFFF2F2EE),
    tertiary = Color(0xFF5BC8FF),
    onTertiary = Color(0xFF0E0F10),
    tertiaryContainer = Color(0xFF12303F),
    onTertiaryContainer = Color(0xFFBDE6FF),
    background = Color(0xFF0E0F10),
    onBackground = Color(0xFFF2F2EE),
    surface = Color(0xFF0E0F10),
    onSurface = Color(0xFFF2F2EE),
    surfaceVariant = Color(0xFF1F2224),
    onSurfaceVariant = Color(0xFF9AA0A5),
    surfaceTint = Color(0xFF2A2D30),
    inverseSurface = Color(0xFFF2F2EE),
    inverseOnSurface = Color(0xFF17191B),
    error = Color(0xFFFF5A4F),
    onError = Color(0xFF0E0F10),
    errorContainer = Color(0xFF3D1412),
    onErrorContainer = Color(0xFFFFB4AE),
    outline = Color(0xFF4A4F54),
    outlineVariant = Color(0xFF2A2D30),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF2A2D30),
    surfaceDim = Color(0xFF0E0F10),
    surfaceContainerLowest = Color(0xFF0B0C0D),
    surfaceContainerLow = Color(0xFF131516),
    surfaceContainer = Color(0xFF17191B),
    surfaceContainerHigh = Color(0xFF1D2022),
    surfaceContainerHighest = Color(0xFF23272A),
)
