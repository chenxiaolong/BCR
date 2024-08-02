package com.chiller3.bcr

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class DirectBootMigrationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) {
            return
        }

        context.startService(Intent(context, DirectBootMigrationService::class.java))
    }
}
