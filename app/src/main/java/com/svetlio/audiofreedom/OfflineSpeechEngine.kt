package com.svetlio.audiofreedom

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.tts.TextToSpeech

internal fun hasInstalledSpeechEngine(context: Context): Boolean =
    context.packageManager.queryIntentServices(
        Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE),
        PackageManager.ResolveInfoFlags.of(0),
    ).isNotEmpty()
