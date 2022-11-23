package com.chiller3.bcr

import android.app.Application
import com.google.android.material.color.DynamicColors

class RecorderApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Enable Material You colors
        DynamicColors.applyToActivitiesIfAvailable(this)

        Notifications(this).updateChannels()
    }
}