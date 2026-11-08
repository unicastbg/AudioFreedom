package com.svetlio.audiofreedom

import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ProfileManagerUiTest {
    @get:Rule val compose = createComposeRule()
    private val initial = (1..5).map { AudioFreedomProfile("$it", "Sound $it", AudioFreedomSettings(preampMillibels = -it*100)) }

    @Test fun fiveProfilesCanBeMovedTogetherThenGroupRenamedAndRemoved() {
        val profiles = mutableStateOf(initial)
        compose.setContent { AudioFreedomTheme(ThemePreference.Dark, AppTheme.Mocha, controlSystemBars = false) {
            Surface { ProfileManagerScreen(profiles.value, "1", { action ->
                profiles.value = planProfileMetadata(profiles.value, action); null
            }) }
        } }
        compose.onNodeWithContentDescription("Select all results").performClick()
        compose.onNodeWithText("5 selected").assertExists()
        compose.onNodeWithText("Move to group").performClick()
        compose.onNodeWithText("Group", substring = false).performTextInput("Sony Amp")
        compose.onNodeWithText("Move", substring = false).performClick()
        compose.runOnIdle {
            assertEquals(List(5) { "Sony Amp" }, profiles.value.map { it.deviceGroup })
            assertEquals(initial.map { it.settings }, profiles.value.map { it.settings })
        }
        compose.onNodeWithContentDescription("Group actions: Sony Amp").performClick()
        compose.onNodeWithText("Rename group").performClick()
        compose.onNodeWithText("Name", substring = false).performTextReplacement("HiFi")
        compose.onNodeWithText("Save", substring = false).performClick()
        compose.onNodeWithContentDescription("Group actions: HiFi").performClick()
        compose.onNodeWithText("Remove group").performClick()
        compose.onNodeWithText("Remove group", substring = false).performClick()
        compose.runOnIdle { assertTrue(profiles.value.all { it.deviceGroup.isEmpty() }) }
        val i = InstrumentationRegistry.getInstrumentation()
        File(i.targetContext.cacheDir, "profile-manager.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun managementAndQuickPickerRemainAvailableAcrossThemes() {
        val theme = mutableStateOf(AppTheme.Classic)
        var opened = 0
        compose.setContent { AudioFreedomTheme(ThemePreference.Dark, theme.value, controlSystemBars = false) {
            ProfileBrowserRow(initial, "1", true, {}, {}, {}, { opened++ })
        } }
        for (value in AppTheme.entries) {
            compose.runOnIdle { theme.value = value }
            compose.onNodeWithContentDescription("Manage profiles").assertIsDisplayed().performClick()
            compose.onNodeWithContentDescription("Choose profile").performClick()
            compose.onNodeWithText("Manage", substring = false).performClick()
        }
        compose.runOnIdle { assertEquals(AppTheme.entries.size * 2, opened) }
    }

    @Test fun saveDialogOffersExistingGroupsAndSeparateCreateAndUpdateModes() {
        val grouped = initial.map { it.copy(deviceGroup = "Sony Amp") }
        val id = mutableStateOf<String?>(null)
        val name = mutableStateOf("New sound")
        val group = mutableStateOf("")
        compose.setContent { AudioFreedomTheme(ThemePreference.System, controlSystemBars = false) {
            ProfileSaveDialog("Save profile", grouped, id.value, "1", name.value, group.value,
                { selected -> id.value = selected; grouped.firstOrNull { it.id == selected }?.let {
                    name.value = it.name; group.value = it.deviceGroup } },
                { name.value = it }, { group.value = it }, {}, {})
        } }
        compose.onNodeWithText("Create profile").assertExists()
        compose.onNodeWithContentDescription("Choose group").performClick()
        compose.onNodeWithText("Sony Amp", substring = false).performClick()
        compose.runOnIdle { assertEquals("Sony Amp", group.value) }
        compose.onNodeWithText("Update existing").performClick()
        compose.onNodeWithText("Update profile", substring = false).assertExists()
        compose.runOnIdle { assertEquals("1", id.value) }
        compose.onNodeWithText("Save as new").performClick()
        compose.onNodeWithText("Create profile").assertIsNotEnabled()
    }
}
