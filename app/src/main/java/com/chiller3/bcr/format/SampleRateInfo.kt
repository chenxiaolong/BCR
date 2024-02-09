@file:OptIn(ExperimentalUnsignedTypes::class)

package com.chiller3.bcr.format

import android.content.Context
import com.chiller3.bcr.R

sealed class SampleRateInfo(
    val default: UInt,
    /** Handful of handpicked sample rate choices to show in the UI as presets. */
    val presets: UIntArray,
) {
    /**
     * Ensure that [rate] is valid.
     *
     * @throws IllegalArgumentException if [rate] is invalid
     */
    abstract fun validate(rate: UInt)

    /**
     * Convert a potentially-invalid [rate] value to the nearest valid value.
     */
    abstract fun toNearest(rate: UInt): UInt

    /**
     * Format [rate] to present as a user-facing string.
     */
    fun format(context: Context, rate: UInt): String =
        context.getString(R.string.format_sample_rate, rate.toString())
}

class RangedSampleRateInfo(
    val range: UIntRange,
    default: UInt,
    presets: UIntArray,
) : SampleRateInfo(default, presets) {
    override fun validate(rate: UInt) {
        if (rate !in range) {
            throw IllegalArgumentException("Sample rate $rate is not in the range: " +
                    "[${range.first}, ${range.last}]")
        }
    }

    /** Clamp [rate] to [range]. */
    override fun toNearest(rate: UInt): UInt = rate.coerceIn(range)
}

class DiscreteSampleRateInfo(
    /** For simplicity, all choices are used as presets. */
    private val choices: UIntArray,
    default: UInt,
) : SampleRateInfo(default, choices) {
    override fun validate(rate: UInt) {
        if (rate !in choices) {
            throw IllegalArgumentException("Sample rate $rate is not supported: " +
                    choices.contentToString())
        }
    }

    /** Find closest sample rate in [choices] to [rate]. */
    override fun toNearest(rate: UInt): UInt = choices.minBy {
        if (it > rate) {
            it - rate
        } else {
            rate - it
        }
    }
}
