package com.svetlio.audiofreedom

import org.junit.Assert.*
import org.junit.Test

class ThemesTest {
    @Test fun ordinaryThemesFollowTheChosenMode() {
        for (theme in listOf(AppTheme.Classic, AppTheme.Mocha, AppTheme.Daylight)) {
            assertFalse(isDark(ThemePreference.Light, theme, true))
            assertTrue(isDark(ThemePreference.Dark, theme, false))
            assertFalse(isDark(ThemePreference.System, theme, false))
            assertTrue(isDark(ThemePreference.System, theme, true))
        }
    }

    @Test fun studioIsAlwaysDark() {
        for (mode in ThemePreference.entries) for (systemDark in listOf(false, true)) {
            assertTrue(isDark(mode, AppTheme.Studio, systemDark))
        }
    }

    @Test fun pickerSwatchesMatchTheThemePalette() {
        for (theme in AppTheme.entries) for (dark in listOf(false, true)) {
            val scheme = colorSchemeFor(theme, dark || !theme.supportsLight)
            assertEquals(listOf(scheme.background, scheme.surfaceContainerHighest, scheme.primary), themeSwatch(theme, dark))
        }
    }

    @Test fun themesOfferDistinctPalettes() {
        assertEquals(AppTheme.entries.size, AppTheme.entries.map { themeSwatch(it, true) }.toSet().size)
        assertEquals(themeSwatch(AppTheme.Studio, true), themeSwatch(AppTheme.Studio, false))
    }
}
