@file:Suppress("OPT_IN_IS_NOT_ENABLED")
@file:OptIn(ExperimentalUnsignedTypes::class)

package com.chiller3.bcr.format

import android.media.MediaCodec
import android.media.MediaFormat
import android.system.Os
import android.system.OsConstants
import java.io.FileDescriptor
import java.io.IOException
import java.nio.ByteBuffer

/**
 * Dummy FLAC container wrapper that updates the STREAMINFO duration field when complete.
 *
 * [MediaCodec] already produces a well-formed FLAC file, thus this class writes those samples
 * directly to the output file.
 */
class FlacContainer(fd: FileDescriptor) : Container(fd) {
    private var lastPresentationTimeUs = -1L
    private var isStopped = true

    override fun start() {
        if (isStopped) {
            Os.lseek(fd, 0, OsConstants.SEEK_SET)
            Os.ftruncate(fd, 0)
            isStopped = false
        } else {
            throw IllegalStateException("Called start when already started")
        }
    }

    override fun stop() {
        if (!isStopped) {
            isStopped = true

            if (lastPresentationTimeUs >= 0) {
                setHeaderDuration()
            }
        } else {
            throw IllegalStateException("Called stop when already stopped")
        }
    }

    override fun release() {
        if (!isStopped) {
            stop()
        }
    }

    override fun addTrack(mediaFormat: MediaFormat): Int =
        // Not needed
        -1

    override fun writeSamples(trackIndex: Int, byteBuffer: ByteBuffer,
                              bufferInfo: MediaCodec.BufferInfo) {
        Os.write(fd, byteBuffer)

        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
            lastPresentationTimeUs = bufferInfo.presentationTimeUs
        }
    }

    /**
     * Write the frame count to the STREAMINFO metadata block of a flac file.
     *
     * @throws IOException If FLAC metadata does not appear to be valid or if the number of frames
     * computed from [lastPresentationTimeUs] exceeds the bounds of a 36-bit integer
     */
    private fun setHeaderDuration() {
        Os.lseek(fd, 0, OsConstants.SEEK_SET)

        // Magic (4 bytes)
        // + metadata block header (4 bytes)
        // + streaminfo block (34 bytes)
        val buf = UByteArray(42)

        if (Os.read(fd, buf.asByteArray(), 0, buf.size) != buf.size) {
            throw IOException("EOF reached when reading FLAC headers")
        }

        // Validate the magic
        if (ByteBuffer.wrap(buf.asByteArray(), 0, 4) !=
            ByteBuffer.wrap(FLAC_MAGIC.asByteArray())) {
            throw IOException("FLAC magic not found")
        }

        // Validate that the first metadata block is STREAMINFO and has the correct size
        if (buf[4] and 0x7fu != 0.toUByte()) {
            throw IOException("First metadata block is not STREAMINFO")
        }

        val streamInfoSize = buf[5].toUInt().shl(16) or
                buf[6].toUInt().shl(8) or buf[7].toUInt()
        if (streamInfoSize < 34u) {
            throw IOException("STREAMINFO block is too small")
        }

        // Sample rate field is a 20-bit integer at the 18th byte
        val sampleRate = buf[18].toUInt().shl(12) or
                buf[19].toUInt().shl(4) or
                buf[20].toUInt().shr(4)

        // This underestimates the duration by a miniscule amount because it doesn't account for the
        // duration of the final write
        val frames = lastPresentationTimeUs.toULong() * sampleRate / 1_000_000uL

        if (frames >= 2uL.shl(36)) {
            throw IOException("Frame count cannot be represented in FLAC: $frames")
        }

        // Total samples field is a 36-bit integer that begins 4 bits into the 21st byte
        buf[21] = (buf[21] and 0xf0u) or (frames.shr(32) and 0xfu).toUByte()
        buf[22] = (frames.shr(24) and 0xffu).toUByte()
        buf[23] = (frames.shr(16) and 0xffu).toUByte()
        buf[24] = (frames.shr(8) and 0xffu).toUByte()
        buf[25] = (frames and 0xffu).toUByte()

        Os.lseek(fd, 21, OsConstants.SEEK_SET)
        if (Os.write(fd, buf.asByteArray(), 21, 5) != 5) {
            throw IOException("EOF reached when writing frame count")
        }
    }

    companion object {
        private val FLAC_MAGIC = ubyteArrayOf(0x66u, 0x4cu, 0x61u, 0x43u) // fLaC
    }
}