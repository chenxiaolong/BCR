/*
 * SPDX-FileCopyrightText: 2024 Andrew Gunnerson
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.chiller3.bcr

import android.system.Os
import java.io.FileDescriptor
import java.io.IOException
import java.nio.ByteBuffer

// These can't be extension methods (yet): https://github.com/Kotlin/KEEP/issues/348.

fun writeFully(fd: FileDescriptor, buffer: ByteBuffer) {
    while (buffer.remaining() > 0) {
        val n = Os.write(fd, buffer)
        if (n == 0) {
            throw IOException("Unexpected EOF when writing data")
        }
    }
}

fun writeFully(fd: FileDescriptor, bytes: ByteArray, byteOffset: Int, byteCount: Int) {
    var offset = byteOffset
    var remaining = byteCount

    while (remaining > 0) {
        val n = Os.write(fd, bytes, offset, remaining)
        if (n == 0) {
            throw IOException("Unexpected EOF when writing data")
        }

        offset += n
        remaining -= n
    }
}
