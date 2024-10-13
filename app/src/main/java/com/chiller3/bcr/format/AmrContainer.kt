/*
 * SPDX-FileCopyrightText: 2024 Andrew Gunnerson
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.chiller3.bcr.format

import android.media.MediaCodec
import android.media.MediaFormat
import android.system.Os
import com.chiller3.bcr.writeFully
import java.io.FileDescriptor
import java.nio.ByteBuffer

class AmrContainer(private val fd: FileDescriptor, private val isWideband: Boolean) : Container {
    private var isStarted = false
    private var track = -1

    override fun start() {
        if (isStarted) {
            throw IllegalStateException("Container already started")
        }

        Os.ftruncate(fd, 0)

        val header = if (isWideband) { HEADER_WB } else { HEADER_NB }
        val headerBytes = header.toByteArray(Charsets.US_ASCII)

        writeFully(fd, headerBytes, 0, headerBytes.size)

        isStarted = true
    }

    override fun stop() {
        if (!isStarted) {
            throw IllegalStateException("Container not started")
        }

        isStarted = false
    }

    override fun release() {
        if (isStarted) {
            stop()
        }
    }

    override fun addTrack(mediaFormat: MediaFormat): Int {
        if (isStarted) {
            throw IllegalStateException("Container already started")
        } else if (track >= 0) {
            throw IllegalStateException("Track already added")
        }

        track = 0

        @Suppress("KotlinConstantConditions")
        return track
    }

    override fun writeSamples(trackIndex: Int, byteBuffer: ByteBuffer,
                              bufferInfo: MediaCodec.BufferInfo) {
        if (!isStarted) {
            throw IllegalStateException("Container not started")
        } else if (track < 0) {
            throw IllegalStateException("No track has been added")
        } else if (track != trackIndex) {
            throw IllegalStateException("Invalid track: $trackIndex")
        }

        writeFully(fd, byteBuffer)
    }

    companion object {
        private const val HEADER_WB = "#!AMR-WB\n"
        private const val HEADER_NB = "#!AMR\n"
    }
}
