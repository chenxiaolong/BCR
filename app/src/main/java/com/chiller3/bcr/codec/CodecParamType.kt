package com.chiller3.bcr.codec

enum class CodecParamType {
    /** For lossless codecs. Represents a codec-specific arbitrary integer. */
    CompressionLevel {
        override fun format(param: UInt): String = param.toString()
    },

    /** For lossy codecs. Represents a bitrate *per channel* in bits per second. */
    Bitrate {
        override fun format(param: UInt): String = "${param / 1_000u} kbps"
    };

    abstract fun format(param: UInt): String
}