package com.svetlio.audiofreedom

import org.junit.Assert.*
import org.junit.Test

class ThemesTest {
    @Test fun ordinaryThemesFollowTheChosenMode() {
        for (theme in AppTheme.entries.filter { it.modes == ThemeModes.Both }) {
            assertFalse(isDark(ThemePreference.Light, theme, true))
            assertTrue(isDark(ThemePreference.Dark, theme, false))
            assertFalse(isDark(ThemePreference.System, theme, false))
            assertTrue(isDark(ThemePreference.System, theme, true))
        }
    }

    @Test fun fixedShadeThemesIgnoreTheChosenMode() {
        val darkThemes = listOf(AppTheme.Studio, AppTheme.MediaPlayer, AppTheme.OledBlack,
            AppTheme.Phosphor, AppTheme.Valve, AppTheme.Neon)
        val lightThemes = listOf(AppTheme.Paper, AppTheme.CassetteDeck)
        for (mode in ThemePreference.entries) for (systemDark in listOf(false, true)) {
            for (theme in darkThemes) assertTrue(theme.label, isDark(mode, theme, systemDark))
            for (theme in lightThemes) assertFalse(theme.label, isDark(mode, theme, systemDark))
        }
    }

    @Test fun specializedThemesSelectTheirMetersAndLayouts() {
        assertEquals(MeterLook.Trace, AppTheme.Phosphor.look.meter)
        assertEquals(MeterLook.Vu, AppTheme.Valve.look.meter)
        assertEquals(MeterLook.Glow, AppTheme.Neon.look.meter)
        assertEquals(MeterLook.Lcd, AppTheme.CassetteDeck.look.meter)
        assertEquals(LayoutStyle.Keys, AppTheme.CassetteDeck.layoutStyle)
        assertEquals(LayoutStyle.Classic, AppTheme.Classic.layoutStyle)
        assertTrue(AppTheme.entries.filter { it != AppTheme.Classic }.all { it.layoutStyle.modern })
    }

    @Test fun newFixedPalettesAreDistinctAndOledUsesTrueBlack() {
        val palettes = listOf(MediaPlayerDark, OledBlackDark, PhosphorDark,
            PaperLight, ValveDark, NeonDark, CassetteLight)
        assertEquals(palettes.size, palettes.map {
            listOf(it.background, it.surfaceContainerHighest, it.primary)
        }.toSet().size)
        assertEquals(androidx.compose.ui.graphics.Color.Black, OledBlackDark.background)
    }
}
