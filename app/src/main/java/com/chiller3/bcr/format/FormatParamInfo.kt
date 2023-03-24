package com.chiller3.bcr.format

sealed class FormatParamInfo(val default: UInt) {
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
    val stepSize: UInt,
    default: UInt,
) : FormatParamInfo(default) {
    override fun validate(param: UInt) {
        if (param !in range) {
            throw IllegalArgumentException("Parameter ${format(param)} is not in the range: " +
                    "[${format(range.first)}, ${format(range.last)}]")
        }
    }

    /** Clamp [param] to [range] and snap to nearest [stepSize]. */
    override fun toNearest(param: UInt): UInt {
        val offset = param.coerceIn(range) - range.first
        val roundedDown = (offset / stepSize) * stepSize

        return range.first + if (roundedDown == offset) {
            // Already on step size boundary
            offset
        } else if (roundedDown >= UInt.MAX_VALUE - stepSize) {
            // Rounded up would overflow
            roundedDown
        } else {
            // Round to closer boundary, preferring the upper boundary if it's in the middle
            val roundedUp = roundedDown + stepSize
            if (roundedUp - offset <= offset - roundedDown) {
                roundedUp
            } else {
                roundedDown
            }
        }
    }

    override fun format(param: UInt): String =
        when (type) {
            RangedParamType.CompressionLevel -> param.toString()
            RangedParamType.Bitrate -> "${param / 1_000u} kbps"
        }
}

object NoParamInfo : FormatParamInfo(0u) {
    override fun validate(param: UInt) {
        // Always valid
    }

    override fun toNearest(param: UInt): UInt = param

    override fun format(param: UInt): String = ""
}
