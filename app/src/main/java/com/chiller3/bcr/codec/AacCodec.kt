package com.chiller3.bcr.codec

import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.FileDescriptor

object AacCodec : Codec() {
    override val name: String = "M4A/AAC"
    override val paramType: CodecParamType = CodecParamType.Bitrate
    // The codec has no hard limits, so the lower bound is ffmpeg's recommended minimum bitrate for
    // HE-AAC: 24kbps/channel. The upper bound is twice the bitrate for audible transparency with
    // AAC-LC: 2 * 64kbps/channel.
    // https://trac.ffmpeg.org/wiki/Encode/AAC
    override val paramRange: UIntRange = 24_000u..128_000u
    override val paramDefault: UInt = 64_000u
    // https://datatracker.ietf.org/doc/html/rfc6381#section-3.1
    override val mimeTypeContainer: String = "audio/mp4"
    override val mimeTypeAudio: String = MediaFormat.MIMETYPE_AUDIO_AAC
    override val supported: Boolean = true

    override fun updateMediaFormat(mediaFormat: MediaFormat, param: UInt) {
        mediaFormat.apply {
            val profile = if (param >= 32_000u) {
                MediaCodecInfo.CodecProfileLevel.AACObjectLC
            } else {
                MediaCodecInfo.CodecProfileLevel.AACObjectHE
            }
            val channelCount = getInteger(MediaFormat.KEY_CHANNEL_COUNT)

            setInteger(MediaFormat.KEY_AAC_PROFILE, profile)
            setInteger(MediaFormat.KEY_BIT_RATE, param.toInt() * channelCount)
        }
    }

    override fun getContainer(fd: FileDescriptor): Container =
        MediaMuxerContainer(fd, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
}