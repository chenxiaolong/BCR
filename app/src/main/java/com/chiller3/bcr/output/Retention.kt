/*
 * SPDX-FileCopyrightText: 2022-2026 Andrew Gunnerson
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.chiller3.bcr.output

import android.content.res.Resources
import com.chiller3.bcr.Preferences
import com.chiller3.bcr.R
import java.time.Duration

sealed interface Retention {
    fun toFormattedString(resources: Resources): String

    fun toRawPreferenceValue(): UInt

    companion object {
        val default = NoRetention

        fun fromRawPreferenceValue(value: UInt): Retention = if (value == 0u) {
            NoRetention
        } else {
            DaysRetention(value)
        }

        fun fromPreferences(prefs: Preferences): Retention = prefs.outputRetention ?: default
    }
}

data object NoRetention : Retention {
    override fun toFormattedString(resources: Resources): String =
        resources.getString(R.string.retention_keep_all)

    override fun toRawPreferenceValue(): UInt = 0u
}

@JvmInline
value class DaysRetention(val days: UInt) : Retention {
    override fun toFormattedString(resources: Resources): String =
        resources.getQuantityString(R.plurals.retention_days, days.toInt(), days.toInt())

    override fun toRawPreferenceValue(): UInt = days

    fun toDuration(): Duration = Duration.ofDays(days.toLong())
}
