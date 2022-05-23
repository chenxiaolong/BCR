@file:Suppress("OPT_IN_IS_NOT_ENABLED")
@file:OptIn(ExperimentalUnsignedTypes::class)

package com.chiller3.bcr

import android.annotation.SuppressLint
import android.content.Context
import android.media.*
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import android.telecom.Call
import android.telecom.PhoneAccount
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import java.io.FileDescriptor
import java.io.IOException
import java.lang.Integer.min
import java.nio.ByteBuffer
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatterBuilder
import java.time.format.SignStyle
import java.time.temporal.ChronoField
import android.os.Process as AndroidProcess

/**
 * Captures call audio and encodes it into an output file in the user's selected directory or the
 * fallback/default directory.
 *
 * @constructor Create a thread for recording a call. Note that the system only has a single
 * [MediaRecorder.AudioSource.VOICE_CALL] stream. If multiple calls are being recorded, the recorded
 * audio for each call may not be as expected.
 * @param context Used for querying shared preferences and accessing files via SAF. A reference is
 * kept in the object.
 * @param listener Used for sending completion notifications. The listener is called from this
 * thread, not the main thread.
 * @param call Used only for determining the output filename and is not saved.
 */
class RecorderThread(
    private val context: Context,
    private val listener: OnRecordingCompletedListener,
    call: Call,
): Thread() {
    @Volatile private var isCancelled = false
    private var captureFailed = false
    private val handleUri: Uri = call.details.handle
    private val creationTime: Long = call.details.creationTimeMillis
    private val direction: String? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        when (call.details.callDirection) {
            Call.Details.DIRECTION_INCOMING -> "in"
            Call.Details.DIRECTION_OUTGOING -> "out"
            else -> null
        }
    } else {
        null
    }
    private val displayName: String? = call.details.callerDisplayName

    init {
        Log.i(TAG, "[${id}] Created thread for call: $call")
    }

    private fun getFilename(): String =
        buildString {
            val instant = Instant.ofEpochMilli(creationTime)
            append(FORMATTER.format(ZonedDateTime.ofInstant(instant, ZoneId.systemDefault())))

            if (direction != null) {
                append('_')
                append(direction)
            }

            if (handleUri.scheme == PhoneAccount.SCHEME_TEL) {
                append('_')
                append(handleUri.schemeSpecificPart)
            }

            // AOSP's SAF automatically replaces invalid characters with underscores, but just in
            // case an OEM fork breaks that, do the replacement ourselves to prevent directory
            // traversal attacks.
            val name = displayName?.replace('/', '_')?.trim()
            if (!name.isNullOrBlank()) {
                append('_')
                append(name)
            }
        }

    override fun run() {
        var success = false
        var resultUri: Uri? = null

        try {
            Log.i(TAG, "[${id}] Recording thread started")

            if (isCancelled) {
                Log.i(TAG, "[${id}] Recording cancelled before it began")
            } else {
                val (uri, pfd) = openOutputFile(getFilename())
                resultUri = uri

                pfd.use {
                    recordUntilCancelled(it)
                }

                success = !captureFailed
            }
        } catch (e: Exception) {
            Log.e(TAG, "[${id}] Error during recording", e)
        } finally {
            Log.i(TAG, "[${id}] Recording thread completed")

            if (success) {
                listener.onRecordingCompleted(this, resultUri!!)
            } else {
                listener.onRecordingFailed(this, resultUri)
            }
        }
    }

    /**
     * Cancel current recording. This stops capturing audio after approximately 100ms, but the
     * thread does not exit until all data encoded so far has been written to the output file.
     *
     * If called before [start], the thread will not record any audio not create an output file. In
     * this scenario, [OnRecordingCompletedListener.onRecordingFailed] will be called with a null
     * [Uri].
     */
    fun cancel() {
        isCancelled = true
    }

    data class OutputFile(val uri: Uri, val pfd: ParcelFileDescriptor)

    /**
     * Try to create and open a new FLAC file in the user-chosen directory if possible and fall back
     * to the default output directory if not. [name] should not contain a file extension.
     *
     * @throws IOException if the file could not be created in either directory
     */
    private fun openOutputFile(name: String): OutputFile {
        val userUri = Preferences.getSavedOutputDir(context)
        if (userUri != null) {
            try {
                // Only returns null on API <21
                val userDir = DocumentFile.fromTreeUri(context, userUri)
                return openOutputFileInDir(userDir!!, name)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open file in user-specified directory: $userUri", e)
            }
        }

        val fallbackDir = DocumentFile.fromFile(Preferences.getDefaultOutputDir(context))
        Log.d(TAG, "Using fallback directory: ${fallbackDir.uri}")

        return openOutputFileInDir(fallbackDir, name)
    }

    /**
     * Create and open a new FLAC file with name [name] inside [directory]. [name] should not
     * contain a file extension.
     *
     * @throws IOException if file creation or opening fails
     */
    private fun openOutputFileInDir(directory: DocumentFile, name: String): OutputFile {
        val file = directory.createFile(MediaFormat.MIMETYPE_AUDIO_FLAC, name)
            ?: throw IOException("Failed to create file in ${directory.uri}")
        val pfd = context.contentResolver.openFileDescriptor(file.uri, "rw")
            ?: throw IOException("Failed to open file at ${file.uri}")
        return OutputFile(file.uri, pfd)
    }

    /**
     * Record from [MediaRecorder.AudioSource.VOICE_CALL] until [cancel] is called or an audio
     * capture or encoding error occurs.
     *
     * [pfd] does not get closed by this method.
     */
    @SuppressLint("MissingPermission")
    private fun recordUntilCancelled(pfd: ParcelFileDescriptor) {
        AndroidProcess.setThreadPriority(AndroidProcess.THREAD_PRIORITY_AUDIO)

        val audioFormat = AudioFormat.Builder()
            .setEncoding(ENCODING)
            .setChannelMask(CHANNEL_CONFIG)
            .build()
        val audioRecord = AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.VOICE_CALL)
            .setAudioFormat(audioFormat)
            .build()

        // Where's my RAII? :(
        try {
            audioRecord.startRecording()

            try {
                val codec = getFlacCodec(audioFormat, audioRecord.sampleRate)

                try {
                    codec.start()

                    try {
                        val frames = encodeLoop(audioRecord, codec, pfd.fileDescriptor)
                        setFlacHeaderDuration(pfd.fileDescriptor, frames)
                    } finally {
                        codec.stop()
                    }
                } finally {
                    codec.release()
                }
            } finally {
                audioRecord.stop()
            }
        } finally {
            audioRecord.release()
        }
    }

    /**
     * Main loop for encoding captured raw audio into an output file.
     *
     * The loop runs forever until [cancel] is called. At that point, no further data will be read
     * from [audioRecord] and the remaining output data from [codec] will be written to [fd].
     * If [audioRecord] fails to capture data, the loop will behave as if [cancel] was called
     * (ie. abort, but ensuring that the output file is valid).
     *
     * The approximate amount of time to cancel reading from the audio source is 100ms. This does
     * not include the time required to write out the remaining encoded data to the output file.
     *
     * @param audioRecord [AudioRecord.startRecording] must have been called
     * @param codec [MediaCodec.start] must have been called
     * @param fd Will be truncated to 0 bytes before writing
     *
     * @throws MediaCodec.CodecException if the codec encounters an error
     *
     * @return The number of frames that were recorded
     */
    private fun encodeLoop(audioRecord: AudioRecord, codec: MediaCodec, fd: FileDescriptor): ULong {
        Os.lseek(fd, 0, OsConstants.SEEK_SET)
        Os.ftruncate(fd, 0)

        // This is the most we ever read from audioRecord, even if the codec input buffer is
        // larger. This is purely for fast'ish cancellation and not for latency.
        val maxSamplesInBytes = audioRecord.sampleRate / 10 * getFrameSize(audioRecord.format)

        val inputTimestamp = 0L
        var inputComplete = false
        val bufferInfo = MediaCodec.BufferInfo()
        val frameSize = getFrameSize(audioRecord.format)
        var totalFrames = 0uL

        while (true) {
            if (!inputComplete) {
                val inputBufferId = codec.dequeueInputBuffer(10000)
                if (inputBufferId >= 0) {
                    val buffer = codec.getInputBuffer(inputBufferId)!!

                    val maxRead = min(maxSamplesInBytes, buffer.remaining())
                    val n = audioRecord.read(buffer, maxRead)
                    if (n < 0) {
                        Log.e(TAG, "Error when reading samples from ${audioRecord}: $n")
                        isCancelled = true
                        captureFailed = true
                    } else if (n == 0) {
                        // This should never be hit because AOSP guarantees that MediaCodec's
                        // ByteBuffers are direct buffers, but this is not publicly documented
                        // behavior
                        Log.e(TAG, "MediaCodec's ByteBuffer was not a direct buffer")
                        isCancelled = true
                    }

                    val frames = n / frameSize

                    // Setting the presentation timestamp will cause `c2.android.flac.encoder`
                    // software encoder to crash with SIGABRT
                    //inputTimestamp += frames * 1000000 / audioRecord.sampleRate

                    if (isCancelled) {
                        val duration = "%.1f".format(inputTimestamp / 1000000f)
                        Log.d(TAG, "Input complete after ${duration}s")
                        inputComplete = true
                    }

                    val flags = if (inputComplete) {
                        MediaCodec.BUFFER_FLAG_END_OF_STREAM
                    } else {
                        0
                    }

                    codec.queueInputBuffer(inputBufferId, 0, n, inputTimestamp, flags)

                    totalFrames += frames.toULong()
                } else if (inputBufferId != MediaCodec.INFO_TRY_AGAIN_LATER) {
                    Log.w(TAG, "Unexpected input buffer dequeue error: $inputBufferId")
                }
            }

            val outputBufferId = codec.dequeueOutputBuffer(bufferInfo, 0)
            if (outputBufferId >= 0) {
                val buffer = codec.getOutputBuffer(outputBufferId)!!

                Os.write(fd, buffer)

                codec.releaseOutputBuffer(outputBufferId, false)

                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    // Output has been fully written
                    break
                }
            } else if (outputBufferId != MediaCodec.INFO_OUTPUT_FORMAT_CHANGED &&
                outputBufferId != MediaCodec.INFO_TRY_AGAIN_LATER) {
                Log.w(TAG, "Unexpected output buffer dequeue error: $outputBufferId")
            }
        }

        return totalFrames
    }

    companion object {
        private val TAG = RecorderThread::class.java.simpleName
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT

        private val FLAC_MAGIC = ubyteArrayOf(0x66u, 0x4cu, 0x61u, 0x43u) // fLaC

        // Eg. 20220429_180249.123-0400
        private val FORMATTER = DateTimeFormatterBuilder()
            .appendValue(ChronoField.YEAR, 4, 10, SignStyle.EXCEEDS_PAD)
            .appendValue(ChronoField.MONTH_OF_YEAR, 2)
            .appendValue(ChronoField.DAY_OF_MONTH, 2)
            .appendLiteral('_')
            .appendValue(ChronoField.HOUR_OF_DAY, 2)
            .appendValue(ChronoField.MINUTE_OF_HOUR, 2)
            .appendValue(ChronoField.SECOND_OF_MINUTE, 2)
            .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true)
            .appendOffset("+HHMMss", "+0000")
            .toFormatter()

        /**
         * Create a [MediaCodec] encoder for FLAC using the given PCM audio format as the input.
         *
         * @throws Exception if the device does not support encoding FLAC with properties matching
         * matching the raw PCM data or if configuring the [MediaCodec] fails.
         */
        private fun getFlacCodec(audioFormat: AudioFormat, sampleRate: Int): MediaCodec {
            // AOSP ignores this because FLAC compression is lossless, but just in case the system
            // uses another FLAC encoder that requiress a non-dummy value (eg. 0), we'll just use
            // the PCM s16le bitrate. It's an overestimation, but shouldn't cause any issues.
            val bitRate = getFrameSize(audioFormat) * sampleRate / 8

            val format = MediaFormat()
            format.setString(MediaFormat.KEY_MIME, MediaFormat.MIMETYPE_AUDIO_FLAC)
            format.setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
            format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, audioFormat.channelCount)
            format.setInteger(MediaFormat.KEY_SAMPLE_RATE, sampleRate)
            format.setInteger(MediaFormat.KEY_FLAC_COMPRESSION_LEVEL, 8)

            val encoder = MediaCodecList(MediaCodecList.REGULAR_CODECS).findEncoderForFormat(format)
                ?: throw Exception("No FLAC encoder found")
            Log.d(TAG, "FLAC encoder: $encoder")

            val codec = MediaCodec.createByCodecName(encoder)

            try {
                codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            } catch (e: Exception) {
                codec.release()
                throw e
            }

            return codec
        }

        private fun getFrameSize(audioFormat: AudioFormat): Int {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                audioFormat.frameSizeInBytes
            } else{
                // Hardcoded for Android 9 compatibility only
                assert(ENCODING == AudioFormat.ENCODING_PCM_16BIT)
                2 * audioFormat.channelCount
            }
        }

        /**
         * Write the frame count [frames] to the STREAMINFO metadata block of a flac file. [frames]
         * should not account for the number of channels (ie. frames, not samples).
         *
         * @throws IOException If FLAC metadata does not appear to be valid
         * @throws IllegalArgumentException if [frames] exceeds the bounds of a 36-bit integer
         */
        private fun setFlacHeaderDuration(fd: FileDescriptor, frames: ULong) {
            if (frames >= 2uL.shl(36)) {
                throw IllegalArgumentException("Frame count cannot be represented in FLAC: $frames")
            }

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
    }

    interface OnRecordingCompletedListener {
        /**
         * Called when the recording completes successfully. [uri] is the output file.
         */
        fun onRecordingCompleted(thread: RecorderThread, uri: Uri)

        /**
         * Called when an error occurs during recording. If [uri] is not null, it points to the
         * output file containing partially recorded audio. If [uri] is null, then either the output
         * file could not be created or the thread was cancelled before it was started.
         */
        fun onRecordingFailed(thread: RecorderThread, uri: Uri?)
    }
}