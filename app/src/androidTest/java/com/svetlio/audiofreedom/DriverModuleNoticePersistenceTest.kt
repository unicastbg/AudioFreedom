package com.svetlio.audiofreedom

import android.content.Context
import android.content.ContextWrapper
import android.provider.Settings
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class DriverModuleNoticePersistenceTest {
    @get:Rule val compose = createComposeRule()
    private val target = InstrumentationRegistry.getInstrumentation().targetContext
    private val context = object : ContextWrapper(target) {
        override fun getSharedPreferences(name: String, mode: Int) =
            super.getSharedPreferences(
                if (name == "driver_module_notices") "test_driver_module_notices" else name, mode)
    }
    private val prefs get() = context.getSharedPreferences("driver_module_notices", Context.MODE_PRIVATE)

    @Before fun setUp() { prefs.edit().clear().commit() }
    @After fun tearDown() { prefs.edit().clear().commit() }

    @Test fun noticeAndPendingRestartSurviveReloadAndClearAfterAnotherBoot() {
        DriverModuleNoticeStore.acknowledge(context, 43)
        assertEquals(43, DriverModuleNoticeStore.notifiedVersionCode(context))
        DriverModuleNoticeStore.installed(context, "KernelSU")
        assertEquals("KernelSU", DriverModuleNoticeStore.pendingRestart(context))
        val boot = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT)
        prefs.edit().putInt("pending_boot_count", boot + 1).commit()
        assertNull(DriverModuleNoticeStore.pendingRestart(context))
        assertEquals(-1, prefs.getInt("pending_boot_count", -1))
    }

    @Test fun pendingUpdateShowsRestartInsteadOfUpdateAndAllowsDeferring() {
        DriverModuleNoticeStore.installed(context, "KernelSU")
        compose.setContent {
            val registryOwner = checkNotNull(LocalActivityResultRegistryOwner.current)
            CompositionLocalProvider(LocalContext provides context,
                LocalActivityResultRegistryOwner provides registryOwner) {
                AudioFreedomTheme(ThemePreference.Light, controlSystemBars = false) {
                    SettingsScreen(
                        route = AudioRoute("phone-speaker", "Phone speaker", AudioRouteKind.PhoneSpeaker),
                        profiles = emptyList(), preferences = AppPreferences(), assignedProfileId = null,
                        onPreferencesChanged = {}, onProfileAssigned = {},
                        onOpenAssistantSettings = {}, onOpenMainPageSettings = {},
                        openDriverUpdates = true,
                    )
                }
            }
        }
        compose.onNodeWithText("Restart phone").assertIsDisplayed().performClick()
        compose.onNodeWithText("Restart phone?").assertIsDisplayed()
        compose.onNodeWithText("Later").performClick()
        compose.onNodeWithText("Restart phone").assertIsDisplayed()
        compose.onNodeWithText("Installed with KernelSU; reboot required").assertIsDisplayed()
        assertEquals("KernelSU", DriverModuleNoticeStore.pendingRestart(context))
        val updateButton = compose.onNodeWithText("Check updates").performScrollTo()
        updateButton.assertIsDisplayed()
        assertEquals(compose.onNodeWithTag("update-check-row").fetchSemanticsNode().boundsInRoot.center.x,
            updateButton.fetchSemanticsNode().boundsInRoot.center.x, 1F)
    }

    @Test fun extendedEqDriverWarningOnlyAppearsForAnOldDriver() {
        val outdated = mutableStateOf(false)
        compose.setContent {
            AudioFreedomTheme(ThemePreference.Light, controlSystemBars = false) {
                EqualizerRangeControl(
                    preferences = AppPreferences(extendedEqualizerRange = true,
                        extendedEqualizerRangeAcknowledged = true),
                    driverUpdateRequired = outdated.value,
                    onPreferencesChanged = {},
                )
            }
        }
        val warning = compose.onNodeWithText("Your driver is older than 0.9.8-beta", substring = true)
        warning.assertDoesNotExist()
        compose.runOnIdle { outdated.value = true }
        warning.assertIsDisplayed()
        compose.runOnIdle { outdated.value = false }
        warning.assertDoesNotExist()
    }
}
