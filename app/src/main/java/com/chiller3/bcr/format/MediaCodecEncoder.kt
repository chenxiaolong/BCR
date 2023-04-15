package com.chiller3.bcr.format

import android.media.MediaCodec
import android.media.MediaCodecList
import android.media.MediaFormat
import android.util.Log
import java.lang.Integer.min
import java.nio.ByteBuffer

/**
 * Create a [MediaCodec]-based encoder for the specified format.
 *
 * @param mediaFormat The [MediaFormat] instance returned by [Format.getMediaFormat].
 * @param container The container for storing the encoded audio stream.
 *
 * @throws Exception if the device does not support encoding with the parameters set in the format
 * or if configuring the [MediaCodec] fails.
 */
class MediaCodecEncoder(
    mediaFormat: MediaFormat,
    private val container: Container,
) : Encoder(mediaFormat) {
    private val codec = createCodec(mediaFormat)
    private val bufferInfo = MediaCodec.BufferInfo()
    private var trackIndex = -1

    override fun start() =
        codec.start()

    override fun stop() =
        codec.stop()

    override fun release() =
        codec.release()

    override fun encode(buffer: ByteBuffer, isEof: Boolean) {
        while (true) {
            var waitForever = false

            val inputBufferId = codec.dequeueInputBuffer(TIMEOUT)
            if (inputBufferId >= 0) {
                val inputBuffer = codec.getInputBuffer(inputBufferId)!!
                // Maximum non-overflowing buffer size that is a multiple of the frame size
                val toCopy = min(buffer.remaining(), inputBuffer.remaining()) / frameSize * frameSize

                // Temporarily change buffer limit to avoid overflow
                val oldLimit = buffer.limit()
                buffer.limit(buffer.position() + toCopy)
                inputBuffer.put(buffer)
                buffer.limit(oldLimit)

                // Submit EOF if the entire buffer has been consumed
                val flags = if (isEof && !buffer.hasRemaining()) {
                    Log.d(TAG, "On final buffer; submitting EOF")
                    waitForever = true
                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                } else {
                    0
                }

                codec.queueInputBuffer(inputBufferId, 0, toCopy, timestampUs, flags)

                numFrames += toCopy / frameSize
            } else {
                Log.w(TAG, "Unexpected input buffer dequeue error: $inputBufferId")
            }

            flush(waitForever)

            if (!buffer.hasRemaining()) {
                break
            }
        }
    }

    /** Flush [MediaCodec]'s pending encoded data to [container]. */
    private fun flush(waitForever: Boolean) {
        while (true) {
            val timeout = if (waitForever) { -1 } else { TIMEOUT }
            val outputBufferId = codec.dequeueOutputBuffer(bufferInfo, timeout)
            if (outputBufferId >= 0) {
                val buffer = codec.getOutputBuffer(outputBufferId)!!

                container.writeSamples(trackIndex, buffer, bufferInfo)

                codec.releaseOutputBuffer(outputBufferId, false)

                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    Log.d(TAG, "Received EOF; fully flushed")
                    // Output has been fully written
                    break
                }
            } else if (outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                val outputFormat = codec.outputFormat
                Log.d(TAG, "Output format changed to: $outputFormat")
                trackIndex = container.addTrack(outputFormat)
                container.start()
            } else if (outputBufferId == MediaCodec.INFO_TRY_AGAIN_LATER) {
                break
            } else {
                Log.w(TAG, "Unexpected output buffer dequeue error: $outputBufferId")
                break
            }
        }
    }

    companion object {
        private val TAG = MediaCodecEncoder::class.java.simpleName
        private const val TIMEOUT = 500L

        fun createCodec(mediaFormat: MediaFormat): MediaCodec {
            val encoder = MediaCodecList(MediaCodecList.REGULAR_CODECS).findEncoderForFormat(mediaFormat)
                ?: throw Exception("No suitable encoder found for $mediaFormat")
            Log.d(TAG, "Audio encoder: $encoder")

            val codec = MediaCodec.createByCodecName(encoder)

            try {
                codec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            } catch (e: Exception) {
                codec.release()
                throw e
            }

            return codec
        }
    }
}
