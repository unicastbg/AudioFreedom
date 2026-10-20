package com.svetlio.audiofreedom

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Colour schemes for the second batch of themes. Every Material role is set, so no
// default (lavender) colour can leak into a slider, chip or switch. Dynamic has no entry
// here: Android builds that one from the wallpaper (see colorSchemeFor in Themes.kt).
//
// Role conventions the redesigned screens rely on:
//   primary              accent: power button, switches, curve, values
//   secondary            "OK" status colour and LED colour
//   tertiary             warnings (headroom, limiting)
//   surfaceContainerLow  processing card / sheets
//   surfaceContainerLowest  effect cards in the card layout
//   surfaceContainerHighest effect tiles / keys

private val Clear = Color(0x00000000)

// ------------------------------------------------------------------ Media Player
// The player app's own colours, so the two apps read as one product.

internal val MediaPlayerDark: ColorScheme = darkColorScheme(
    primary = Color(0xFFFF8C00),
    onPrimary = Color(0xFF201400),
    primaryContainer = Color(0xFF3A2400),
    onPrimaryContainer = Color(0xFFFFD2A1),
    inversePrimary = Color(0xFF8A4B00),
    secondary = Color(0xFFFF8C00),
    onSecondary = Color(0xFF201400),
    secondaryContainer = Color(0xFF2A2A2A),
    onSecondaryContainer = Color(0xFFECECEC),
    tertiary = Color(0xFFFFD166),
    onTertiary = Color(0xFF201400),
    tertiaryContainer = Color(0xFF3A2E10),
    onTertiaryContainer = Color(0xFFFFE6A3),
    background = Color(0xFF0E0E0E),
    onBackground = Color(0xFFECECEC),
    surface = Color(0xFF0E0E0E),
    onSurface = Color(0xFFECECEC),
    surfaceVariant = Color(0xFF1F1F1F),
    onSurfaceVariant = Color(0xFFB6B6B6),
    surfaceTint = Clear,
    inverseSurface = Color(0xFFECECEC),
    inverseOnSurface = Color(0xFF161616),
    error = Color(0xFFFF5449),
    onError = Color(0xFF2B0000),
    errorContainer = Color(0xFF5C1410),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF3A3A3A),
    outlineVariant = Color(0xFF262626),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF2A2A2A),
    surfaceDim = Color(0xFF0E0E0E),
    surfaceContainerLowest = Color(0xFF161616),
    surfaceContainerLow = Color(0xFF161616),
    surfaceContainer = Color(0xFF1A1A1A),
    surfaceContainerHigh = Color(0xFF1D1D1D),
    surfaceContainerHighest = Color(0xFF1F1F1F),
)

// ------------------------------------------------------------------ OLED Black
// True black: on an OLED panel those pixels are simply off.

internal val OledBlackDark: ColorScheme = darkColorScheme(
    primary = Color(0xFF00E5FF),
    onPrimary = Color(0xFF001F24),
    primaryContainer = Color(0xFF002E35),
    onPrimaryContainer = Color(0xFF9EF3FF),
    inversePrimary = Color(0xFF00687A),
    secondary = Color(0xFF00E5FF),
    onSecondary = Color(0xFF001F24),
    secondaryContainer = Color(0xFF141414),
    onSecondaryContainer = Color(0xFFEDEDED),
    tertiary = Color(0xFFFFC24A),
    onTertiary = Color(0xFF261800),
    tertiaryContainer = Color(0xFF3A2A00),
    onTertiaryContainer = Color(0xFFFFE1A1),
    background = Color(0xFF000000),
    onBackground = Color(0xFFEDEDED),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFEDEDED),
    surfaceVariant = Color(0xFF141414),
    onSurfaceVariant = Color(0xFF8C8C8C),
    surfaceTint = Clear,
    inverseSurface = Color(0xFFEDEDED),
    inverseOnSurface = Color(0xFF000000),
    error = Color(0xFFFF5C7A),
    onError = Color(0xFF2A0010),
    errorContainer = Color(0xFF4A0018),
    onErrorContainer = Color(0xFFFFD9DF),
    outline = Color(0xFF3A3A3A),
    outlineVariant = Color(0xFF1F1F1F),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF1F1F1F),
    surfaceDim = Color(0xFF000000),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF000000),
    surfaceContainer = Color(0xFF0A0A0A),
    surfaceContainerHigh = Color(0xFF111111),
    surfaceContainerHighest = Color(0xFF141414),
)

// ------------------------------------------------------------------ Phosphor
// Green phosphor on a scope screen.

