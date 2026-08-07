/*
 * SPDX-FileCopyrightText: 2026 Django Eijgensteijn
 * SPDX-License-Identifier: GPL-3.0-only
 */

@file:OptIn(ExperimentalUnsignedTypes::class)

package com.chiller3.bcr.format

import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import android.system.Os
import android.system.OsConstants
import android.util.Log
import androidx.annotation.RequiresApi
import com.chiller3.bcr.writeFully
import java.io.FileDescriptor
import java.io.IOException
import java.nio.ByteBuffer

/**
 * [MediaMuxer] wrapper that marks the final Ogg page as the end of the logical bitstream.
 *
 * AOSP's Ogg muxer copies its `mReachedEOS` field into every packet's `e_o_s` field, but only sets
 * that field to true after the last packet has already been written. As a result, no page in the
 * output file ever has the end-of-stream bit set in its header type flags. Demuxers that seek to
 * the end of the file to find the last granule position, like Firefox's, reject the file because
 * the terminating page is missing.
 *
 * The fix is to set the bit in the last page's header after the muxer is done and recompute that
 * page's CRC.
 *
 * @param fd Output file descriptor. This class does not take ownership of it and it should not be
 * touched outside of this class until [stop] is called and returns.
 */
@RequiresApi(Build.VERSION_CODES.Q)
class OggContainer(private val fd: FileDescriptor) : Container {
    private val muxer = MediaMuxer(fd, MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG)
    private var isStarted = false
    private var receivedEof = false

    override fun start() {
        if (isStarted) {
            throw IllegalStateException("Container already started")
        }

        muxer.start()

        isStarted = true
    }

    override fun stop() {
        if (!isStarted) {
            throw IllegalStateException("Container not started")
        }

        isStarted = false

        // The muxer's writer thread closes its copy of the file descriptor when it stops, so the
        // final page is only guaranteed to be on disk once this returns.
        muxer.stop()

        // An aborted recording has no final page to mark and the file may be truncated anywhere,
        // so leave it alone rather than risk throwing over whatever error caused the abort.
        if (receivedEof) {
            setEndOfStreamFlag()
        }
    }

    override fun release() {
        try {
            if (isStarted) {
                stop()
            }
        } finally {
            muxer.release()
        }
    }

    override fun addTrack(mediaFormat: MediaFormat): Int =
        muxer.addTrack(mediaFormat)

    override fun writeSamples(trackIndex: Int, byteBuffer: ByteBuffer,
                              bufferInfo: MediaCodec.BufferInfo) {
        muxer.writeSampleData(trackIndex, byteBuffer, bufferInfo)

        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
            receivedEof = true
        }
    }

    /**
     * Set the end-of-stream bit in the header type flags of the file's last Ogg page and update
     * that page's CRC checksum.
     *
     * @throws IOException If the last page cannot be found or cannot be read
     */
    private fun setEndOfStreamFlag() {
        val fileSize = Os.lseek(fd, 0, OsConstants.SEEK_END)
        val bufSize = minOf(fileSize, OggPage.MAX_SIZE.toLong()).toInt()
        val buf = UByteArray(bufSize)

        Os.lseek(fd, fileSize - bufSize, OsConstants.SEEK_SET)
        if (Os.read(fd, buf.asByteArray(), 0, bufSize) != bufSize) {
            throw IOException("EOF reached when reading final Ogg page")
        }

        val pageOffset = OggPage.findLast(buf)
            ?: throw IOException("Final Ogg page not found")

        if (!OggPage.markEndOfStream(buf, pageOffset)) {
            Log.d(TAG, "Final Ogg page already marks the end of the stream")
            return
        }

        Log.d(TAG, "Marked final Ogg page as the end of the stream")

        Os.lseek(fd, fileSize - bufSize + pageOffset, OsConstants.SEEK_SET)
        writeFully(fd, buf.asByteArray(), pageOffset, OggPage.headerSize(buf, pageOffset))
    }

    companion object {
        private val TAG = OggContainer::class.java.simpleName
    }
}
