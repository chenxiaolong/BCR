@file:OptIn(ExperimentalUnsignedTypes::class)

package com.chiller3.bcr.format

import android.content.Context
import com.chiller3.bcr.R

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
    abstract fun format(context: Context, param: UInt): String
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
            throw IllegalArgumentException("Parameter $param is not in the range: " +
                    "[${range.first}, ${range.last}]")
        }
    }

    /** Clamp [param] to [range]. */
    override fun toNearest(param: UInt): UInt = param.coerceIn(range)

    override fun format(context: Context, param: UInt): String =
        when (type) {
            RangedParamType.CompressionLevel ->
                context.getString(R.string.format_param_compression_level, param.toString())
            RangedParamType.Bitrate ->
                context.getString(R.string.format_param_bitrate, (param / 1_000U).toString())
        }
}

object NoParamInfo : FormatParamInfo(0u, uintArrayOf()) {
    override fun validate(param: UInt) {
        // Always valid
    }

    override fun toNearest(param: UInt): UInt = param

    override fun format(context: Context, param: UInt): String = ""
}