internal val PhosphorDark: ColorScheme = darkColorScheme(
    primary = Color(0xFF39FF6A),
    onPrimary = Color(0xFF002910),
    primaryContainer = Color(0xFF0C2412),
    onPrimaryContainer = Color(0xFF9CFFB0),
    inversePrimary = Color(0xFF1B7A35),
    secondary = Color(0xFF39FF6A),
    onSecondary = Color(0xFF002910),
    secondaryContainer = Color(0xFF0F2214),
    onSecondaryContainer = Color(0xFF9CFFB0),
    tertiary = Color(0xFFFFC233),
    onTertiary = Color(0xFF2A1C00),
    tertiaryContainer = Color(0xFF3A2A00),
    onTertiaryContainer = Color(0xFFFFE19A),
    background = Color(0xFF050A06),
    onBackground = Color(0xFF9CFFB0),
    surface = Color(0xFF050A06),
    onSurface = Color(0xFF9CFFB0),
    surfaceVariant = Color(0xFF0F2214),
    onSurfaceVariant = Color(0xFF5FA56E),
    surfaceTint = Clear,
    inverseSurface = Color(0xFF9CFFB0),
    inverseOnSurface = Color(0xFF050A06),
    error = Color(0xFFFF5E5E),
    onError = Color(0xFF2A0000),
    errorContainer = Color(0xFF4A0F0F),
    onErrorContainer = Color(0xFFFFD4D4),
    outline = Color(0xFF2E6B3C),
    outlineVariant = Color(0xFF1C4426),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF163020),
    surfaceDim = Color(0xFF050A06),
    surfaceContainerLowest = Color(0xFF040805),
    surfaceContainerLow = Color(0xFF08120A),
    surfaceContainer = Color(0xFF08120A),
    surfaceContainerHigh = Color(0xFF0C1A0F),
    surfaceContainerHighest = Color(0xFF0F2214),
)

// ------------------------------------------------------------------ Paper
// Warm paper, ink-black text, one terracotta accent.

internal val PaperLight: ColorScheme = lightColorScheme(
    primary = Color(0xFFB4532A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF6DCCF),
    onPrimaryContainer = Color(0xFF4A1C08),
    inversePrimary = Color(0xFFF2A07E),
    secondary = Color(0xFF4B6B3A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE2D9C7),
    onSecondaryContainer = Color(0xFF1C1B19),
    tertiary = Color(0xFF2F5D8A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD5E3F2),
    onTertiaryContainer = Color(0xFF0E2A45),
    background = Color(0xFFF6F1E7),
    onBackground = Color(0xFF1C1B19),
    surface = Color(0xFFF6F1E7),
    onSurface = Color(0xFF1C1B19),
    surfaceVariant = Color(0xFFE8E0D0),
    onSurfaceVariant = Color(0xFF5E5A52),
    surfaceTint = Clear,
    inverseSurface = Color(0xFF32302C),
    inverseOnSurface = Color(0xFFF6F1E7),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    outline = Color(0xFF8A8478),
    outlineVariant = Color(0xFFDDD5C6),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFBF8F2),
    surfaceDim = Color(0xFFE2D9C7),
    surfaceContainerLowest = Color(0xFFFBF8F2),
    surfaceContainerLow = Color(0xFFF1EBDF),
    surfaceContainer = Color(0xFFEDE6D8),
    surfaceContainerHigh = Color(0xFFE8E0D0),
    surfaceContainerHighest = Color(0xFFE2D9C7),
)

// ------------------------------------------------------------------ Valve
// Walnut, cream and the amber glow of a warm tube.

internal val ValveDark: ColorScheme = darkColorScheme(
    primary = Color(0xFFFFB347),
    onPrimary = Color(0xFF2A1C13),
    primaryContainer = Color(0xFF4A3014),
    onPrimaryContainer = Color(0xFFFFD9A6),
    inversePrimary = Color(0xFF8A5A10),
    secondary = Color(0xFFFFB347),
    onSecondary = Color(0xFF2A1C13),
    secondaryContainer = Color(0xFF3A2A1D),
    onSecondaryContainer = Color(0xFFF3E6CF),
    tertiary = Color(0xFFE8875A),
    onTertiary = Color(0xFF2A1408),
    tertiaryContainer = Color(0xFF4A2A18),
    onTertiaryContainer = Color(0xFFFFD2BB),
    background = Color(0xFF1E140E),
    onBackground = Color(0xFFF3E6CF),
    surface = Color(0xFF1E140E),
    onSurface = Color(0xFFF3E6CF),
    surfaceVariant = Color(0xFF3A2A1D),
    onSurfaceVariant = Color(0xFFBFA88A),
    surfaceTint = Clear,
    inverseSurface = Color(0xFFF3E6CF),
    inverseOnSurface = Color(0xFF2A1C13),
    error = Color(0xFFE0533F),
    onError = Color(0xFF2A0800),
    errorContainer = Color(0xFF5A1E14),
    onErrorContainer = Color(0xFFFFD6CC),
    outline = Color(0xFF6B5440),
    outlineVariant = Color(0xFF4A3424),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF4A3424),
    surfaceDim = Color(0xFF1E140E),
    surfaceContainerLowest = Color(0xFF2A1C13),
    surfaceContainerLow = Color(0xFF2A1C13),
    surfaceContainer = Color(0xFF2E1F15),
    surfaceContainerHigh = Color(0xFF33231A),
    surfaceContainerHighest = Color(0xFF3A2A1D),
)

