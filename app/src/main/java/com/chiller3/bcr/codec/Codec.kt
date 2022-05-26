package com.chiller3.bcr.codec

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaCodecList
import android.media.MediaFormat
import android.util.Log
import java.io.FileDescriptor

sealed class Codec {
    /** User-facing name of the codec. */
    abstract val name: String

    /** Meaning of the codec parameter value. */
    abstract val paramType: CodecParamType

    /** Valid range for the codec-specific parameter value. */
    abstract val paramRange: UIntRange

    /** Default codec parameter value. */
    abstract val paramDefault: UInt

    /** The MIME type of the container storing the encoded audio stream. */
    abstract val mimeTypeContainer: String

    /**
     * The MIME type of the encoded audio stream inside the container.
     *
     * May be the same as [mimeTypeContainer] for some codecs.
     */
    abstract val mimeTypeAudio: String

    /** Whether the codec is supported on the current device. */
    abstract val supported: Boolean

    /**
     * Create a [MediaFormat] representing the encoded audio with parameters matching the specified
     * input PCM audio format.
     *
     * @param param Codec-specific parameter value. Must be in the [paramRange] range. If null,
     * [paramDefault] is used.
     *
     * @throws IllegalArgumentException if [param] is outside [paramRange]
     */
    fun getMediaFormat(audioFormat: AudioFormat, sampleRate: Int, param: UInt?): MediaFormat {
        if (param != null && param !in paramRange) {
            throw IllegalArgumentException("Parameter $param not in range $paramRange")
        }

        val format = MediaFormat().apply {
            setString(MediaFormat.KEY_MIME, mimeTypeAudio)
            setInteger(MediaFormat.KEY_CHANNEL_COUNT, audioFormat.channelCount)
            setInteger(MediaFormat.KEY_SAMPLE_RATE, sampleRate)
        }

        updateMediaFormat(format, param ?: paramDefault)

        return format
    }

    /**
     * Update [mediaFormat] with parameter keys relevant to the codec-specific parameter.
     *
     * @param param Guaranteed to be within [paramRange]
     */
    protected abstract fun updateMediaFormat(mediaFormat: MediaFormat, param: UInt)

    /**
     * Create a [MediaCodec] encoder that produces [mediaFormat] output.
     *
     * @param mediaFormat The [MediaFormat] instance returned by [getMediaFormat]
     *
     * @throws Exception if the device does not support encoding with the parameters set in
     * [mediaFormat] or if configuring the [MediaCodec] fails.
     */
    fun getMediaCodec(mediaFormat: MediaFormat): MediaCodec {
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

    /**
     * Create a container muxer that takes encoded input and writes the muxed output to [fd].
     *
     * @param fd The container does not take ownership of the file descriptor
     */
    abstract fun getContainer(fd: FileDescriptor): Container

    companion object {
        private val TAG = Codec::class.java.simpleName
    }
}