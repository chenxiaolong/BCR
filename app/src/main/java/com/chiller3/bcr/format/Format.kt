package com.chiller3.bcr.format

import android.media.AudioFormat
import android.media.MediaFormat
import com.chiller3.bcr.frameSizeInBytesCompat
import java.io.FileDescriptor

sealed class Format {
    /** User-facing name of the format. */
    abstract val name: String

    /** Details about the format parameter range and default value. */
    abstract val paramInfo: FormatParamInfo

    /** The MIME type of the container storing the encoded audio stream. */
    abstract val mimeTypeContainer: String

    /**
     * The MIME type of the encoded audio stream inside the container.
     *
     * May be the same as [mimeTypeContainer] for some formats.
     */
    abstract val mimeTypeAudio: String

    /** Whether the format takes the PCM samples as is without encoding. */
    abstract val passthrough: Boolean

    /** Whether the format is supported on the current device. */
    abstract val supported: Boolean

    /**
     * Create a [MediaFormat] representing the encoded audio with parameters matching the specified
     * input PCM audio format.
     *
     * @param audioFormat [AudioFormat.getSampleRate] must not be
     * [AudioFormat.SAMPLE_RATE_UNSPECIFIED].
     * @param param Format-specific parameter value. Must be valid according to [paramInfo].
     *
     * @throws IllegalArgumentException if [FormatParamInfo.validate] fails
     */
    fun getMediaFormat(audioFormat: AudioFormat, param: UInt?): MediaFormat {
        if (param != null) {
            paramInfo.validate(param)
        }

        val format = MediaFormat().apply {
            setString(MediaFormat.KEY_MIME, mimeTypeAudio)
            setInteger(MediaFormat.KEY_CHANNEL_COUNT, audioFormat.channelCount)
            setInteger(MediaFormat.KEY_SAMPLE_RATE, audioFormat.sampleRate)
            setInteger(KEY_X_FRAME_SIZE_IN_BYTES, audioFormat.frameSizeInBytesCompat)
        }

        updateMediaFormat(format, param ?: paramInfo.default)

        return format
    }

    /**
     * Update [mediaFormat] with parameter keys relevant to the format-specific parameter.
     *
     * @param param Guaranteed to be valid according to [paramInfo]
     */
    protected abstract fun updateMediaFormat(mediaFormat: MediaFormat, param: UInt)

    /**
     * Create an [Encoder] that produces [mediaFormat] output.
     *
     * @param mediaFormat The [MediaFormat] instance returned by [getMediaFormat].
     * @param container The [Container] instance returned by [getContainer].
     *
     * @throws Exception if the device does not support encoding with the parameters set in
     * [mediaFormat] or if configuring the encoder fails.
     */
    fun getEncoder(mediaFormat: MediaFormat, container: Container): Encoder =
        if (passthrough) {
            PassthroughEncoder(mediaFormat, container)
        } else {
            MediaCodecEncoder(mediaFormat, container)
        }

    /**
     * Create a container muxer that takes encoded input and writes the muxed output to [fd].
     *
     * @param fd The container does not take ownership of the file descriptor.
     */
    abstract fun getContainer(fd: FileDescriptor): Container

    companion object {
        const val KEY_X_FRAME_SIZE_IN_BYTES = "x-frame-size-in-bytes"
    }
}