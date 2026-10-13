/*
 * SPDX-FileCopyrightText: 2022-2026 Andrew Gunnerson
 * SPDX-License-Identifier: GPL-3.0-only
 */

@file:OptIn(ExperimentalUnsignedTypes::class)

package com.chiller3.bcr.format

import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import java.io.FileDescriptor

class AacFormat : Format() {
    override val name: String = "M4A/AAC"
    // https://datatracker.ietf.org/doc/html/rfc6381#section-3.1
    override val mimeTypeContainer: String = "audio/mp4"
    override val mimeTypeAudio: String = MediaFormat.MIMETYPE_AUDIO_AAC
    override val passthrough: Boolean = false
    override val paramInfo: FormatParamInfo = RangedParamInfo(
        RangedParamType.Bitrate,
        // The format has no hard limits, so the lower bound is ffmpeg's recommended minimum bitrate
        // for HE-AAC: 24kbps/channel. The upper bound is twice the bitrate for audible transparency
        // with AAC-LC: 2 * 64kbps/channel.
        // https://trac.ffmpeg.org/wiki/Encode/AAC
        24_000u..128_000u,
        64_000u,
        uintArrayOf(
            24_000u,
            // "As a rule of thumb, for audible transparency, use 64 kBit/s for each channel"
            64_000u,
            128_000u,
        ),
    )
    override val sampleRateInfo: SampleRateInfo =
        SampleRateInfo.fromCodec(baseMediaFormat, 16_000u)
    override val supportsStereo: Boolean = true

    override fun updateMediaFormat(mediaFormat: MediaFormat, param: UInt) {
        mediaFormat.apply {
            // Android 9, 10, 12, 12L, 13, and 14 have a broken HE-AAC encoder that produces files
            // ffmpeg fails to decode with:
            //
            //   [ffmpeg/audio] aac: Reserved bit set.
            //   [ffmpeg/audio] aac: Number of scalefactor bands in group (48) exceeds limit (40).
            //
            // Android 11 produces files that ffmpeg fails to decode with:
            //
            //   [ffmpeg/audio] aac: Input buffer exhausted before END element found
            //
            // VLC also fails to decode these files. Android 15 and newer produce files that play
            // back fine without any warnings or errors.
            val profile = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM
                    || param >= 32_000u) {
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
