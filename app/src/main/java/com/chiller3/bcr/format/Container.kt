package com.chiller3.bcr.format

import android.media.MediaCodec
import android.media.MediaFormat
import java.nio.ByteBuffer

/**
 * Abstract class for writing encoded samples to a container format.
 */
sealed class Container {
    /**
     * Start the muxer process.
     *
     * Must be called before [writeSamples].
     */
    abstract fun start()

    /**
     * Stop the muxer process.
     *
     * Must not be called if [start] did not complete successfully.
     */
    abstract fun stop()

    /**
     * Free resources used by the muxer process.
     *
     * Can be called in any state. If the muxer process is started, it will be stopped.
     */
    abstract fun release()

    /**
     * Add a track to the container with the specified format.
     *
     * Must not be called after the muxer process is started.
     *
     * @param mediaFormat Must be the instance returned by [MediaCodec.getOutputFormat]
     */
    abstract fun addTrack(mediaFormat: MediaFormat): Int

    /**
     * Write encoded samples to the output container.
     *
     * Must not be called unless the muxer process is started.
     *
     * @param trackIndex Must be an index returned by [addTrack]
     */
    abstract fun writeSamples(trackIndex: Int, byteBuffer: ByteBuffer,
                              bufferInfo: MediaCodec.BufferInfo)
}
