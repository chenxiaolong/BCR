package com.chiller3.bcr.codec

import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import androidx.annotation.RequiresApi
import java.io.FileDescriptor

object OpusCodec : Codec() {
    override val name: String = "OGG/Opus"
    override val paramType: CodecParamType = CodecParamType.Bitrate
    override val paramRange: UIntRange = 6_000u..510_000u
    override val paramStepSize: UInt = 2_000u
    // "Essentially transparent mono or stereo speech, reasonable music"
    // https://wiki.hydrogenaud.io/index.php?title=Opus
    override val paramDefault: UInt = 48_000u
    // https://datatracker.ietf.org/doc/html/rfc7845#section-9
    override val mimeTypeContainer: String = "audio/ogg"
    override val mimeTypeAudio: String = MediaFormat.MIMETYPE_AUDIO_OPUS
    override val supported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    override fun updateMediaFormat(mediaFormat: MediaFormat, param: UInt) {
        mediaFormat.apply {
            val channelCount = getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            setInteger(MediaFormat.KEY_BIT_RATE, param.toInt() * channelCount)
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun getContainer(fd: FileDescriptor): Container =
        MediaMuxerContainer(fd, MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG)
}