// ------------------------------------------------------------------ Neon
// Deep purple night, magenta and cyan light.

internal val NeonDark: ColorScheme = darkColorScheme(
    primary = Color(0xFFFF2BD6),
    onPrimary = Color(0xFF2A0024),
    primaryContainer = Color(0xFF4A0A40),
    onPrimaryContainer = Color(0xFFFFD6F6),
    inversePrimary = Color(0xFF9A1080),
    secondary = Color(0xFF23F0FF),
    onSecondary = Color(0xFF00262A),
    secondaryContainer = Color(0xFF2A1758),
    onSecondaryContainer = Color(0xFFF5E9FF),
    tertiary = Color(0xFFFFE45E),
    onTertiary = Color(0xFF2A2200),
    tertiaryContainer = Color(0xFF4A3F00),
    onTertiaryContainer = Color(0xFFFFF3B0),
    background = Color(0xFF0D0221),
    onBackground = Color(0xFFF5E9FF),
    surface = Color(0xFF0D0221),
    onSurface = Color(0xFFF5E9FF),
    surfaceVariant = Color(0xFF2A1758),
    onSurfaceVariant = Color(0xFFB9A6D9),
    surfaceTint = Clear,
    inverseSurface = Color(0xFFF5E9FF),
    inverseOnSurface = Color(0xFF0D0221),
    error = Color(0xFFFF5470),
    onError = Color(0xFF2A0010),
    errorContainer = Color(0xFF5A0A24),
    onErrorContainer = Color(0xFFFFD9E0),
    outline = Color(0xFF4A3480),
    outlineVariant = Color(0xFF3B2470),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF3B2470),
    surfaceDim = Color(0xFF0D0221),
    surfaceContainerLowest = Color(0xFF140632),
    surfaceContainerLow = Color(0xFF140632),
    surfaceContainer = Color(0xFF1A0B3A),
    surfaceContainerHigh = Color(0xFF20104A),
    surfaceContainerHighest = Color(0xFF2A1758),
)

// ------------------------------------------------------------------ Cassette Deck
// Beige 80s deck panel. The amber LCD colours live with the LCD itself (ThemeParts.kt).

internal val CassetteLight: ColorScheme = lightColorScheme(
    primary = Color(0xFFC8372D),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF5D3CC),
    onPrimaryContainer = Color(0xFF4A0F0A),
    inversePrimary = Color(0xFFFF8A7A),
    secondary = Color(0xFF8A3A12),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD6CBB3),
    onSecondaryContainer = Color(0xFF2A2620),
    tertiary = Color(0xFF8A5A00),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF7E2B0),
    onTertiaryContainer = Color(0xFF3A2A00),
    background = Color(0xFFE8DFCC),
    onBackground = Color(0xFF2A2620),
    surface = Color(0xFFE8DFCC),
    onSurface = Color(0xFF2A2620),
    surfaceVariant = Color(0xFFD6CBB3),
    onSurfaceVariant = Color(0xFF5C5446),
    surfaceTint = Clear,
    inverseSurface = Color(0xFF2A2620),
    inverseOnSurface = Color(0xFFE8DFCC),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    outline = Color(0xFF8F8470),
    outlineVariant = Color(0xFFC9BC9F),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFF2E8D4),
    surfaceDim = Color(0xFFD6CBB3),
    surfaceContainerLowest = Color(0xFFF2E8D4),
    surfaceContainerLow = Color(0xFFDED4BE),
    surfaceContainer = Color(0xFFD6CBB3),
    surfaceContainerHigh = Color(0xFFCFC3A8),
    surfaceContainerHighest = Color(0xFFC9BC9F),
)
