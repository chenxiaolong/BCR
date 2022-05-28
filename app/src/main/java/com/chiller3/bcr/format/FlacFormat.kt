package com.chiller3.bcr.format

import android.media.MediaFormat
import java.io.FileDescriptor

object FlacFormat: Format() {
    override val name: String = "FLAC"
    override val paramType: FormatParamType = FormatParamType.CompressionLevel
    override val paramRange: UIntRange = 0u..8u
    override val paramStepSize: UInt = 1u
    // Devices are fast enough nowadays to use the highest compression for realtime recording
    override val paramDefault: UInt = 8u
    override val mimeTypeContainer: String = MediaFormat.MIMETYPE_AUDIO_FLAC
    override val mimeTypeAudio: String = MediaFormat.MIMETYPE_AUDIO_FLAC
    override val supported: Boolean = true

    override fun updateMediaFormat(mediaFormat: MediaFormat, param: UInt) {
        mediaFormat.apply {
            // Not relevant for lossless formats
            setInteger(MediaFormat.KEY_BIT_RATE, 0)
            setInteger(MediaFormat.KEY_FLAC_COMPRESSION_LEVEL, param.toInt())
        }
    }

    override fun getContainer(fd: FileDescriptor): Container =
        FlacContainer(fd)
}