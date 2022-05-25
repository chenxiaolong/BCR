package com.chiller3.bcr.codec

enum class CodecParamType {
    /** For lossless codecs. Represents a codec-specific arbitrary integer. */
    CompressionLevel,
    /** For lossy codecs. Represents a bitrate *per channel* in bits per second. */
    Bitrate,
}