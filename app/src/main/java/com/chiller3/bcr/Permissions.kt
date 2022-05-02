package com.chiller3.bcr

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.os.BuildCompat

object Permissions {
    private val REQUIRED_33: Array<String> = arrayOf(Manifest.permission.POST_NOTIFICATIONS)

    @BuildCompat.PrereleaseSdkCheck
    val REQUIRED: Array<String> = if (BuildCompat.isAtLeastT()) { REQUIRED_33 } else { arrayOf() } +
           arrayOf(Manifest.permission.RECORD_AUDIO)

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