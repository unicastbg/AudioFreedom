package com.svetlio.audiofreedom

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.audiofx.AudioEffect
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.lang.reflect.Method
import java.util.UUID

private const val TAG = "AudioFreedom"

internal fun shouldApplyAssignedProfileOnServiceStart(action: String?): Boolean = action == null

internal val EffectImplementationUuid: UUID =
    UUID.fromString("2f6e8c10-8d44-4b42-b110-16f3a729ef01")
private val EffectTypeUuid: UUID =
    UUID.fromString("a7e03c90-7c3d-4f48-9c8d-497c8f1b1201")

internal enum class DriverState {
    Attached,
    Detected,
    ParameterError,
    NotInstalled,
    ProbeFailed,
}

internal fun probeDriver(): DriverState = try {
    if (AudioEffect.queryEffects()?.any { it.uuid == EffectImplementationUuid } == true) {
        DriverState.Detected
    } else {
        DriverState.NotInstalled
    }
} catch (_: RuntimeException) {
    DriverState.ProbeFailed
}

private class SessionZeroEffectController {
    private var effect: AudioEffect? = null
    private val setParameterMethod by lazy { resolveByteArrayMethod("setParameter") }
    private val getParameterMethod by lazy { resolveByteArrayMethod("getParameter") }

    val isAttached: Boolean
        get() = effect != null

    fun attach(settings: AudioFreedomSettings): DriverState {
        release()
        val probeState = probeDriver()
        if (probeState != DriverState.Detected) {
            return probeState
        }

        return try {
            val constructor = AudioEffect::class.java.getConstructor(
                UUID::class.java,
                UUID::class.java,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
            )
            val created = constructor.newInstance(
                EffectTypeUuid,
                EffectImplementationUuid,
                0,
                0,
            )
            created.enabled = true
            effect = created
            if (!applySettings(settings)) {
                release()
                Log.e(TAG, "Session 0 effect attached but rejected DSP settings")
                return DriverState.ParameterError
            }
            Log.i(TAG, "Session 0 effect attached and enabled")
            DriverState.Attached
        } catch (error: ReflectiveOperationException) {
            Log.e(TAG, "Session 0 effect attachment failed", rootCause(error))
            DriverState.Detected
        } catch (error: RuntimeException) {
            Log.e(TAG, "Session 0 effect attachment failed", rootCause(error))
            DriverState.Detected
        }
    }

    fun release() {
        effect?.release()
        effect = null
    }

    fun applySettings(settings: AudioFreedomSettings): Boolean {
        val currentEffect = effect ?: return false
        val equalizerConfigured = sendParameter(
                currentEffect,
                AudioFreedomProtocol.equalizerConfiguration(settings),
            )
        if (equalizerConfigured) {
            val dynamicBassConfigured = sendParameter(
                currentEffect,
                AudioFreedomProtocol.dynamicBassConfiguration(settings),
            )
            if (!dynamicBassConfigured) {
                return false
            }
            val detailRecoveryConfigured = sendParameter(
                currentEffect,
                AudioFreedomProtocol.detailRecoveryConfiguration(settings),
            )
            if (!detailRecoveryConfigured) {
                return false
            }
            val immersiveFieldConfigured = sendParameter(
                currentEffect,
                AudioFreedomProtocol.immersiveFieldConfiguration(settings),
            )
            if (!immersiveFieldConfigured) {
                return false
            }
            return sendParameter(
                currentEffect,
                AudioFreedomProtocol.limiterConfiguration(settings),
            )
        }

        // Preserve compatibility with the confirmed fixed-gain rollback driver.
        if (!settings.equalizerEnabled) {
            return sendParameter(
                currentEffect,
                AudioFreedomProtocol.preamp(0),
            )
        }
        return false
    }

    fun queryOutputMetrics(): AudioOutputMetrics? {
        val currentEffect = effect ?: return null
        return try {
            val response = ByteArray(40)
            val bytesRead = getParameterMethod.invoke(
                currentEffect,
                AudioFreedomProtocol.outputMetricsQuery(),
                response,
            ) as Int
            if (bytesRead == response.size) {
                AudioFreedomProtocol.readOutputMetrics(response)
            } else {
                null
            }
        } catch (_: ReflectiveOperationException) {
            null
        } catch (_: SecurityException) {
            null
        } catch (_: RuntimeException) {
            null
        }
    }

