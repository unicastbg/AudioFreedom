package com.svetlio.audiofreedom

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import java.util.Locale

internal enum class AudioRouteKind {
    PhoneSpeaker,
    WiredHeadphones,
    Bluetooth,
    Usb,
    Hdmi,
    Other,
}

internal data class AudioRoute(
    val id: String,
    val label: String,
    val kind: AudioRouteKind,
)

internal object AudioRouteDetector {
    fun current(context: Context): AudioRoute {
        val audioManager = context.getSystemService(AudioManager::class.java)
        val devices = if (Build.VERSION.SDK_INT >= 36) {
            val mediaAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            audioManager.getAudioDevicesForAttributes(mediaAttributes).toTypedArray()
        } else {
            audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        }
        val device = devices
            .filter { it.isSink }
            .maxByOrNull(::priority)
        return device?.toRoute() ?: AudioRoute(
            id = "phone-speaker",
            label = "Phone speaker",
            kind = AudioRouteKind.PhoneSpeaker,
        )
    }

    private fun priority(device: AudioDeviceInfo): Int = when (device.type) {
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        AudioDeviceInfo.TYPE_BLE_HEADSET,
        AudioDeviceInfo.TYPE_BLE_SPEAKER,
        AudioDeviceInfo.TYPE_BLE_BROADCAST,
        -> 600

        AudioDeviceInfo.TYPE_USB_DEVICE,
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_USB_ACCESSORY,
        -> 500

        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_LINE_ANALOG,
        -> 400

        AudioDeviceInfo.TYPE_HDMI,
        AudioDeviceInfo.TYPE_HDMI_ARC,
        AudioDeviceInfo.TYPE_HDMI_EARC,
        -> 300

        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE,
        -> 100

        else -> 0
    }

    private fun AudioDeviceInfo.toRoute(): AudioRoute {
        val kind = when (type) {
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLE_SPEAKER,
            AudioDeviceInfo.TYPE_BLE_BROADCAST,
            -> AudioRouteKind.Bluetooth

            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_ACCESSORY,
            -> AudioRouteKind.Usb

            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_LINE_ANALOG,
            -> AudioRouteKind.WiredHeadphones

            AudioDeviceInfo.TYPE_HDMI,
            AudioDeviceInfo.TYPE_HDMI_ARC,
            AudioDeviceInfo.TYPE_HDMI_EARC,
            -> AudioRouteKind.Hdmi

            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE,
            -> AudioRouteKind.PhoneSpeaker

            else -> AudioRouteKind.Other
        }
        val product = productName.toString().trim()
        val label = when {
            kind == AudioRouteKind.PhoneSpeaker -> "Phone speaker"
            product.isNotEmpty() -> product
            kind == AudioRouteKind.Bluetooth -> "Bluetooth audio"
            kind == AudioRouteKind.Usb -> "USB audio"
            kind == AudioRouteKind.WiredHeadphones -> "Wired headphones"
            kind == AudioRouteKind.Hdmi -> "HDMI audio"
            else -> "Audio output"
        }
        val stableName = product.ifEmpty { label }.lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
        return AudioRoute(
            id = "${kind.name.lowercase(Locale.ROOT)}-$stableName",
            label = label,
            kind = kind,
        )
    }
}
