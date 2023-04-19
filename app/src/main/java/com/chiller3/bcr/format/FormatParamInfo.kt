@file:OptIn(ExperimentalUnsignedTypes::class)

package com.chiller3.bcr.format

sealed class FormatParamInfo(
    val default: UInt,
    /** Handful of handpicked parameter choices to show in the UI as presets. */
    val presets: UIntArray,
) {
    /**
     * Ensure that [param] is valid.
     *
     * @throws IllegalArgumentException if [param] is invalid
     */
    abstract fun validate(param: UInt)

    /**
     * Convert a potentially-invalid [param] value to the nearest valid value.
     */
    abstract fun toNearest(param: UInt): UInt

    /**
     * Format [param] to present as a user-facing string.
     */
    abstract fun format(param: UInt): String
}

enum class RangedParamType {
    CompressionLevel,
    Bitrate,
}

class RangedParamInfo(
    val type: RangedParamType,
    val range: UIntRange,
    default: UInt,
    presets: UIntArray,
) : FormatParamInfo(default, presets) {
    override fun validate(param: UInt) {
        if (param !in range) {
            throw IllegalArgumentException("Parameter ${format(param)} is not in the range: " +
                    "[${format(range.first)}, ${format(range.last)}]")
        }
    }

    /** Clamp [param] to [range]. */
    override fun toNearest(param: UInt): UInt = param.coerceIn(range)

    override fun format(param: UInt): String =
        when (type) {
            RangedParamType.CompressionLevel -> param.toString()
            RangedParamType.Bitrate -> "${param / 1_000u} kbps"
        }
}

object NoParamInfo : FormatParamInfo(0u, uintArrayOf()) {
    override fun validate(param: UInt) {
        // Always valid
    }

    override fun toNearest(param: UInt): UInt = param

    override fun format(param: UInt): String = ""
}
