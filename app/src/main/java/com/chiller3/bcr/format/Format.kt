/*
 * SPDX-FileCopyrightText: 2022-2024 Andrew Gunnerson
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.chiller3.bcr.format

import android.media.AudioFormat
import android.media.MediaFormat
import android.util.Log
import com.chiller3.bcr.Preferences
import com.chiller3.bcr.extension.frameSizeInBytesCompat
import java.io.FileDescriptor

sealed class Format {
    /** User-facing name of the format. */
    abstract val name: String

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

    /** Details about the format parameter range and default value. */
    abstract val paramInfo: FormatParamInfo

    /** Defaults about the supported sample rates. */
    abstract val sampleRateInfo: SampleRateInfo

    /** Bare minimum [MediaFormat] containing only [MediaFormat.KEY_MIME]. */
    protected val baseMediaFormat: MediaFormat
        get() = MediaFormat().apply {
            setString(MediaFormat.KEY_MIME, mimeTypeAudio)
        }

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

        val format = baseMediaFormat.apply {
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
        private val TAG = Format::class.java.simpleName

        const val KEY_X_FRAME_SIZE_IN_BYTES = "x-frame-size-in-bytes"

        val all: List<Format> by lazy {
            val formats = mutableListOf<Format>()

            for (constructor in arrayOf(
                ::OpusFormat,
                ::AacFormat,
                ::FlacFormat,
                { WaveFormat },
                ::AmrWbFormat,
                ::AmrNbFormat,
            )) {
                try {
                    formats.add(constructor())
                } catch (e: IllegalArgumentException) {
                    Log.w(TAG, "Failed to initialize with $constructor", e)
                }
            }

            formats
        }
        private val default: Format = all.first()

        /** Find output format by name. */
        fun getByName(name: String): Format? = all.find { it.name == name }

        /**
         * Get the saved format from the preferences or fall back to the default.
         *
         * The parameter, if set, is clamped to the format's allowed parameter range. Similarly, the
         * sample rate, if set, is set to the nearest valid value.
         */
        fun fromPreferences(prefs: Preferences): Triple<Format, UInt?, UInt?> {
            // Use the saved format if it is (still) valid.
            val format = prefs.format ?: default

            // Convert the saved value to the nearest valid value (eg. in case the bitrate range is
            // changed in a future version).
            val param = prefs.getFormatParam(format)?.let {
                format.paramInfo.toNearest(it)
            }

            // Same with the sample rate.
            val sampleRate = prefs.getFormatSampleRate(format)?.let {
                format.sampleRateInfo.toNearest(it)
            }

            return Triple(format, param, sampleRate)
        }
    }
}
