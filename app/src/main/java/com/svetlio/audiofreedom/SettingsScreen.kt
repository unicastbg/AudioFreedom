package com.svetlio.audiofreedom

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.BluetoothAudio
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DevicesOther
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

private sealed interface UpdateSectionState {
    data object Idle : UpdateSectionState
    data object Checking : UpdateSectionState
    data class Available(val version: String, val releaseUrl: String) : UpdateSectionState
    data class Current(val latestVersion: String) : UpdateSectionState
    data object Failed : UpdateSectionState
}

private sealed interface DriverModuleSectionState {
    data object Inspecting : DriverModuleSectionState
    data object Missing : DriverModuleSectionState
    data object RootUnavailable : DriverModuleSectionState
    data class Installed(val info: DriverModuleInfo) : DriverModuleSectionState
    data class Failed(val message: String) : DriverModuleSectionState
}

private sealed interface DriverModuleOperationState {
    data object Idle : DriverModuleOperationState
    data class Working(val label: String) : DriverModuleOperationState
    data class RebootRequired(val rootManager: String) : DriverModuleOperationState
    data class Failed(val message: String) : DriverModuleOperationState
}

private sealed interface PendingDriverModuleAction {
    val title: String
    val description: String

    data class Bundled(
        override val title: String,
        override val description: String,
    ) : PendingDriverModuleAction

