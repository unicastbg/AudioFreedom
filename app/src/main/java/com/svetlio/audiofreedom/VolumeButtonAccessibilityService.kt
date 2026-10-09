package com.svetlio.audiofreedom

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import androidx.core.content.ContextCompat

class VolumeButtonAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private val gesture = VolumeHoldGesture()
    private lateinit var audioManager: AudioManager
    private val activateHold = Runnable {
        if (!eligible()) gesture.cancel()
        else if (gesture.activate(SystemClock.uptimeMillis())) activateVoice()
    }
    private val modeListener = AudioManager.OnModeChangedListener { mode ->
        if (mode != AudioManager.MODE_NORMAL) {
            handler.removeCallbacks(activateHold)
            gesture.cancel()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        AssistantRuntimeManager.initialize(this)
        audioManager = getSystemService(AudioManager::class.java)
        audioManager.addOnModeChangedListener(mainExecutor, modeListener)
    }

    private fun eligible(): Boolean =
        AssistantHandsFreeService.canUseShortcut(AppPreferencesStore.load(this)) &&
            audioManager.mode == AudioManager.MODE_NORMAL &&
            !AssistantHandsFreeService.isRunning && VoiceCaptureOwnership.manualSessions.get() == 0 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (!::audioManager.isInitialized) return false
        val preferences = AppPreferencesStore.load(this)
        if (gesture.tracks(event.keyCode, event.deviceId)) {
            if (event.action == KeyEvent.ACTION_UP) {
                handler.removeCallbacks(activateHold)
                if (event.isCanceled || audioManager.mode != AudioManager.MODE_NORMAL ||
                    !AssistantHandsFreeService.canUseShortcut(preferences)) gesture.cancel()
                when (gesture.release(event.keyCode, event.deviceId, SystemClock.uptimeMillis())) {
                    VolumeRelease.Tap -> audioManager.adjustSuggestedStreamVolume(
                        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP) AudioManager.ADJUST_RAISE
                        else AudioManager.ADJUST_LOWER,
                        AudioManager.USE_DEFAULT_STREAM_TYPE, AudioManager.FLAG_SHOW_UI)
                    VolumeRelease.Activate -> if (eligible()) activateVoice()
                    else -> Unit
                }
            }
            return true
        }
        if (AssistantHandsFreeService.isRunning && event.keyCode == preferences.assistantVolumeButton.keyCode &&
            event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            handler.removeCallbacks(activateHold)
            if (!gesture.beginCancellation(event.keyCode, event.deviceId)) return false
            AssistantHandsFreeService.interruptConversation()
            return true
        }
        if (!eligible() || event.keyCode != preferences.assistantVolumeButton.keyCode ||
            event.action != KeyEvent.ACTION_DOWN || event.repeatCount != 0) return false
        if (!gesture.begin(event.keyCode, event.deviceId, SystemClock.uptimeMillis(),
                preferences.assistantButtonHoldMilliseconds)) return false
        handler.postDelayed(activateHold, preferences.assistantButtonHoldMilliseconds.toLong())
        return true
    }

    private fun activateVoice() {
        try {
            startActivity(Intent(this, VolumeVoiceActivationActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS))
        } catch (error: Exception) {
            AssistantHandsFreeService.activationFailed(error.message ?: "Could not activate voice command")
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() {
        handler.removeCallbacks(activateHold)
        gesture.cancel()
        AssistantHandsFreeService.interruptConversation()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        onInterrupt()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        onInterrupt()
        if (::audioManager.isInitialized) audioManager.removeOnModeChangedListener(modeListener)
        super.onDestroy()
    }

    companion object {
        internal fun enabled(context: Context): Boolean = context.getSystemService(AccessibilityManager::class.java)
            .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.resolveInfo.serviceInfo.let { info ->
                info.packageName == context.packageName && info.name == VolumeButtonAccessibilityService::class.java.name
            } }

        internal fun openSettings(context: Context) {
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }
}
