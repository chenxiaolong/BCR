@file:OptIn(ExperimentalUnsignedTypes::class)

package com.chiller3.bcr.format

import android.media.MediaCodec
import android.media.MediaFormat
import android.system.Os
import android.system.OsConstants
import java.io.FileDescriptor
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WaveContainer(private val fd: FileDescriptor) : Container {
    private var isStarted = false
    private var track = -1
    private var frameSize = 0
    private var channelCount = 0
    private var sampleRate = 0

    override fun start() {
        if (isStarted) {
            throw IllegalStateException("Container already started")
        }

        Os.ftruncate(fd, 0)

        // Skip header
        Os.lseek(fd, HEADER_SIZE.toLong(), OsConstants.SEEK_SET)

        isStarted = true
    }

    override fun stop() {
        if (!isStarted) {
            throw IllegalStateException("Container not started")
        }

        isStarted = false

        if (track >= 0) {
            val fileSize = Os.lseek(fd, 0, OsConstants.SEEK_CUR)
            val header = buildHeader(fileSize)
            Os.lseek(fd, 0, OsConstants.SEEK_SET)
            Os.write(fd, header)
        }
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
        frameSize = mediaFormat.getInteger(Format.KEY_X_FRAME_SIZE_IN_BYTES)
        channelCount = mediaFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        sampleRate = mediaFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)

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

        Os.write(fd, byteBuffer)
    }

    private fun buildHeader(fileSize: Long): ByteBuffer =
        ByteBuffer.allocate(HEADER_SIZE).apply {
            order(ByteOrder.LITTLE_ENDIAN)

            val (chunkSize, dataSize) = if (fileSize >= Int.MAX_VALUE) {
                // If, for some reason, the recording is excessively huge, don't set a size and just
                // let the audio player guess
                Pair(0, 0)
            } else {
                Pair(fileSize.toInt() - 8, fileSize.toInt() - HEADER_SIZE)
            }

            // 0-3: Chunk ID
            put(RIFF_MAGIC.asByteArray())
            // 4-7: Chunk size
            putInt(chunkSize)
            // 8-11: Format
            put(WAVE_MAGIC.asByteArray())
            // 12-15: Subchunk 1 ID
            put(FMT_MAGIC.asByteArray())
            // 16-19: Subchunk 1 size
            putInt(16)
            // 20-21: Audio format
            putShort(1)
            // 22-23: Number of channels
            putShort(channelCount.toShort())
            // 24-27: Sample rate
            putInt(sampleRate)
            // 28-31: Byte rate
            putInt(sampleRate * frameSize)
            // 32-33: Block align
            putShort(frameSize.toShort())
            // 34-35: Bits per sample
            putShort(((frameSize / channelCount) * 8).toShort())
            // 36-39: Subchunk 2 ID
            put(DATA_MAGIC.asByteArray())
            // 40-43: Subchunk 2 size
            putInt(dataSize)

            flip()
        }

    companion object {
        private const val HEADER_SIZE = 44
        private val RIFF_MAGIC = ubyteArrayOf(0x52u, 0x49u, 0x46u, 0x46u) // RIFF
        private val WAVE_MAGIC = ubyteArrayOf(0x57u, 0x41u, 0x56u, 0x45u) // WAVE
        private val FMT_MAGIC = ubyteArrayOf(0x66u, 0x6du, 0x74u, 0x20u) // "fmt "
        private val DATA_MAGIC = ubyteArrayOf(0x64u, 0x61u, 0x74u, 0x61u) // data
    }
}
