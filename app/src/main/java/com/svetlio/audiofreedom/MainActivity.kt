package com.svetlio.audiofreedom

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initialDriverState = probeDriver()
        val initialEnabled = AudioFreedomService.isRequestedEnabled(this)
        val initialSettings = AudioFreedomSettingsStore.load(this)
        var appPreferences by mutableStateOf(AppPreferencesStore.load(this))
        if (initialEnabled) {
            AudioFreedomService.setEnabled(this, true)
        }
        Log.i("AudioFreedom", "Driver state: ${initialDriverState.name}")
        setContent {
            AudioFreedomTheme(appPreferences.theme) {
                ControllerScreen(
                    initialDriverState = initialDriverState,
                    initialEnabled = initialEnabled,
                    initialSettings = initialSettings,
                    initialAppPreferences = appPreferences,
                    onRefresh = {
                        if (AudioFreedomService.isRequestedEnabled(this)) {
                            AudioFreedomService.currentState
                        } else {
                            probeDriver()
                        }
                    },
                    onProcessingChanged = {
                        if (it && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                            PackageManager.PERMISSION_GRANTED
                        ) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        AudioFreedomService.setEnabled(this, it)
                    },
                    onSettingsChanged = {
                        AudioFreedomService.updateSettings(this, it)
                    },
                    onAppPreferencesChanged = {
                        AppPreferencesStore.save(this, it)
                        appPreferences = it
                        AudioFreedomService.refreshStatus(this)
                    },
                )
            }
        }
        if (initialEnabled && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ControllerScreen(
    initialDriverState: DriverState,
    initialEnabled: Boolean,
    initialSettings: AudioFreedomSettings,
    initialAppPreferences: AppPreferences,
    onRefresh: () -> DriverState,
    onProcessingChanged: (Boolean) -> DriverState,
    onSettingsChanged: (AudioFreedomSettings) -> DriverState,
    onAppPreferencesChanged: (AppPreferences) -> Unit,
) {
    var driverState by remember { mutableStateOf(initialDriverState) }
    var enabled by remember { mutableStateOf(initialEnabled) }
    var settings by remember { mutableStateOf(initialSettings) }
    var selectedPreset by remember { mutableStateOf(EqualizerPreset.matching(initialSettings)) }
    var presetMenuExpanded by remember { mutableStateOf(false) }
    var selectedBassPreset by remember {
        mutableStateOf(BassFoundationPreset.matching(initialSettings))
    }
    var bassPresetMenuExpanded by remember { mutableStateOf(false) }
    var selectedDetailPreset by remember {
        mutableStateOf(DetailRecoveryPreset.matching(initialSettings))
    }
    var detailPresetMenuExpanded by remember { mutableStateOf(false) }
    var selectedImmersivePreset by remember {
        mutableStateOf(ImmersiveFieldPreset.matching(initialSettings))
    }
    var immersivePresetMenuExpanded by remember { mutableStateOf(false) }
    var outputExpanded by rememberSaveable { mutableStateOf(false) }
    var equalizerExpanded by rememberSaveable { mutableStateOf(false) }
    var dynamicBassExpanded by rememberSaveable { mutableStateOf(false) }
    var detailRecoveryExpanded by rememberSaveable { mutableStateOf(false) }
    var immersiveFieldExpanded by rememberSaveable { mutableStateOf(false) }
    var outputMetrics by remember { mutableStateOf(AudioFreedomService.currentOutputMetrics) }
    val context = androidx.compose.ui.platform.LocalContext.current
    var appPreferences by remember { mutableStateOf(initialAppPreferences) }
    var profiles by remember { mutableStateOf(AudioFreedomProfileStore.list(context)) }
    var audioRoute by remember { mutableStateOf(AudioRouteDetector.current(context)) }
    var assignedProfileId by remember {
        mutableStateOf(AppPreferencesStore.profileForRoute(context, audioRoute.id))
    }
    var selectedProfileId by remember {
        mutableStateOf(
            assignedProfileId.takeIf { initialAppPreferences.automaticDeviceProfiles },
        )
    }
    var profileMenuExpanded by remember { mutableStateOf(false) }
    var profileSaveRequest by remember { mutableStateOf<ProfileSaveRequest?>(null) }
    var profileName by remember { mutableStateOf("") }
    var showingSettings by rememberSaveable { mutableStateOf(false) }
    var rootAccessState by remember { mutableStateOf(RootAccessState.NotChecked) }
    var diagnosticRefresh by remember { mutableIntStateOf(0) }
    val driverAvailable =
        driverState == DriverState.Attached || driverState == DriverState.Detected

    fun commitSettings(updated: AudioFreedomSettings) {
        settings = updated
        selectedPreset = EqualizerPreset.matching(updated)
        selectedBassPreset = BassFoundationPreset.matching(updated)
        selectedDetailPreset = DetailRecoveryPreset.matching(updated)
        selectedImmersivePreset = ImmersiveFieldPreset.matching(updated)
        driverState = onSettingsChanged(updated)
    }

    LaunchedEffect(enabled) {
        if (!enabled) {
            outputMetrics = null
            return@LaunchedEffect
        }
        while (true) {
            outputMetrics = AudioFreedomService.currentOutputMetrics
            delay(250L)
        }
    }

    LaunchedEffect(driverState, diagnosticRefresh) {
        rootAccessState = if (driverAvailable) {
            RootAccessState.NotChecked
        } else {
            RootAccessState.Checking
            withContext(Dispatchers.IO) { probeRootAccess() }
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            val detectedRoute = AudioRouteDetector.current(context)
            if (detectedRoute.id != audioRoute.id) {
                audioRoute = detectedRoute
                assignedProfileId =
                    AppPreferencesStore.profileForRoute(context, detectedRoute.id)
                if (appPreferences.automaticDeviceProfiles) {
                    profiles.firstOrNull { it.id == assignedProfileId }?.let { profile ->
                        commitSettings(profile.settings)
                        selectedProfileId = profile.id
                    }
                }
            }
            delay(1000L)
        }
    }

    profileSaveRequest?.let { request ->
        ProfileSaveDialog(
            title = if (request.profileId == null) "Save new profile" else "Update profile",
            profiles = profiles,
            profileId = request.profileId,
            name = profileName,
            onProfileChanged = { profileId ->
                profileSaveRequest = ProfileSaveRequest(profileId)
                profileName = profiles.firstOrNull { it.id == profileId }?.name.orEmpty()
            },
            onNameChange = { profileName = it },
            onDismiss = { profileSaveRequest = null },
            onSave = {
                val saved = AudioFreedomProfileStore.save(
                    context = context,
                    name = profileName,
                    settings = settings,
                    profileId = request.profileId,
                )
                profiles = AudioFreedomProfileStore.list(context)
                selectedProfileId = saved.id
                profileSaveRequest = null
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (showingSettings) {
                        Text("Settings", fontWeight = FontWeight.SemiBold)
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("AudioFreedom", fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.width(14.dp))
                            AudioRouteIcon(audioRoute, contentDescription = audioRoute.label)
                        }
                    }
                },
                navigationIcon = {
                    if (showingSettings) {
                        IconButton(onClick = { showingSettings = false }) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    if (!showingSettings) {
                        IconButton(onClick = { showingSettings = true }) {
                            Icon(Icons.Rounded.Settings, contentDescription = "Settings")
                        }
                        IconButton(onClick = {
                            driverState = onRefresh()
                            diagnosticRefresh++
                            enabled = AudioFreedomService.isRequestedEnabled(context)
                            audioRoute = AudioRouteDetector.current(context)
                        }) {
                            Icon(Icons.Rounded.Refresh, contentDescription = "Refresh driver status")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { contentPadding ->
        if (showingSettings) {
            SettingsScreen(
                route = audioRoute,
                profiles = profiles,
                preferences = appPreferences,
                assignedProfileId = assignedProfileId,
                onPreferencesChanged = { updated ->
                    val automaticProfilesJustEnabled =
                        !appPreferences.automaticDeviceProfiles &&
                            updated.automaticDeviceProfiles
                    appPreferences = updated
                    onAppPreferencesChanged(updated)
                    if (automaticProfilesJustEnabled) {
                        profiles.firstOrNull { it.id == assignedProfileId }?.let { profile ->
                            commitSettings(profile.settings)
                            selectedProfileId = profile.id
                        }
                    }
                },
                onProfileAssigned = { profileId ->
                    AppPreferencesStore.assignProfile(context, audioRoute.id, profileId)
                    assignedProfileId = profileId
                    if (appPreferences.automaticDeviceProfiles) {
                        profiles.firstOrNull { it.id == profileId }?.let { profile ->
                            commitSettings(profile.settings)
                            selectedProfileId = profile.id
                        }
                    }
                    AudioFreedomService.refreshStatus(context)
                },
                modifier = Modifier.padding(contentPadding),
            )
        } else {
            Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            DriverStatusRow(driverState, rootAccessState)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text("Processing", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (enabled && driverAvailable) "Enabled" else "Disabled",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Switch(
                    checked = enabled && driverAvailable,
                    enabled = driverAvailable,
                    onCheckedChange = { requested ->
                        driverState = onProcessingChanged(requested)
                        enabled = requested && (
                            driverState == DriverState.Attached ||
                                driverState == DriverState.Detected
                            )
                    },
                )
            }
            HorizontalDivider(
                modifier = Modifier.padding(top = 18.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            ProfileControlRow(
                profiles = profiles,
                selectedProfileId = selectedProfileId,
                profileModified = profiles
                    .firstOrNull { it.id == selectedProfileId }
                    ?.settings
                    ?.let { it != settings } == true,
                menuExpanded = profileMenuExpanded,
                onMenuExpandedChange = { profileMenuExpanded = it },
                onProfileSelected = { profile ->
                    profileMenuExpanded = false
                    commitSettings(profile.settings)
                    selectedProfileId = profile.id
                },
                onAdd = {
                    profileName = ""
                    profileSaveRequest = ProfileSaveRequest(profileId = null)
                },
                onSave = {
                    val selected = profiles.firstOrNull { it.id == selectedProfileId }
                    profileName = selected?.name.orEmpty()
                    profileSaveRequest = ProfileSaveRequest(profileId = selected?.id)
                },
                onDelete = {
                    selectedProfileId?.let { profileId ->
                        AudioFreedomProfileStore.delete(context, profileId)
                        if (assignedProfileId == profileId) {
                            AppPreferencesStore.assignProfile(context, audioRoute.id, null)
                            assignedProfileId = null
                        }
                    }
                    selectedProfileId = null
                    profiles = AudioFreedomProfileStore.list(context)
                },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            CollapsibleEffectSection(
                title = "Equalizer",
                summary = selectedPreset?.label ?: "Custom",
                expanded = equalizerExpanded,
                onExpandedChange = { equalizerExpanded = it },
                enabled = settings.equalizerEnabled,
                onEnabledChange = { requested ->
                    commitSettings(settings.copy(equalizerEnabled = requested))
                },
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.foundation.layout.Box {
                        TextButton(onClick = { presetMenuExpanded = true }) {
                            Text("Preset")
                            Icon(Icons.Rounded.ExpandMore, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = presetMenuExpanded,
                            onDismissRequest = { presetMenuExpanded = false },
                        ) {
                            EqualizerPreset.entries.forEach { preset ->
                                DropdownMenuItem(
                                    text = { Text(preset.label) },
                                    onClick = {
                                        presetMenuExpanded = false
                                        commitSettings(preset.applyTo(settings))
                                    },
                                )
                            }
                        }
                    }
                    IconButton(
                        onClick = {
                            commitSettings(EqualizerPreset.Flat.applyTo(settings))
                        },
                    ) {
                        Icon(Icons.Rounded.RestartAlt, contentDescription = "Reset equalizer")
                    }
                }
                SettingSlider(
                    label = "Preamp",
                    valueLabel = formatGain(settings.preampMillibels),
                    value = settings.preampMillibels.toFloat(),
                    onValueChange = { value ->
                        settings = settings.copy(
                            preampMillibels = quantizeGain(value, -2400, 0),
                        )
                        selectedPreset = EqualizerPreset.matching(settings)
                    },
                    onValueChangeFinished = { commitSettings(settings) },
                    valueRange = -2400F..0F,
                    steps = 47,
                    enabled = settings.equalizerEnabled,
                )
                EqualizerBandLabels.forEachIndexed { band, label ->
                    EqualizerBandRow(
                        label = label,
                        gainMillibels = settings.bandGainsMillibels[band],
                        enabled = settings.equalizerEnabled,
                        onGainChanged = { gain ->
                            val gains = settings.bandGainsMillibels.toMutableList()
                            gains[band] = gain
                            settings = settings.copy(bandGainsMillibels = gains)
                            selectedPreset = EqualizerPreset.matching(settings)
                        },
                        onGainCommitted = { commitSettings(settings) },
                    )
                }
                Spacer(Modifier.height(14.dp))
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            CollapsibleEffectSection(
                title = "Immersive field",
                summary = if (settings.immersiveFieldEnabled) {
                    selectedImmersivePreset?.label ?: "Custom"
                } else {
                    "Off"
                },
                expanded = immersiveFieldExpanded,
                onExpandedChange = { immersiveFieldExpanded = it },
                enabled = settings.immersiveFieldEnabled,
                onEnabledChange = { requested ->
                    commitSettings(settings.copy(immersiveFieldEnabled = requested))
                },
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.foundation.layout.Box {
                        TextButton(onClick = { immersivePresetMenuExpanded = true }) {
                            Text("Preset")
                            Icon(Icons.Rounded.ExpandMore, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = immersivePresetMenuExpanded,
                            onDismissRequest = { immersivePresetMenuExpanded = false },
                        ) {
                            ImmersiveFieldPreset.entries.forEach { preset ->
                                DropdownMenuItem(
                                    text = { Text(preset.label) },
                                    onClick = {
                                        immersivePresetMenuExpanded = false
                                        commitSettings(preset.applyTo(settings))
                                    },
                                )
                            }
                        }
                    }
                }
                PercentageSlider(
                    label = "Amount",
                    value = settings.immersiveAmountPercent,
                    enabled = settings.immersiveFieldEnabled,
                    onValueChange = { value ->
                        settings = settings.copy(immersiveAmountPercent = value)
                        selectedImmersivePreset = ImmersiveFieldPreset.matching(settings)
                    },
                    onValueChangeFinished = { commitSettings(settings) },
                )
                PercentageSlider(
                    label = "Stage width",
                    value = settings.immersiveWidthPercent,
                    enabled = settings.immersiveFieldEnabled,
                    onValueChange = { value ->
                        settings = settings.copy(immersiveWidthPercent = value)
                        selectedImmersivePreset = ImmersiveFieldPreset.matching(settings)
                    },
                    onValueChangeFinished = { commitSettings(settings) },
                )
                PercentageSlider(
                    label = "Center",
                    value = settings.immersiveCenterPercent,
                    enabled = settings.immersiveFieldEnabled,
                    onValueChange = { value ->
                        settings = settings.copy(immersiveCenterPercent = value)
                        selectedImmersivePreset = ImmersiveFieldPreset.matching(settings)
                    },
                    onValueChangeFinished = { commitSettings(settings) },
                )
                PercentageSlider(
                    label = "Room",
                    value = settings.immersiveRoomPercent,
                    enabled = settings.immersiveFieldEnabled,
                    onValueChange = { value ->
                        settings = settings.copy(immersiveRoomPercent = value)
                        selectedImmersivePreset = ImmersiveFieldPreset.matching(settings)
                    },
                    onValueChangeFinished = { commitSettings(settings) },
                )
                Spacer(Modifier.height(14.dp))
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            CollapsibleEffectSection(
                title = "Detail recovery",
                summary = if (settings.detailRecoveryEnabled) {
                    selectedDetailPreset?.label ?: "Custom"
                } else {
                    "Off"
                },
                expanded = detailRecoveryExpanded,
                onExpandedChange = { detailRecoveryExpanded = it },
                enabled = settings.detailRecoveryEnabled,
                onEnabledChange = { requested ->
                    commitSettings(settings.copy(detailRecoveryEnabled = requested))
                },
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.foundation.layout.Box {
                        TextButton(onClick = { detailPresetMenuExpanded = true }) {
                            Text("Preset")
                            Icon(Icons.Rounded.ExpandMore, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = detailPresetMenuExpanded,
                            onDismissRequest = { detailPresetMenuExpanded = false },
                        ) {
                            DetailRecoveryPreset.entries.forEach { preset ->
                                DropdownMenuItem(
                                    text = { Text(preset.label) },
                                    onClick = {
                                        detailPresetMenuExpanded = false
                                        commitSettings(preset.applyTo(settings))
                                    },
                                )
                            }
                        }
                    }
                }
                SettingSlider(
                    label = "Amount",
                    valueLabel = "${settings.detailAmountPercent}%",
                    value = settings.detailAmountPercent.toFloat(),
                    onValueChange = { value ->
                        settings = settings.copy(
                            detailAmountPercent =
                                ((value / 5F).roundToInt() * 5).coerceIn(0, 100),
                        )
                        selectedDetailPreset = DetailRecoveryPreset.matching(settings)
                    },
                    onValueChangeFinished = { commitSettings(settings) },
                    valueRange = 0F..100F,
                    steps = 19,
                    enabled = settings.detailRecoveryEnabled,
                )
                SettingSlider(
                    label = "Focus",
                    valueLabel = "${settings.detailFocusHz} Hz",
                    value = settings.detailFocusHz.toFloat(),
                    onValueChange = { value ->
                        settings = settings.copy(
                            detailFocusHz =
                                ((value / 500F).roundToInt() * 500).coerceIn(3000, 10000),
                        )
                        selectedDetailPreset = DetailRecoveryPreset.matching(settings)
                    },
                    onValueChangeFinished = { commitSettings(settings) },
                    valueRange = 3000F..10000F,
                    steps = 13,
                    enabled = settings.detailRecoveryEnabled,
                )
                SettingSlider(
                    label = "Transients",
                    valueLabel = "${settings.detailTransientsPercent}%",
                    value = settings.detailTransientsPercent.toFloat(),
                    onValueChange = { value ->
                        settings = settings.copy(
                            detailTransientsPercent =
                                ((value / 5F).roundToInt() * 5).coerceIn(0, 100),
                        )
                        selectedDetailPreset = DetailRecoveryPreset.matching(settings)
                    },
                    onValueChangeFinished = { commitSettings(settings) },
                    valueRange = 0F..100F,
                    steps = 19,
                    enabled = settings.detailRecoveryEnabled,
                )
                Spacer(Modifier.height(14.dp))
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            CollapsibleEffectSection(
                title = "Bass foundation",
                summary = if (settings.dynamicBassEnabled) {
                    selectedBassPreset?.label ?: "Custom"
                } else {
                    "Off"
                },
                expanded = dynamicBassExpanded,
                onExpandedChange = { dynamicBassExpanded = it },
                enabled = settings.dynamicBassEnabled,
                onEnabledChange = { requested ->
                    commitSettings(settings.copy(dynamicBassEnabled = requested))
                },
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.foundation.layout.Box {
                        TextButton(onClick = { bassPresetMenuExpanded = true }) {
                            Text("Device")
                            Icon(Icons.Rounded.ExpandMore, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = bassPresetMenuExpanded,
                            onDismissRequest = { bassPresetMenuExpanded = false },
                        ) {
                            BassFoundationPreset.entries.forEach { preset ->
                                DropdownMenuItem(
                                    text = { Text(preset.label) },
                                    onClick = {
                                        bassPresetMenuExpanded = false
                                        commitSettings(preset.applyTo(settings))
                                    },
                                )
                            }
                        }
                    }
                }
                SettingSlider(
                    label = "Strength",
                    valueLabel = "${(settings.bassBoostMillibels / 12F).roundToInt()}%",
                    value = settings.bassBoostMillibels.toFloat(),
                    onValueChange = { value ->
                        settings = settings.copy(
                            bassBoostMillibels =
                                ((value / 12F).roundToInt() * 12).coerceIn(0, 1200),
                        )
                        selectedBassPreset = BassFoundationPreset.matching(settings)
                    },
                    onValueChangeFinished = { commitSettings(settings) },
                    valueRange = 0F..1200F,
                    steps = 99,
                    enabled = settings.dynamicBassEnabled,
                )
                SettingSlider(
                    label = "Bass range",
                    valueLabel = "${settings.bassCutoffHz} Hz",
                    value = settings.bassCutoffHz.toFloat(),
                    onValueChange = { value ->
                        settings = settings.copy(
                            bassCutoffHz =
                                ((value / 5F).roundToInt() * 5).coerceIn(40, 160),
                        )
                        selectedBassPreset = BassFoundationPreset.matching(settings)
                    },
                    onValueChangeFinished = { commitSettings(settings) },
                    valueRange = 40F..160F,
                    steps = 23,
                    enabled = settings.dynamicBassEnabled,
                )
                PercentageSlider(
                    label = "Small-driver support",
                    value = settings.bassDynamicsPercent,
                    enabled = settings.dynamicBassEnabled,
                    onValueChange = { value ->
                        settings = settings.copy(bassDynamicsPercent = value)
                        selectedBassPreset = BassFoundationPreset.matching(settings)
                    },
                    onValueChangeFinished = { commitSettings(settings) },
                )
                Spacer(Modifier.height(14.dp))
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            CollapsibleEffectSection(
                title = "Output protection",
                summary = if (settings.limiterEnabled) {
                    "${formatGain(settings.limiterThresholdMillibels)} ceiling"
                } else {
                    "Off"
                },
                expanded = outputExpanded,
                onExpandedChange = { outputExpanded = it },
                enabled = settings.limiterEnabled,
                onEnabledChange = { requested ->
                    commitSettings(settings.copy(limiterEnabled = requested))
                },
            ) {
                SettingSlider(
                    label = "Limiter ceiling",
                    valueLabel = formatGain(settings.limiterThresholdMillibels),
                    value = settings.limiterThresholdMillibels.toFloat(),
                    onValueChange = { value ->
                        settings = settings.copy(
                            limiterThresholdMillibels = quantizeGain(value, -600, 0),
                        )
                    },
                    onValueChangeFinished = { commitSettings(settings) },
                    valueRange = -600F..0F,
                    steps = 11,
                    enabled = settings.limiterEnabled,
                )
                SettingSlider(
                    label = "Release",
                    valueLabel = "${settings.limiterReleaseMilliseconds} ms",
                    value = settings.limiterReleaseMilliseconds.toFloat(),
                    onValueChange = { value ->
                        settings = settings.copy(
                            limiterReleaseMilliseconds =
                                ((value / 10F).roundToInt() * 10).coerceIn(20, 1000),
                        )
                    },
                    onValueChangeFinished = { commitSettings(settings) },
                    valueRange = 20F..1000F,
                    steps = 97,
                    enabled = settings.limiterEnabled,
                )
                Spacer(Modifier.height(6.dp))
                OutputMeterRow("Input peak", outputMetrics?.inputPeakMillibels)
                OutputMeterRow("Output peak", outputMetrics?.outputPeakMillibels)
                OutputMeterRow(
                    "Gain reduction",
                    outputMetrics?.gainReductionMillibels,
                    isReduction = true,
                )
                Spacer(Modifier.height(14.dp))
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(24.dp))
        }
        }
    }
}

private data class ProfileSaveRequest(val profileId: String?)

@Composable
private fun ProfileSaveDialog(
    title: String,
    profiles: List<AudioFreedomProfile>,
    profileId: String?,
    name: String,
    onProfileChanged: (String?) -> Unit,
    onNameChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    var targetMenuExpanded by remember { mutableStateOf(false) }
    val selectedProfile = profiles.firstOrNull { it.id == profileId }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text("Save to", style = MaterialTheme.typography.labelLarge)
                androidx.compose.foundation.layout.Box {
                    TextButton(onClick = { targetMenuExpanded = true }) {
                        Text(
                            selectedProfile?.name ?: "New profile",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Icon(Icons.Rounded.ExpandMore, contentDescription = "Choose save target")
                    }
                    DropdownMenu(
                        expanded = targetMenuExpanded,
                        onDismissRequest = { targetMenuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("New profile") },
                            onClick = {
                                targetMenuExpanded = false
                                onProfileChanged(null)
                            },
                        )
                        profiles.forEach { profile ->
                            DropdownMenuItem(
                                text = { Text(profile.name) },
                                onClick = {
                                    targetMenuExpanded = false
                                    onProfileChanged(profile.id)
                                },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    label = { Text("Profile name") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onSave, enabled = name.isNotBlank()) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun ProfileControlRow(
    profiles: List<AudioFreedomProfile>,
    selectedProfileId: String?,
    profileModified: Boolean,
    menuExpanded: Boolean,
    onMenuExpandedChange: (Boolean) -> Unit,
    onProfileSelected: (AudioFreedomProfile) -> Unit,
    onAdd: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
) {
    val selected = profiles.firstOrNull { it.id == selectedProfileId }
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 76.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1F)) {
            Text("Profile", style = MaterialTheme.typography.titleMedium)
            androidx.compose.foundation.layout.Box {
                TextButton(onClick = { onMenuExpandedChange(true) }) {
                    Text(
                        when {
                            selected == null -> "Current settings"
                            profileModified -> "${selected.name} (Modified)"
                            else -> selected.name
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Icon(Icons.Rounded.ExpandMore, contentDescription = "Choose profile")
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { onMenuExpandedChange(false) },
                ) {
                    if (profiles.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("No saved profiles") },
                            onClick = {},
                            enabled = false,
                        )
                    } else {
                        profiles.forEach { profile ->
                            DropdownMenuItem(
                                text = { Text(profile.name) },
                                onClick = { onProfileSelected(profile) },
                            )
                        }
                    }
                }
            }
        }
        IconButton(onClick = onAdd) {
            Icon(Icons.Rounded.Add, contentDescription = "Save as new profile")
        }
        IconButton(onClick = onSave) {
            Icon(Icons.Rounded.Save, contentDescription = "Save current settings")
        }
        IconButton(onClick = onDelete, enabled = selected != null) {
            Icon(Icons.Rounded.Delete, contentDescription = "Delete selected profile")
        }
    }
}

@Composable
private fun PercentageSlider(
    label: String,
    value: Int,
    enabled: Boolean,
    onValueChange: (Int) -> Unit,
    onValueChangeFinished: () -> Unit,
) {
    SettingSlider(
        label = label,
        valueLabel = "$value%",
        value = value.toFloat(),
        onValueChange = {
            onValueChange(((it / 5F).roundToInt() * 5).coerceIn(0, 100))
        },
        onValueChangeFinished = onValueChangeFinished,
        valueRange = 0F..100F,
        steps = 19,
        enabled = enabled,
    )
}

@Composable
private fun CollapsibleEffectSection(
    title: String,
    summary: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 76.dp)
            .clickable { onExpandedChange(!expanded) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1F)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                summary,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Switch(checked = enabled, onCheckedChange = onEnabledChange)
        IconButton(onClick = { onExpandedChange(!expanded) }) {
            Icon(
                if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = if (expanded) "Collapse $title" else "Expand $title",
            )
        }
    }
    if (expanded) {
        content()
    }
}

@Composable
private fun SettingSlider(
    label: String,
    valueLabel: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(
            valueLabel,
            color = if (enabled) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            fontWeight = FontWeight.SemiBold,
        )
    }
    Slider(
        value = value,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        valueRange = valueRange,
        steps = steps,
        enabled = enabled,
    )
}

@Composable
private fun OutputMeterRow(label: String, millibels: Int?, isReduction: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().height(32.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            when {
                millibels == null -> "--"
                isReduction -> String.format(Locale.US, "%.1f dB", millibels / 100F)
                else -> formatGain(millibels)
            },
            modifier = Modifier.width(72.dp),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun EqualizerBandRow(
    label: String,
    gainMillibels: Int,
    enabled: Boolean,
    onGainChanged: (Int) -> Unit,
    onGainCommitted: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(52.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.width(44.dp), style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = gainMillibels.toFloat(),
            onValueChange = { onGainChanged(quantizeGain(it, -1200, 1200)) },
            onValueChangeFinished = onGainCommitted,
            valueRange = -1200F..1200F,
            steps = 47,
            enabled = enabled,
            modifier = Modifier.weight(1F),
        )
        Text(
            formatGain(gainMillibels),
            modifier = Modifier.width(64.dp),
            color = if (gainMillibels == 0) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.primary
            },
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private fun quantizeGain(value: Float, minimum: Int, maximum: Int): Int =
    ((value / 50F).roundToInt() * 50).coerceIn(minimum, maximum)

private fun formatGain(millibels: Int): String =
    String.format(Locale.US, "%+.1f dB", millibels / 100F)

@Composable
private fun DriverStatusRow(state: DriverState, rootAccessState: RootAccessState) {
    val (label, color) = when (state) {
        DriverState.Attached -> "Driver attached" to MaterialTheme.colorScheme.primary
        DriverState.Detected -> "Driver detected" to MaterialTheme.colorScheme.primary
        DriverState.ParameterError -> "DSP control unavailable" to MaterialTheme.colorScheme.error
        DriverState.NotInstalled -> "Driver not installed" to MaterialTheme.colorScheme.error
        DriverState.ProbeFailed -> "Driver check failed" to MaterialTheme.colorScheme.error
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(color = color, shape = MaterialTheme.shapes.extraSmall) {
            Spacer(Modifier.width(8.dp).height(8.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(label, color = color, style = MaterialTheme.typography.bodyMedium)
            when (rootAccessState) {
                RootAccessState.Checking -> Text(
                    "Checking root access",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                RootAccessState.Available -> Text(
                    "Root access available",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                RootAccessState.Unavailable -> Text(
                    "Root denied or unavailable",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
                RootAccessState.NotChecked -> Unit
            }
        }
    }
}

@Composable
private fun AudioFreedomTheme(
    themePreference: ThemePreference,
    content: @Composable () -> Unit,
) {
    val useDarkTheme = when (themePreference) {
        ThemePreference.System -> isSystemInDarkTheme()
        ThemePreference.Light -> false
        ThemePreference.Dark -> true
    }
    val colorScheme = if (useDarkTheme) {
        darkColorScheme(
            primary = Color(0xFF68DBB7),
            secondary = Color(0xFFB1CCC1),
            background = Color(0xFF101412),
            surface = Color(0xFF101412),
            onSurface = Color(0xFFE0E4DF),
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF006B55),
            secondary = Color(0xFF4E635B),
            surface = Color(0xFFF7F8F5),
            background = Color(0xFFF7F8F5),
            onSurface = Color(0xFF1A1C1A),
        )
    }
    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
