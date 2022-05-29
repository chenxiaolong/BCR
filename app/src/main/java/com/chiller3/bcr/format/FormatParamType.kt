package com.chiller3.bcr.format

enum class FormatParamType {
    /** For lossless formats. Represents a format-specific arbitrary integer. */
    CompressionLevel {
        override fun format(param: UInt): String = param.toString()
    },

    /** For lossy formats. Represents a bitrate *per channel* in bits per second. */
    Bitrate {
        override fun format(param: UInt): String = "${param / 1_000u} kbps"
    };

    abstract fun format(param: UInt): String
}