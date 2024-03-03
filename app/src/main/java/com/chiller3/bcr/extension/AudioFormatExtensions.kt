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

// Static extension functions are currently not supported in Kotlin. Also, we install a sysconfig
// file to allow access to these hidden fields.

@SuppressLint("SoonBlockedPrivateApi")
val SAMPLE_RATE_HZ_MIN_COMPAT: Int =
    AudioFormat::class.java.getDeclaredField("SAMPLE_RATE_HZ_MIN").getInt(null)

@SuppressLint("SoonBlockedPrivateApi")
val SAMPLE_RATE_HZ_MAX_COMPAT: Int =
    AudioFormat::class.java.getDeclaredField("SAMPLE_RATE_HZ_MAX").getInt(null)
