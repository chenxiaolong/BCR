@file:OptIn(ExperimentalUnsignedTypes::class)

package com.chiller3.bcr.format

import android.content.Context
import android.media.MediaCodecList
import android.media.MediaFormat
import com.chiller3.bcr.R

sealed class SampleRateInfo(
    val default: UInt,
    /** Fixed sample rate choices to show in the UI as presets. */
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

    companion object {
        fun fromCodec(format: MediaFormat, tryDefault: UInt): SampleRateInfo {
            val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)

            for (info in codecList.codecInfos) {
                if (!info.isEncoder) {
                    continue
                }

                val capabilities = try {
                    info.getCapabilitiesForType(format.getString(MediaFormat.KEY_MIME))
                } catch (e: IllegalArgumentException) {
                    continue
                }

                if (capabilities == null || !capabilities.isFormatSupported(format)) {
                    continue
                }

                val audioCapabilities = capabilities.audioCapabilities
                    ?: throw IllegalArgumentException("${info.name} encoder has no audio capabilities")

                val rates = audioCapabilities.supportedSampleRates
                if (rates != null && rates.isNotEmpty()) {
                    val ratesUnsigned = rates.toUIntArray()
                    val default = DiscreteSampleRateInfo.toNearest(ratesUnsigned, tryDefault)!!

                    return DiscreteSampleRateInfo(ratesUnsigned, default)
                }

                val rateRanges = audioCapabilities.supportedSampleRateRanges
                if (rateRanges.isNotEmpty()) {
                    val rateRangesUnsigned = rateRanges
                        .map { it.lower.toUInt()..it.upper.toUInt() }
                        .toTypedArray()
                    val default = RangedSampleRateInfo.toNearest(rateRangesUnsigned, tryDefault)!!

                    return RangedSampleRateInfo(rateRangesUnsigned, default)
                }
            }

            throw IllegalArgumentException("No suitable encoder found for $format")
        }
    }
}

private fun absDiff(a: UInt, b: UInt): UInt = if (a > b) {
    a - b
} else {
    b - a
}

class RangedSampleRateInfo(
    val ranges: Array<UIntRange>,
    default: UInt,
) : SampleRateInfo(default, uintArrayOf(default)) {
    override fun validate(rate: UInt) {
        if (!ranges.any { rate in it }) {
            throw IllegalArgumentException(
                "Sample rate $rate is not in the ranges: ${ranges.contentToString()}")
        }
    }

    /** Clamp [rate] to the nearest range in [ranges]. */
    override fun toNearest(rate: UInt): UInt = toNearest(ranges, rate)!!

    companion object {
        fun toNearest(ranges: Array<UIntRange>, rate: UInt): UInt? = ranges
            .asSequence()
            .map { rate.coerceIn(it) }
            .minByOrNull { absDiff(rate, it) }
    }
}

class DiscreteSampleRateInfo(
    /** For simplicity, all choices are used as presets. */
    private val choices: UIntArray,
    default: UInt,
) : SampleRateInfo(default, choices) {
    init {
        require(choices.isNotEmpty()) { "List of choices cannot be empty" }
    }

    override fun validate(rate: UInt) {
        if (rate !in choices) {
            throw IllegalArgumentException("Sample rate $rate is not supported: " +
                    choices.contentToString())
        }
    }

    /** Find closest sample rate in [choices] to [rate]. */
    override fun toNearest(rate: UInt): UInt = toNearest(choices, rate)!!

    companion object {
        fun toNearest(choices: UIntArray, rate: UInt): UInt? =
            choices.minByOrNull { absDiff(rate, it) }
    }
}
