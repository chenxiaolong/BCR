package com.chiller3.bcr.format

import android.media.MediaCodec
import android.media.MediaFormat
import java.lang.IllegalStateException
import java.nio.ByteBuffer

/**
 * Create a passthrough encoder for the specified format.
 *
 * @param mediaFormat The [MediaFormat] instance returned by [Format.getMediaFormat].
 * @param container The container for storing the raw PCM audio stream.
 */
class PassthroughEncoder(
    private val mediaFormat: MediaFormat,
    private val container: Container,
) : Encoder(mediaFormat) {
    private var isStarted = false
    private val bufferInfo = MediaCodec.BufferInfo()
    private var trackIndex = -1

    override fun start() {
        if (isStarted) {
            throw IllegalStateException("Encoder is already started")
        }

        isStarted = true
        trackIndex = container.addTrack(mediaFormat)
        container.start()
    }

    override fun stop() {
        if (!isStarted) {
            throw IllegalStateException("Encoder is not started")
        }

        isStarted = false
    }

    override fun release() {
        if (isStarted) {
            stop()
        }
    }

    override fun encode(buffer: ByteBuffer, isEof: Boolean) {
        if (!isStarted) {
            throw IllegalStateException("Encoder is not started")
        }

        val frames = buffer.remaining() / frameSize

        bufferInfo.offset = buffer.position()
        bufferInfo.size = buffer.limit()
        bufferInfo.presentationTimeUs = timestampUs
        bufferInfo.flags = if (isEof) {
            MediaCodec.BUFFER_FLAG_END_OF_STREAM
        } else {
            0
        }

        container.writeSamples(trackIndex, buffer, bufferInfo)

        numFrames += frames
    }
}
