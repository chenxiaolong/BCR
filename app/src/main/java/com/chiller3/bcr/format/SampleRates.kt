@file:Suppress("OPT_IN_IS_NOT_ENABLED")
@file:OptIn(ExperimentalUnsignedTypes::class)

package com.chiller3.bcr.format

import com.chiller3.bcr.Preferences

object SampleRates {
    /**
     * Hardcoded list of sample rates supported by every [Format].
     *
     * Ideally, there would be a way to query what sample rates are supported for a given audio
     * source and then filter that list based on what the [Format] supports. Unfortunately, no such
     * API exists.
     */
    val all = uintArrayOf(
        8_000u,
        12_000u,
        16_000u,
        24_000u,
        48_000u,
    )
    val default = all.last()

    /**
     * Get the saved sample rate from the preferences.
     *
     * If the saved sample rate is no longer valid or no sample rate is selected, then [default]
     * is returned.
     */
    fun fromPreferences(prefs: Preferences): UInt {
        val savedSampleRate = prefs.sampleRate

        if (savedSampleRate != null && all.contains(savedSampleRate)) {
            return savedSampleRate
        }

        return default
    }

    fun format(sampleRate: UInt): String = "$sampleRate Hz"
}