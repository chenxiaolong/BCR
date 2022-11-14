package link.cure.recorder

import android.media.AudioFormat

val AudioFormat.frameSizeInBytesCompat: Int
    get() =
        frameSizeInBytes
