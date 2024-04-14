@file:OptIn(ExperimentalUnsignedTypes::class)

package com.chiller3.bcr.format

import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import androidx.annotation.RequiresApi
import java.io.FileDescriptor

class OpusFormat : Format() {
    init {
        require(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "Only supported on Android 10 and newer"
        }
    }

    override val name: String = "OGG/Opus"
    // https://datatracker.ietf.org/doc/html/rfc7845#section-9
    override val mimeTypeContainer: String = "audio/ogg"
    override val mimeTypeAudio: String = MediaFormat.MIMETYPE_AUDIO_OPUS
    override val passthrough: Boolean = false
    override val paramInfo: FormatParamInfo = RangedParamInfo(
        RangedParamType.Bitrate,
        6_000u..510_000u,
        48_000u,
        // https://wiki.hydrogenaud.io/index.php?title=Opus
        uintArrayOf(
            // "Medium bandwidth, better than telephone quality"
            12_000u,
            // "Near transparent speech"
            24_000u,
            // "Essentially transparent mono or stereo speech, reasonable music"
            48_000u,
        ),
    )
    override val sampleRateInfo: SampleRateInfo =
        SampleRateInfo.fromCodec(baseMediaFormat, 16_000u)

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