    data class GitHub(
        val release: DriverModuleRelease,
        override val title: String,
        override val description: String,
    ) : PendingDriverModuleAction
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun SettingsScreen(
    route: AudioRoute,
    profiles: List<AudioFreedomProfile>,
    preferences: AppPreferences,
    assignedProfileId: String?,
    onPreferencesChanged: (AppPreferences) -> Unit,
    onProfileAssigned: (String?) -> Unit,
    onOpenAssistantSettings: () -> Unit,
    onOpenMainPageSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var profileMenuExpanded by remember { mutableStateOf(false) }
    var updateState by remember { mutableStateOf<UpdateSectionState>(UpdateSectionState.Idle) }
    var moduleState by remember {
        mutableStateOf<DriverModuleSectionState>(DriverModuleSectionState.Inspecting)
    }
    var moduleUpdateState by remember { mutableStateOf<DriverModuleUpdateResult?>(null) }
    var moduleUpdateChecking by remember { mutableStateOf(false) }
    var moduleOperationState by remember {
        mutableStateOf<DriverModuleOperationState>(DriverModuleOperationState.Idle)
    }
    var pendingModuleAction by remember { mutableStateOf<PendingDriverModuleAction?>(null) }
    val updateScope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val assignedProfile = profiles.firstOrNull { it.id == assignedProfileId }
    val bundledModule = remember(context) { DriverModuleManager.bundled(context) }

    fun refreshModuleStatus() {
        if (moduleOperationState is DriverModuleOperationState.Working) return
        moduleState = DriverModuleSectionState.Inspecting
        updateScope.launch {
            moduleState = when (val result = DriverModuleManager.probeInstalled(context)) {
                is DriverModuleProbeResult.Installed ->
                    DriverModuleSectionState.Installed(result.info)
                DriverModuleProbeResult.Missing -> DriverModuleSectionState.Missing
                DriverModuleProbeResult.RootUnavailable ->
                    DriverModuleSectionState.RootUnavailable
                is DriverModuleProbeResult.Failed ->
                    DriverModuleSectionState.Failed(result.message)
            }
        }
    }

    fun checkAllUpdates() {
        if (updateState == UpdateSectionState.Checking || moduleUpdateChecking) return
        val currentDriverVersion = when (val state = moduleState) {
            is DriverModuleSectionState.Installed -> state.info.version
            else -> bundledModule?.info?.version
        }
        updateState = UpdateSectionState.Checking
        moduleUpdateState = null
        moduleUpdateChecking = true
        updateScope.launch {
            try {
                val appResult = async { AppUpdateChecker.check(BuildConfig.VERSION_NAME) }
                val moduleResult = currentDriverVersion?.let { version ->
                    async { DriverModuleManager.checkForUpdate(version) }
                }
                updateState = when (val result = appResult.await()) {
                    is UpdateCheckResult.UpdateAvailable -> UpdateSectionState.Available(
                        version = result.version,
                        releaseUrl = result.releaseUrl,
                    )
                    is UpdateCheckResult.UpToDate ->
                        UpdateSectionState.Current(result.latestVersion)
                    UpdateCheckResult.Failed -> UpdateSectionState.Failed
                }
                moduleUpdateState = moduleResult?.await() ?: DriverModuleUpdateResult.Failed
            } catch (_: RuntimeException) {
                updateState = UpdateSectionState.Failed
                moduleUpdateState = DriverModuleUpdateResult.Failed
            } finally {
                moduleUpdateChecking = false
            }
        }
    }

    fun installDriverModule(action: PendingDriverModuleAction) {
        pendingModuleAction = null
        moduleOperationState = DriverModuleOperationState.Working(action.title)
        updateScope.launch {
            val result = when (action) {
                is PendingDriverModuleAction.Bundled ->
                    DriverModuleManager.installBundled(context)
                is PendingDriverModuleAction.GitHub ->
                    DriverModuleManager.downloadAndInstall(context, action.release)
            }
            moduleOperationState = when (result) {
                is DriverModuleInstallResult.Success ->
                    DriverModuleOperationState.RebootRequired(result.rootManager)
                is DriverModuleInstallResult.Failed ->
                    DriverModuleOperationState.Failed(result.message)
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshModuleStatus()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        SettingsSectionTitle("Audio output")
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AudioRouteIcon(route, contentDescription = null)
            Column(modifier = Modifier.padding(start = 16.dp).weight(1F)) {
                Text(route.label, style = MaterialTheme.typography.titleMedium)
                Text(
                    route.kind.displayName,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        SettingsSectionTitle("Device profiles")
        SettingsSwitchRow(
            title = "Automatic profiles",
            summary = "Apply the assigned profile when this output connects",
            checked = preferences.automaticDeviceProfiles,
            onCheckedChange = {
                onPreferencesChanged(preferences.copy(automaticDeviceProfiles = it))
            },
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1F)) {
                Text("Profile for ${route.label}", style = MaterialTheme.typography.bodyLarge)
                Text(
                    assignedProfile?.name ?: "Not assigned",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            androidx.compose.foundation.layout.Box {
                TextButton(onClick = { profileMenuExpanded = true }) {
                    Text("Choose")
                    Icon(Icons.Rounded.ExpandMore, contentDescription = null)
                }
                DropdownMenu(
                    expanded = profileMenuExpanded,
                    onDismissRequest = { profileMenuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("No profile") },
                        onClick = {
                            profileMenuExpanded = false
                            onProfileAssigned(null)
                        },
                    )
                    profiles.forEach { profile ->
                        DropdownMenuItem(
                            text = { Text(profile.name) },
                            onClick = {
                                profileMenuExpanded = false
                                onProfileAssigned(profile.id)
                            },
                        )
                    }
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        SettingsSectionTitle("Assistant")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenAssistantSettings)
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.SmartToy, contentDescription = null)
            Column(modifier = Modifier.padding(start = 16.dp).weight(1F)) {
                Text("Local assistant", style = MaterialTheme.typography.bodyLarge)
                Text(
                    if (preferences.assistantEnabled) "Enabled" else "Disabled",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Icon(Icons.Rounded.ChevronRight, contentDescription = null)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        SettingsSectionTitle("Notification")
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Lock, contentDescription = null)
            Column(modifier = Modifier.padding(start = 16.dp).weight(1F)) {
                Text("Processing status", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Required while system-wide processing is active",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        SettingsSwitchRow(
            title = "Show connected device",
            summary = route.label,
            checked = preferences.showDeviceInNotification,
            onCheckedChange = {
                onPreferencesChanged(preferences.copy(showDeviceInNotification = it))
            },
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        SettingsSectionTitle("Appearance")
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenMainPageSettings)
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Tune, contentDescription = null)
            Column(modifier = Modifier.padding(start = 16.dp).weight(1F)) {
                Text("Main page functions", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "${MainPageFunction.entries.size - preferences.hiddenMainPageFunctions.size} of " +
                        "${MainPageFunction.entries.size} shown",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Icon(Icons.Rounded.ChevronRight, contentDescription = null)
        }
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            ThemePreference.entries.forEachIndexed { index, theme ->
                SegmentedButton(
                    selected = preferences.theme == theme,
                    onClick = { onPreferencesChanged(preferences.copy(theme = theme)) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = ThemePreference.entries.size,
                    ),
                ) {
                    Text(theme.label)
                }
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(top = 24.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
        SettingsSectionTitle("Updates")
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.SystemUpdate, contentDescription = null)
            Column(modifier = Modifier.padding(start = 16.dp).weight(1F)) {
                Text("AudioFreedom ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = when (val state = updateState) {
                        UpdateSectionState.Idle -> "Check GitHub for a newer release"
                        UpdateSectionState.Checking -> "Checking GitHub"
                        is UpdateSectionState.Available -> "Version ${state.version} is available"
                        is UpdateSectionState.Current -> "Up to date"
                        UpdateSectionState.Failed -> "Could not check for updates"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            when (val state = updateState) {
                is UpdateSectionState.Available -> TextButton(
                    onClick = { uriHandler.openUri(state.releaseUrl) },
                ) {
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null)
                    Text("View")
                }
                UpdateSectionState.Checking,
                UpdateSectionState.Failed,
                UpdateSectionState.Idle,
                is UpdateSectionState.Current,
                -> Unit
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Build, contentDescription = null)
            Column(modifier = Modifier.padding(start = 16.dp).weight(1F)) {
                Text("Driver module", style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = driverModuleSummary(
                        state = moduleState,
                        bundled = bundledModule,
                        update = moduleUpdateState,
                        operation = moduleOperationState,
                        checkingUpdate = moduleUpdateChecking,
                    ),
                    color = when (moduleOperationState) {
                        is DriverModuleOperationState.Failed -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            when (moduleOperationState) {
                is DriverModuleOperationState.Working -> CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                )
                else -> {
                    val action = preferredModuleAction(
                        state = moduleState,
                        bundled = bundledModule,
                        update = moduleUpdateState,
                    )
                    if (action != null) {
                        TextButton(onClick = { pendingModuleAction = action }) {
                            Text(action.title)
                        }
                    } else {
                        TextButton(onClick = ::refreshModuleStatus) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null)
                            Text("Retry")
                        }
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(
                onClick = ::checkAllUpdates,
                enabled = updateState != UpdateSectionState.Checking && !moduleUpdateChecking,
            ) {
                if (updateState == UpdateSectionState.Checking || moduleUpdateChecking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Text("Checking updates")
                } else {
                    Icon(Icons.Rounded.Refresh, contentDescription = null)
                    Text("Check updates")
                }
            }
        }
        Spacer(Modifier.height(32.dp))
    }

    pendingModuleAction?.let { action ->
        AlertDialog(
            onDismissRequest = { pendingModuleAction = null },
            title = { Text("${action.title} driver module?") },
            text = {
                Text(
                    "${action.description} AudioFreedom will request root access. " +
                        "A reboot is required before the new driver becomes active.",
                )
            },
            confirmButton = {
                TextButton(onClick = { installDriverModule(action) }) {
                    Text(action.title)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingModuleAction = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}

private fun driverModuleSummary(
    state: DriverModuleSectionState,
    bundled: BundledDriverModule?,
    update: DriverModuleUpdateResult?,
    operation: DriverModuleOperationState,
    checkingUpdate: Boolean,
): String = when {
    checkingUpdate -> "Checking GitHub for a driver update"
    else -> when (operation) {
        is DriverModuleOperationState.Working -> "${operation.label} in progress"
        is DriverModuleOperationState.RebootRequired ->
            "Installed with ${operation.rootManager}; reboot required"
        is DriverModuleOperationState.Failed -> operation.message
        DriverModuleOperationState.Idle -> when (update) {
            is DriverModuleUpdateResult.Available ->
                "Version ${update.release.version} is available"
            is DriverModuleUpdateResult.Current -> "Installed driver is up to date"
            DriverModuleUpdateResult.Failed -> "Could not check the latest driver release"
            null -> when (state) {
                DriverModuleSectionState.Inspecting -> "Checking installed driver"
                DriverModuleSectionState.Missing -> bundled?.let {
                    "Not installed; version ${it.info.version} is ready"
                } ?: "Not installed"
                DriverModuleSectionState.RootUnavailable -> "Root access is required"
                is DriverModuleSectionState.Installed -> "Installed ${state.info.version}"
                is DriverModuleSectionState.Failed -> state.message
            }
        }
    }
}

private fun preferredModuleAction(
    state: DriverModuleSectionState,
    bundled: BundledDriverModule?,
    update: DriverModuleUpdateResult?,
): PendingDriverModuleAction? {
    if (update is DriverModuleUpdateResult.Available) {
        return PendingDriverModuleAction.GitHub(
            release = update.release,
            title = "Update",
            description = "Version ${update.release.version} will be downloaded from GitHub, " +
                "verified, and installed.",
        )
    }
    if (bundled == null) return null
    return when (state) {
        DriverModuleSectionState.Missing -> PendingDriverModuleAction.Bundled(
            title = "Install",
            description = "The bundled version ${bundled.info.version} will be installed.",
        )
        is DriverModuleSectionState.Installed -> {
            val updateBundled = state.info.versionCode < bundled.info.versionCode
            PendingDriverModuleAction.Bundled(
                title = if (updateBundled) "Update" else "Reinstall",
                description = if (updateBundled) {
                    "The bundled version ${bundled.info.version} will replace " +
                        "${state.info.version}."
                } else {
                    "The bundled version ${bundled.info.version} will be installed again."
                },
            )
        }
        DriverModuleSectionState.Inspecting,
        DriverModuleSectionState.RootUnavailable,
        is DriverModuleSectionState.Failed,
        -> null
    }
}

@Composable
internal fun AudioRouteIcon(route: AudioRoute, contentDescription: String?) {
    Icon(
        imageVector = route.kind.icon,
        contentDescription = contentDescription,
        tint = MaterialTheme.colorScheme.primary,
    )
}

private val AudioRouteKind.icon: ImageVector
    get() = when (this) {
        AudioRouteKind.PhoneSpeaker -> Icons.Rounded.PhoneAndroid
        AudioRouteKind.WiredHeadphones -> Icons.Rounded.Headphones
        AudioRouteKind.Bluetooth -> Icons.Rounded.BluetoothAudio
        AudioRouteKind.Usb -> Icons.Rounded.Usb
        AudioRouteKind.Hdmi -> Icons.Rounded.Tv
        AudioRouteKind.Other -> Icons.Rounded.DevicesOther
    }

private val AudioRouteKind.displayName: String
    get() = when (this) {
        AudioRouteKind.PhoneSpeaker -> "Built-in speaker"
        AudioRouteKind.WiredHeadphones -> "Wired audio"
        AudioRouteKind.Bluetooth -> "Bluetooth audio"
        AudioRouteKind.Usb -> "USB audio"
        AudioRouteKind.Hdmi -> "HDMI audio"
        AudioRouteKind.Other -> "External audio"
    }

@Composable
private fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        style = MaterialTheme.typography.labelLarge,
    )
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1F)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                summary,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
