package com.svetlio.audiofreedom

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier

internal fun needsDriverUpdateNotice(
    installed: DriverModuleProbeResult,
    bundled: DriverModuleInfo,
    notifiedVersionCode: Int,
    restartPending: Boolean,
): Boolean = !restartPending && notifiedVersionCode < bundled.versionCode &&
    installed is DriverModuleProbeResult.Installed &&
    installed.info.versionCode < bundled.versionCode

internal fun isDriverRestartPending(savedBootCount: Int, currentBootCount: Int): Boolean =
    savedBootCount >= 0 && savedBootCount == currentBootCount

internal fun needsExtendedEqualizerDriverUpdate(installed: DriverModuleProbeResult): Boolean =
    installed is DriverModuleProbeResult.Installed && installed.info.versionCode < 43

internal object DriverModuleNoticeStore {
    private fun preferences(context: Context) =
        context.getSharedPreferences("driver_module_notices", Context.MODE_PRIVATE)

    fun notifiedVersionCode(context: Context): Int =
        preferences(context).getInt("notified_version_code", 0)

    fun acknowledge(context: Context, versionCode: Int) {
        preferences(context).edit().putInt("notified_version_code", versionCode).apply()
    }

    fun installed(context: Context, rootManager: String) {
        preferences(context).edit()
            .putInt("pending_boot_count", bootCount(context))
            .putString("pending_root_manager", rootManager)
            .commit()
    }

    fun pendingRestart(context: Context): String? {
        val prefs = preferences(context)
        val savedBootCount = prefs.getInt("pending_boot_count", -1)
        if (isDriverRestartPending(savedBootCount, bootCount(context))) {
            return prefs.getString("pending_root_manager", null)
        }
        if (savedBootCount >= 0) {
            prefs.edit().remove("pending_boot_count").remove("pending_root_manager").apply()
        }
        return null
    }

    private fun bootCount(context: Context): Int = runCatching {
        Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
    }.getOrDefault(-1)
}

@Composable
internal fun DriverModuleUpdateNotice(
    onOpenUpdates: () -> Unit,
    onModuleChecked: (DriverModuleProbeResult) -> Unit = {},
) {
    val context = LocalContext.current
    val bundled = remember(context) { DriverModuleManager.bundled(context)?.info }
    var installedVersion by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(bundled) {
        val target = bundled ?: return@LaunchedEffect
        val notified = DriverModuleNoticeStore.notifiedVersionCode(context)
        val restartPending = DriverModuleNoticeStore.pendingRestart(context) != null
        val result = DriverModuleManager.probeInstalled(context)
        onModuleChecked(result)
        if (needsDriverUpdateNotice(result, target, notified, restartPending)) {
            installedVersion = (result as DriverModuleProbeResult.Installed).info.version
        }
    }
    val installed = installedVersion ?: return
    val target = bundled ?: return
    fun dismiss() {
        DriverModuleNoticeStore.acknowledge(context, target.versionCode)
        installedVersion = null
    }
    AlertDialog(
        onDismissRequest = ::dismiss,
        title = { Text("Driver module update needed") },
        text = {
            Text("The installed driver is $installed. This app includes driver ${target.version}. " +
                "Update the driver module to use the latest DSP features, including extended EQ. " +
                "Install the bundled update in Settings, then restart your phone. " +
                "Your settings and profiles are retained.",
                modifier = Modifier.verticalScroll(rememberScrollState()))
        },
        confirmButton = {
            TextButton(onClick = { dismiss(); onOpenUpdates() }) { Text("Open driver update") }
        },
        dismissButton = { TextButton(onClick = ::dismiss) { Text("Later") } },
    )
}
