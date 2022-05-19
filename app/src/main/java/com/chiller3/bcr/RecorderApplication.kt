package com.chiller3.bcr

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.google.android.material.color.DynamicColors

class RecorderApplication : Application() {
    companion object {
        const val CHANNEL_ID = "persistent"
    }

    override fun onCreate() {
        super.onCreate()

        // Enable Material You colors
        DynamicColors.applyToActivitiesIfAvailable(this)

        createNotificationChannel()
    }

    /**
     * Create a low priority notification channel for the persistent notification.
     */
    private fun createNotificationChannel() {
        val name: CharSequence = getString(R.string.notification_channel_persistent_name)
        val description = getString(R.string.notification_channel_persistent_desc)
        val channel = NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_LOW)
        channel.description = description

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }
}