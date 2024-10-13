/*
 * SPDX-FileCopyrightText: 2024 Andrew Gunnerson
 * SPDX-License-Identifier: GPL-3.0-only
 */

@file:OptIn(ExperimentalUnsignedTypes::class)

package com.chiller3.bcr.format

import android.media.MediaFormat
import java.io.FileDescriptor

class AmrWbFormat : Format() {
    override val name: String = "AMR-WB"
    override val mimeTypeContainer: String = MediaFormat.MIMETYPE_AUDIO_AMR_WB
    override val mimeTypeAudio: String = mimeTypeContainer
    override val passthrough: Boolean = false
    override val paramInfo: FormatParamInfo = RangedParamInfo(
        RangedParamType.Bitrate,
        6_600u..23_850u,
        23_850u,
        // AMR-WB only supports 9 possible bit rates. If the user picks a bit rate that's not one
        // of the 9 possibilities, then Android will fall back to 23050 bits/second.
        uintArrayOf(
            6_600u,
            15_850u,
            23_850u,
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

    override fun getContainer(fd: FileDescriptor): Container = AmrContainer(fd, true)
}
