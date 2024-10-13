/*
 * SPDX-FileCopyrightText: 2022-2024 Andrew Gunnerson
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.chiller3.bcr.format

import android.media.MediaFormat
import com.chiller3.bcr.extension.SAMPLE_RATE_HZ_MAX_COMPAT
import com.chiller3.bcr.extension.SAMPLE_RATE_HZ_MIN_COMPAT
import java.io.FileDescriptor

data object WaveFormat : Format() {
    override val name: String = "WAV/PCM"
    // Should be "audio/vnd.wave" [1], but Android only recognizes "audio/x-wav" [2] for the
    // purpose of picking an appropriate file extension when creating a file via SAF.
    // [1] https://datatracker.ietf.org/doc/html/rfc2361
    // [2] https://android.googlesource.com/platform/external/mime-support/+/refs/tags/android-12.1.0_r5/mime.types#571
    override val mimeTypeContainer: String = "audio/x-wav"
    override val mimeTypeAudio: String = "audio/x-wav"
    override val passthrough: Boolean = true
    override val paramInfo: FormatParamInfo = NoParamInfo
    override val sampleRateInfo: SampleRateInfo = RangedSampleRateInfo(
        // WAV sample rate field is a 4-byte integer and there's nothing that theoretically prevents
        // using an absurdly large sample rate. However, let's stick to a range that AudioRecord
        // actually supports.
        arrayOf(SAMPLE_RATE_HZ_MIN_COMPAT.toUInt()..SAMPLE_RATE_HZ_MAX_COMPAT.toUInt()),
        16_000u,
    )

    override fun updateMediaFormat(mediaFormat: MediaFormat, param: UInt) {
        // Not needed
    }

    override fun getContainer(fd: FileDescriptor): Container =
        WaveContainer(fd)
}
