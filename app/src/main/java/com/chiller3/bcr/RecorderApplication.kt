package com.chiller3.bcr

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.google.android.material.color.DynamicColors

class RecorderApplication : Application() {
    companion object {
        const val CHANNEL_ID_PERSISTENT = "persistent"
        const val CHANNEL_ID_ALERTS = "alerts"
    }

    override fun onCreate() {
        super.onCreate()

        // Enable Material You colors
        DynamicColors.applyToActivitiesIfAvailable(this)

        createPersistentChannel()
        createAlertsChannel()
    }

    /**
     * Create a low priority notification channel for the persistent notification.
     */
    private fun createPersistentChannel() {
        val name = getString(R.string.notification_channel_persistent_name)
        val description = getString(R.string.notification_channel_persistent_desc)
        val channel = NotificationChannel(
            CHANNEL_ID_PERSISTENT, name, NotificationManager.IMPORTANCE_LOW)
        channel.description = description

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    /**
     * Create a high priority notification channel for alerts.
     */
    private fun createAlertsChannel() {
        val name = getString(R.string.notification_channel_alerts_name)
        val description = getString(R.string.notification_channel_alerts_desc)
        val channel = NotificationChannel(
            CHANNEL_ID_ALERTS, name, NotificationManager.IMPORTANCE_HIGH)
        channel.description = description

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }
}