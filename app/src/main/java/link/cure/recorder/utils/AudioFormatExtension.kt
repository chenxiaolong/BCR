package link.cure.recorder.utils

import android.media.AudioFormat

val AudioFormat.frameSizeInBytesCompat: Int
    get() =
        frameSizeInBytes