    private fun sendParameter(effect: AudioEffect, message: ByteArray): Boolean = try {
        val result = setParameterMethod.invoke(
            effect,
            AudioFreedomProtocol.parameterKey(),
            message,
        ) as Int
        result == AudioEffect.SUCCESS
    } catch (error: ReflectiveOperationException) {
        Log.e(TAG, "AudioEffect parameter transport failed", rootCause(error))
        false
    } catch (error: SecurityException) {
        Log.e(TAG, "AudioEffect parameter transport was blocked", error)
        false
    } catch (error: RuntimeException) {
        Log.e(TAG, "AudioEffect parameter transport failed", rootCause(error))
        false
    }

    private fun resolveByteArrayMethod(name: String): Method = try {
        AudioEffect::class.java.getMethod(
            name,
            ByteArray::class.java,
            ByteArray::class.java,
        )
    } catch (_: NoSuchMethodException) {
        Log.i(TAG, "Using hidden AudioEffect.$name transport")
        HiddenApiBypass.getDeclaredMethod(
            AudioEffect::class.java,
            name,
            ByteArray::class.java,
            ByteArray::class.java,
        )
    }
}

class AudioFreedomService : Service() {
    private val effectController = SessionZeroEffectController()
    private val meterHandler = Handler(Looper.getMainLooper())
    private val meterPoll = object : Runnable {
        override fun run() {
            currentOutputMetrics = effectController.queryOutputMetrics()
            if (effectController.isAttached) {
                meterHandler.postDelayed(this, METER_INTERVAL_MILLISECONDS)
            }
        }
    }
    private lateinit var audioManager: AudioManager
    private val routeRefresh = Runnable { refreshAudioRoute(applyAssignedProfile = true) }
    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            scheduleRouteRefresh()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            scheduleRouteRefresh()
        }
    }
    private val modeChangedListener = object : AudioManager.OnModeChangedListener {
        override fun onModeChanged(mode: Int) {
            applyAudioMode(mode)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        audioManager = getSystemService(AudioManager::class.java)
        currentAudioRoute = AudioRouteDetector.current(this)
        audioManager.registerAudioDeviceCallback(audioDeviceCallback, meterHandler)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            registerModeChangedListener()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        refreshAudioRoute(
            applyAssignedProfile = true,
            updateNotification = false,
            forceAssignedProfile = shouldApplyAssignedProfileOnServiceStart(intent?.action),
        )
        startForeground(NOTIFICATION_ID, createNotification(isPaused = false))
        applyAudioMode(
            audioManager.mode,
            forceSettings = intent?.action == ACTION_APPLY_SETTINGS ||
                intent?.action == ACTION_REFRESH_STATUS,
        )
        return START_STICKY
    }

    override fun onDestroy() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            unregisterModeChangedListener()
        }
        audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
        meterHandler.removeCallbacks(routeRefresh)
        effectController.release()
        stopMetering()
        currentState = probeDriver()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @RequiresApi(Build.VERSION_CODES.S)
    private fun registerModeChangedListener() {
        audioManager.addOnModeChangedListener(mainExecutor, modeChangedListener)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun unregisterModeChangedListener() {
        audioManager.removeOnModeChangedListener(modeChangedListener)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Audio processing",
            NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun applyAudioMode(mode: Int, forceSettings: Boolean = false) {
        if (mode == AudioManager.MODE_NORMAL) {
            val settings = AudioFreedomSettingsStore.load(this)
            if (!effectController.isAttached) {
                currentState = effectController.attach(settings)
            } else if (forceSettings && !effectController.applySettings(settings)) {
                currentState = DriverState.ParameterError
            }
            if (currentState != DriverState.Attached) {
                setRequestedEnabled(this, false)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return
            }
            startForeground(NOTIFICATION_ID, createNotification(isPaused = false))
            startMetering()
            Log.i(TAG, "Processing active in normal audio mode")
            return
        }

        effectController.release()
        stopMetering()
        currentState = probeDriver()
        startForeground(NOTIFICATION_ID, createNotification(isPaused = true))
        Log.i(TAG, "Processing bypassed for audio mode $mode")
    }

    private fun startMetering() {
        meterHandler.removeCallbacks(meterPoll)
        meterHandler.post(meterPoll)
    }

    private fun stopMetering() {
        meterHandler.removeCallbacks(meterPoll)
        currentOutputMetrics = null
    }

    private fun createNotification(isPaused: Boolean): Notification {
        val openApp = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openApp,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val preferences = AppPreferencesStore.load(this)
        val processingState = if (isPaused) "Paused during call" else "Processing enabled"
        val content = if (preferences.showDeviceInNotification) {
            "$processingState - ${currentAudioRoute.label}"
        } else {
            processingState
        }
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("AudioFreedom")
            .setContentText(content)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun scheduleRouteRefresh() {
        meterHandler.removeCallbacks(routeRefresh)
        meterHandler.postDelayed(routeRefresh, ROUTE_SETTLE_MILLISECONDS)
    }

    private fun refreshAudioRoute(
        applyAssignedProfile: Boolean,
        updateNotification: Boolean = true,
        forceAssignedProfile: Boolean = false,
    ) {
        val route = AudioRouteDetector.current(this)
        val routeChanged = route.id != currentAudioRoute.id
        currentAudioRoute = route

        if ((routeChanged || forceAssignedProfile) && applyAssignedProfile &&
            AppPreferencesStore.load(this).automaticDeviceProfiles
        ) {
            val profileId = AppPreferencesStore.profileForRoute(this, route.id)
            val profile = AudioFreedomProfileStore.list(this).firstOrNull { it.id == profileId }
            if (profile != null) {
                AudioFreedomSettingsStore.save(this, profile.settings)
                if (effectController.isAttached && !effectController.applySettings(profile.settings)) {
                    currentState = DriverState.ParameterError
                }
                Log.i(TAG, "Applied profile ${profile.name} for ${route.label}")
            }
        }

        if (updateNotification && isRequestedEnabled(this)) {
            startForeground(
                NOTIFICATION_ID,
                createNotification(isPaused = audioManager.mode != AudioManager.MODE_NORMAL),
            )
        }
    }

    companion object {
        private const val CHANNEL_ID = "audiofreedom_processing"
        private const val NOTIFICATION_ID = 4101
        private const val METER_INTERVAL_MILLISECONDS = 250L
        private const val ROUTE_SETTLE_MILLISECONDS = 600L
        private const val PREFERENCES = "audiofreedom"
        private const val KEY_ENABLED = "processing_enabled"
        private const val ACTION_APPLY_SETTINGS =
            "com.svetlio.audiofreedom.action.APPLY_SETTINGS"
        private const val ACTION_REFRESH_STATUS =
            "com.svetlio.audiofreedom.action.REFRESH_STATUS"

        @Volatile
        internal var currentState: DriverState = DriverState.Detected
            private set

        @Volatile
        internal var currentOutputMetrics: AudioOutputMetrics? = null
            private set

        @Volatile
        internal var currentAudioRoute: AudioRoute = AudioRoute(
            id = "phone-speaker",
            label = "Phone speaker",
            kind = AudioRouteKind.PhoneSpeaker,
        )
            private set

        internal fun isRequestedEnabled(context: Context): Boolean =
            context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .getBoolean(KEY_ENABLED, false)

        internal fun setEnabled(context: Context, enabled: Boolean): DriverState {
            setRequestedEnabled(context, enabled)
            if (!enabled) {
                context.stopService(Intent(context, AudioFreedomService::class.java))
                currentState = probeDriver()
                currentOutputMetrics = null
                return currentState
            }

            val probeState = probeDriver()
            if (probeState != DriverState.Detected && probeState != DriverState.Attached) {
                setRequestedEnabled(context, false)
                return probeState
            }
            ContextCompat.startForegroundService(
                context,
                Intent(context, AudioFreedomService::class.java),
            )
            currentState = DriverState.Attached
            return currentState
        }

        internal fun updateSettings(
            context: Context,
            settings: AudioFreedomSettings,
        ): DriverState {
            AudioFreedomSettingsStore.save(context, settings)
            if (isRequestedEnabled(context)) {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, AudioFreedomService::class.java)
                        .setAction(ACTION_APPLY_SETTINGS),
                )
            }
            return currentState
        }

        internal fun refreshStatus(context: Context) {
            if (isRequestedEnabled(context)) {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, AudioFreedomService::class.java)
                        .setAction(ACTION_REFRESH_STATUS),
                )
            }
        }

        private fun setRequestedEnabled(context: Context, enabled: Boolean) {
            context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_ENABLED, enabled)
                .apply()
        }
    }
}

private fun rootCause(error: Throwable): Throwable {
    var cause = error
    while (cause.cause != null && cause.cause !== cause) {
        cause = cause.cause!!
    }
    return cause
}
