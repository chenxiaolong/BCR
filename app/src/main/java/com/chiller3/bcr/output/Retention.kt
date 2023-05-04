package com.chiller3.bcr.output

import android.content.Context
import com.chiller3.bcr.Preferences
import com.chiller3.bcr.R
import java.time.Duration

sealed interface Retention {
    fun toFormattedString(context: Context): String

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

object NoRetention : Retention {
    override fun toFormattedString(context: Context): String =
        context.getString(R.string.retention_keep_all)

    override fun toRawPreferenceValue(): UInt = 0u
}

@JvmInline
value class DaysRetention(val days: UInt) : Retention {
    override fun toFormattedString(context: Context): String =
        context.resources.getQuantityString(R.plurals.retention_days, days.toInt(), days.toInt())

    override fun toRawPreferenceValue(): UInt = days

    fun toDuration(): Duration = Duration.ofDays(days.toLong())
}
