package com.chiller3.bcr.format

import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.FileDescriptor
import java.nio.ByteBuffer

/**
 * A thin wrapper around [MediaMuxer].
 *
 * @param fd Output file descriptor. This class does not take ownership of the file descriptor.
 * @param containerFormat A valid [MediaMuxer.OutputFormat] value for the output container format.
 */
class MediaMuxerContainer(
    fd: FileDescriptor,
    containerFormat: Int,
) : Container {
    private val muxer = MediaMuxer(fd, containerFormat)

    override fun start() =
        muxer.start()

    override fun stop() =
        muxer.stop()

    override fun release() =
        muxer.release()

    override fun addTrack(mediaFormat: MediaFormat): Int =
        muxer.addTrack(mediaFormat)

    override fun writeSamples(trackIndex: Int, byteBuffer: ByteBuffer,
                              bufferInfo: MediaCodec.BufferInfo) =
        muxer.writeSampleData(trackIndex, byteBuffer, bufferInfo)
}
