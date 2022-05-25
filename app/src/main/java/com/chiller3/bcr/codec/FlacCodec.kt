package com.chiller3.bcr.codec

import android.media.AudioFormat
import android.media.MediaFormat
import java.io.FileDescriptor

class FlacCodec: Codec() {
    override val codecParamType: CodecParamType = CodecParamType.CompressionLevel
    override val codecParamRange: UIntRange = 0u..8u
    // Devices are fast enough nowadays to use the highest compression for realtime recording
    override var codecParamDefault: UInt = 8u
    override var mimeTypeContainer: String = MediaFormat.MIMETYPE_AUDIO_FLAC
    override var mimeTypeAudio: String = MediaFormat.MIMETYPE_AUDIO_FLAC
    override var supported: Boolean = true

    override fun getMediaFormat(audioFormat: AudioFormat, sampleRate: Int): MediaFormat =
        super.getMediaFormat(audioFormat, sampleRate).apply {
            // Not relevant for lossless formats
            setInteger(MediaFormat.KEY_BIT_RATE, 0)
            setInteger(MediaFormat.KEY_FLAC_COMPRESSION_LEVEL, codecParamValue.toInt())
        }

    override fun getContainer(fd: FileDescriptor): Container =
        FlacContainer(fd)
}