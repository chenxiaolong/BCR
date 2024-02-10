@file:OptIn(ExperimentalUnsignedTypes::class)

package com.chiller3.bcr.format

import android.media.MediaFormat
import java.io.FileDescriptor

data object AmrNbFormat : Format() {
    override val name: String = "AMR-NB"
    override val paramInfo: FormatParamInfo = RangedParamInfo(
        RangedParamType.Bitrate,
        4_750u..12_200u,
        12_200u,
        // AMR-NB only supports 8 possible bit rates. If the user picks a bit rate that's not one
        // of the 8 possibilities, then Android will fall back to 7950 bits/second.
        uintArrayOf(
            4_750u,
            7_950u,
            12_200u,
        ),
    )
    override val sampleRateInfo: SampleRateInfo = DiscreteSampleRateInfo(
        uintArrayOf(8_000u),
        8_000u,
    )
    override val mimeTypeContainer: String = "audio/amr"
    override val mimeTypeAudio: String = MediaFormat.MIMETYPE_AUDIO_AMR_NB
    override val passthrough: Boolean = false
    override val supported: Boolean = true

    override fun updateMediaFormat(mediaFormat: MediaFormat, param: UInt) {
        mediaFormat.apply {
            val channelCount = getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            setInteger(MediaFormat.KEY_BIT_RATE, param.toInt() * channelCount)
        }
    }

    override fun getContainer(fd: FileDescriptor): Container = AmrContainer(fd, false)
}
