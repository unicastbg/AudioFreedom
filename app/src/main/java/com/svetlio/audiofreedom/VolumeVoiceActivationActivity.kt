package com.svetlio.audiofreedom

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver

class VolumeVoiceActivationActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private var requested = false
    private val ready = object : ResultReceiver(handler) {
        override fun onReceiveResult(resultCode: Int, resultData: Bundle?) { finish() }
    }
    private val finishTimeout = Runnable { finish() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        AssistantRuntimeManager.initialize(this)
    }

    override fun onResume() {
        super.onResume()
        if (requested) return
        requested = true
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            AssistantHandsFreeService.activationFailed("Grant microphone permission in AudioFreedom first")
            finish()
            return
        }
        // A visible activity starts the short microphone service under Android's while-in-use rules.
        AssistantHandsFreeService.startCommand(this, ready)
        handler.postDelayed(finishTimeout, 4000)
    }

    override fun onDestroy() {
        handler.removeCallbacks(finishTimeout)
        super.onDestroy()
    }
}
