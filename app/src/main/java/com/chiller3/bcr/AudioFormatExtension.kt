package com.chiller3.bcr

import android.media.AudioFormat

val AudioFormat.frameSizeInBytesCompat: Int
    get() = frameSizeInBytes
