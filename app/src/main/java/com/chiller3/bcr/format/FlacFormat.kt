/*
 * SPDX-FileCopyrightText: 2022-2024 Andrew Gunnerson
 * SPDX-License-Identifier: GPL-3.0-only
 */

@file:OptIn(ExperimentalUnsignedTypes::class)

package com.chiller3.bcr.format

import android.media.MediaFormat
import java.io.FileDescriptor

class FlacFormat : Format() {
    override val name: String = "FLAC"
    override val mimeTypeContainer: String = MediaFormat.MIMETYPE_AUDIO_FLAC
    override val mimeTypeAudio: String = MediaFormat.MIMETYPE_AUDIO_FLAC
    override val passthrough: Boolean = false
    override val paramInfo: FormatParamInfo = RangedParamInfo(
        RangedParamType.CompressionLevel,
        0u..8u,
        // Devices are fast enough nowadays to use the highest compression for realtime recording
        8u,
        uintArrayOf(0u, 5u, 8u),
    )
    override val sampleRateInfo: SampleRateInfo =
        SampleRateInfo.fromCodec(baseMediaFormat, 16_000u)

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
