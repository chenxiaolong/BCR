package com.chiller3.bcr.codec

import android.media.AudioFormat
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.FileDescriptor

class AacCodec : Codec() {
    override val codecParamType: CodecParamType = CodecParamType.Bitrate
    // The codec has no hard limits, so the lower bound is ffmpeg's recommended minimum bitrate for
    // HE-AAC: 24kbps/channel. The upper bound is twice the bitrate for audible transparency with
    // AAC-LC: 2 * 64kbps/channel.
    // https://trac.ffmpeg.org/wiki/Encode/AAC
    override val codecParamRange: UIntRange = 24_000u..128_000u
    override val codecParamDefault: UInt = 64_000u
    // https://datatracker.ietf.org/doc/html/rfc6381#section-3.1
    override val mimeTypeContainer: String = "audio/mp4"
    override val mimeTypeAudio: String = MediaFormat.MIMETYPE_AUDIO_AAC
    override val supported: Boolean = true

    override fun getMediaFormat(audioFormat: AudioFormat, sampleRate: Int): MediaFormat =
        super.getMediaFormat(audioFormat, sampleRate).apply {
            val profile = if (codecParamValue >= 32_000u) {
                MediaCodecInfo.CodecProfileLevel.AACObjectLC
            } else {
                MediaCodecInfo.CodecProfileLevel.AACObjectHE
            }

            setInteger(MediaFormat.KEY_AAC_PROFILE, profile)
            setInteger(MediaFormat.KEY_BIT_RATE, codecParamValue.toInt() * audioFormat.channelCount)
        }

    override fun getContainer(fd: FileDescriptor): Container =
        MediaMuxerContainer(fd, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
}