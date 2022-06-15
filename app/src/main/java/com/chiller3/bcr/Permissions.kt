package com.chiller3.bcr

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat

object Permissions {
    private val NOTIFICATION: Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            arrayOf()
        }

    val REQUIRED: Array<String> = arrayOf(Manifest.permission.RECORD_AUDIO) + NOTIFICATION
    val OPTIONAL: Array<String> = arrayOf(Manifest.permission.READ_CONTACTS)

    private fun isGranted(context: Context, permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /**
     * Check if all permissions required for call recording have been granted.
     */
    fun haveRequired(context: Context): Boolean =
        REQUIRED.all { isGranted(context, it) }

    /**
     * Check if battery optimizations are currently disabled for this app.
     */
    fun isInhibitingBatteryOpt(context: Context): Boolean {
        val pm: PowerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Get intent for opening the app info page in the system settings.
     */
    fun getAppInfoIntent(context: Context): Intent {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        intent.data = Uri.fromParts("package", context.packageName, null)
        return intent
    }

    /**
     * Get intent for requesting the disabling of battery optimization for this app.
     */
    @SuppressLint("BatteryLife")
    fun getInhibitBatteryOptIntent(context: Context): Intent {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
        intent.data = Uri.fromParts("package", context.packageName, null)
        return intent
    }
}