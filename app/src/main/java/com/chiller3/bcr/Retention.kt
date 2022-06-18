@file:Suppress("OPT_IN_IS_NOT_ENABLED")
@file:OptIn(ExperimentalUnsignedTypes::class)

package com.chiller3.bcr

import android.content.Context

object Retention {
    val all = uintArrayOf(
        1u,
        7u,
        30u,
        90u,
        182u,
        365u,
        // Keep forever
        0u,
    )
    val default = all.last()

    /**
     * Get the saved retention in days from the preferences.
     *
     * If the saved sample rate is no longer valid or no sample rate is selected, then [default]
     * is returned.
     */
    fun fromPreferences(prefs: Preferences): UInt {
        val savedRetention = prefs.outputRetention

        if (savedRetention != null && all.contains(savedRetention)) {
            return savedRetention
        }

        return default
    }

    fun format(context: Context, days: UInt): String =
        if (days == 0u) {
            context.getString(R.string.retention_keep_all)
        } else {
            context.resources.getQuantityString(R.plurals.retention_days, days.toInt(), days.toInt())
        }
}