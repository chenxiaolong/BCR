package com.chiller3.bcr.extension

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
