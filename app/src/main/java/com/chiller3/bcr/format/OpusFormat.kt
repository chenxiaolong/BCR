package com.chiller3.bcr.format

import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import androidx.annotation.RequiresApi
import java.io.FileDescriptor

object OpusFormat : Format() {
    override val name: String = "OGG/Opus"
    override val paramInfo: FormatParamInfo = RangedParamInfo(
        RangedParamType.Bitrate,
        6_000u..510_000u,
        21_000u,
        // "Essentially transparent mono or stereo speech, reasonable music"
        // https://wiki.hydrogenaud.io/index.php?title=Opus
        48_000u,
    )
    // https://datatracker.ietf.org/doc/html/rfc7845#section-9
    override val mimeTypeContainer: String = "audio/ogg"
    override val mimeTypeAudio: String = MediaFormat.MIMETYPE_AUDIO_OPUS
    override val passthrough: Boolean = false
    override val supported: Boolean = true

    override fun updateMediaFormat(mediaFormat: MediaFormat, param: UInt) {
        mediaFormat.apply {
            val channelCount = getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            setInteger(MediaFormat.KEY_BIT_RATE, param.toInt() * channelCount)
        }
    }

    override fun getContainer(fd: FileDescriptor): Container =
        MediaMuxerContainer(fd, MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG)
}
