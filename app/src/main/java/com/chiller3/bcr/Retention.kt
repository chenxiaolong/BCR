package com.chiller3.bcr

import android.content.Context
import java.time.Duration

sealed interface Retention {
    fun toFormattedString(context: Context): String

    fun toRawPreferenceValue(): UInt

    companion object {
        val all = arrayOf(
            DaysRetention(1u),
            DaysRetention(7u),
            DaysRetention(30u),
            DaysRetention(90u),
            DaysRetention(182u),
            DaysRetention(365u),
            NoRetention,
        )
        val default = all.last()

        fun fromRawPreferenceValue(value: UInt): Retention = if (value == 0u) {
            NoRetention
        } else {
            DaysRetention(value)
        }

        /**
         * Get the saved retention in days from the preferences.
         *
         * If the saved sample rate is no longer valid or no sample rate is selected, then [default]
         * is returned.
         */
        fun fromPreferences(prefs: Preferences): Retention {
            val savedRetention = prefs.outputRetention

            if (savedRetention != null && all.contains(savedRetention)) {
                return savedRetention
            }

            return default
        }
    }
}

object NoRetention : Retention {
    override fun toFormattedString(context: Context): String =
        context.getString(R.string.retention_keep_all)

    override fun toRawPreferenceValue(): UInt = 0u
}

@JvmInline
value class DaysRetention(private val days: UInt) : Retention {
    override fun toFormattedString(context: Context): String =
        context.resources.getQuantityString(R.plurals.retention_days, days.toInt(), days.toInt())

    override fun toRawPreferenceValue(): UInt = days

    fun toDuration(): Duration = Duration.ofDays(days.toLong())
}
