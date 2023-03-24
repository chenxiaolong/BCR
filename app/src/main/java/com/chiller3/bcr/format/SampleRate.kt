package com.chiller3.bcr.format

import com.chiller3.bcr.Preferences

@JvmInline
value class SampleRate(val value: UInt) {
    override fun toString(): String = "$value Hz"

    companion object {
        /**
         * Hardcoded list of sample rates supported by every [Format].
         *
         * Ideally, there would be a way to query what sample rates are supported for a given audio
         * source and then filter that list based on what the [Format] supports. Unfortunately, no such
         * API exists.
         */
        val all = arrayOf(
            SampleRate(8_000u),
            SampleRate(12_000u),
            SampleRate(16_000u),
            SampleRate(24_000u),
            SampleRate(48_000u),
        )
        val default = all.last()

        /**
         * Get the saved sample rate from the preferences.
         *
         * If the saved sample rate is no longer valid or no sample rate is selected, then [default]
         * is returned.
         */
        fun fromPreferences(prefs: Preferences): SampleRate {
            val savedSampleRate = prefs.sampleRate

            if (savedSampleRate != null && all.contains(savedSampleRate)) {
                return savedSampleRate
            }

            return default
        }
    }
}
