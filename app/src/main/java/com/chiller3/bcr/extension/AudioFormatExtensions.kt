/*
 * SPDX-FileCopyrightText: 2022-2024 Andrew Gunnerson
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.chiller3.bcr.extension

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.os.Build

val AudioFormat.frameSizeInBytesCompat: Int
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        frameSizeInBytes
    } else{
        // Hardcoded for Android 9 compatibility only
        assert(encoding == AudioFormat.ENCODING_PCM_16BIT)
        2 * channelCount
    }

// Static extension functions are currently not supported in Kotlin. Also, we both install a
// sysconfig file and set usesNonSdkApi to allow access to these hidden fields. The usesNonSdkApi
// flag is more reliable, but the sysconfig file is needed on older versions of Android that are
// missing AOSP commit ca6f81d39525174e926c2fcc824fe9531ffb3563.

@SuppressLint("SoonBlockedPrivateApi")
val SAMPLE_RATE_HZ_MIN_COMPAT: Int =
    AudioFormat::class.java.getDeclaredField("SAMPLE_RATE_HZ_MIN").getInt(null)

@SuppressLint("SoonBlockedPrivateApi")
val SAMPLE_RATE_HZ_MAX_COMPAT: Int =
    AudioFormat::class.java.getDeclaredField("SAMPLE_RATE_HZ_MAX").getInt(null)
