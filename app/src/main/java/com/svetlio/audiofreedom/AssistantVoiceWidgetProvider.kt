package com.svetlio.audiofreedom

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class AssistantVoiceWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        appWidgetIds.forEach { appWidgetId ->
            val openVoicePanel = PendingIntent.getActivity(
                context,
                appWidgetId,
                Intent(context, VoiceCommandWidgetActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP,
                ),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val views = RemoteViews(context.packageName, R.layout.widget_assistant_voice)
            views.setOnClickPendingIntent(R.id.assistant_voice_widget, openVoicePanel)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}

internal fun requestAssistantVoiceWidget(context: Context): String {
    val manager = AppWidgetManager.getInstance(context)
    val provider = ComponentName(context, AssistantVoiceWidgetProvider::class.java)
    val registered = manager
        .getInstalledProvidersForPackage(context.packageName, null)
        .any { it.provider == provider }
    if (!registered) {
        return "Android has not registered the AudioFreedom widget yet. Restart the phone after installing this update."
    }
    if (!manager.isRequestPinAppWidgetSupported) {
        return "This launcher does not support adding widgets from inside apps. Use its widget picker instead."
    }
    return if (manager.requestPinAppWidget(provider, null, null)) {
        "Confirm the Add to Home screen request from your launcher."
    } else {
        "The launcher declined the widget request. Check whether home screen changes are locked."
    }
}
