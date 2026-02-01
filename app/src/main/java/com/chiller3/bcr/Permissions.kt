/*
 * SPDX-FileCopyrightText: 2022-2026 Andrew Gunnerson
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.chiller3.bcr

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
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
    val OPTIONAL: Array<String> = arrayOf(
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.READ_PHONE_STATE,
    )

    /**
     * Check if all permissions required for call recording have been granted.
     */
    fun haveRequired(context: Context): Boolean =
        REQUIRED.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

    /**
     * Get intent for opening the app info page in the system settings.
     */
    fun getAppInfoIntent(context: Context) = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null),
    )
}
