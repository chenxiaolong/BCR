/*
 * SPDX-FileCopyrightText: 2025 Andrew Gunnerson
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.chiller3.bcr.settings

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager

class DialerCodeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != TelephonyManager.ACTION_SECRET_CODE) {
            return
        }

        context.startActivity(Intent(context, SettingsActivity::class.java).apply {
            setAction(Intent.ACTION_MAIN)
            setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
}
