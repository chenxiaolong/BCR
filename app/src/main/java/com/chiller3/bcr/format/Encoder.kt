package com.chiller3.bcr.format

import android.media.MediaFormat
import java.nio.ByteBuffer

abstract class Encoder(
    mediaFormat: MediaFormat,
) {
    protected val frameSize = mediaFormat.getInteger(Format.KEY_X_FRAME_SIZE_IN_BYTES)
    private val sampleRate = mediaFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)

    /** Number of frames encoded so far. */
    protected var numFrames = 0L

    /** Presentation timestamp given [numFrames] already being encoded */
    protected val timestampUs
        get() = numFrames * 1_000_000L / sampleRate

    /**
     * Start the encoder process.
     *
     * Can only be called if the encoder process is not already started.
     */
    abstract fun start()

    /**
     * Stop the encoder process.
     *
     * Can only be called if the encoder process is started.
     */
    abstract fun stop()

    /**
     * Release resources used by the encoder process.
     *
     * If the encoder process is not already stopped, then it will be stopped.
     */
    abstract fun release()

    /**
     * Submit a buffer to be encoded.
     *
     * @param buffer Must be in the PCM format expected by the encoder and [ByteBuffer.position]
     * and [ByteBuffer.limit] must correctly represent the bounds of the data.
     * @param isEof No more data can be submitted after this method is called once with EOF == true.
     */
    abstract fun encode(buffer: ByteBuffer, isEof: Boolean)
}
