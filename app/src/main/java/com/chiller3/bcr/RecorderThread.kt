package com.chiller3.bcr

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.telecom.Call
import android.telecom.PhoneAccount
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.chiller3.bcr.codec.Codec
import com.chiller3.bcr.codec.Codecs
import com.chiller3.bcr.codec.Container
import java.io.IOException
import java.lang.Integer.min
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
    // Thread state
    @Volatile private var isCancelled = false
    private var captureFailed = false

    // Filename
    private val filenameLock = Object()
    private lateinit var filename: String
    private val redactions = HashMap<String, String>()

    // Codec
    private val codec: Codec
    private val codecParam: UInt?

    init {
        logI("Created thread for call: $call")

        onCallDetailsChanged(call.details)

        val savedCodec = Codecs.fromPreferences(context)
        codec = savedCodec.first
        codecParam = savedCodec.second
    }

    private fun logD(msg: String) {
        Log.d(TAG, "[${id}] $msg")
    }

    private fun logE(msg: String, throwable: Throwable) {
        Log.e(TAG, "[${id}] $msg", throwable)
    }

    private fun logE(msg: String) {
        Log.e(TAG, "[${id}] $msg")
    }

    private fun logI(msg: String) {
        Log.i(TAG, "[${id}] $msg")
    }

    private fun logW(msg: String) {
        Log.w(TAG, "[${id}] $msg")
    }

    fun redact(msg: String): String {
        synchronized(filenameLock) {
            var result = msg

            for ((source, target) in redactions) {
                result = result
                    .replace(Uri.encode(source), Uri.encode(target))
                    .replace(source, target)
            }

            return result
        }
    }

    /**
     * Update [filename] with information from [details].
     *
     * This function holds a lock on [filenameLock] until it returns.
     */
    fun onCallDetailsChanged(details: Call.Details) {
        synchronized(filenameLock) {
            redactions.clear()

            filename = buildString {
                val instant = Instant.ofEpochMilli(details.creationTimeMillis)
                append(FORMATTER.format(ZonedDateTime.ofInstant(instant, ZoneId.systemDefault())))

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    when (details.callDirection) {
                        Call.Details.DIRECTION_INCOMING -> append("_in")
                        Call.Details.DIRECTION_OUTGOING -> append("_out")
                        Call.Details.DIRECTION_UNKNOWN -> {}
                    }
                }

                if (details.handle.scheme == PhoneAccount.SCHEME_TEL) {
                    append('_')
                    append(details.handle.schemeSpecificPart)

                    redactions[details.handle.schemeSpecificPart] = "<phone number>"
                }

                val callerName = details.callerDisplayName?.trim()
                if (!callerName.isNullOrBlank()) {
                    append('_')
                    append(callerName)

                    redactions[callerName] = "<caller name>"
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val contactName = details.contactDisplayName?.trim()
                    if (!contactName.isNullOrBlank()) {
                        append('_')
                        append(contactName)

                        redactions[contactName] = "<contact name>"
                    }
                }
            }
            // AOSP's SAF automatically replaces invalid characters with underscores, but just
            // in case an OEM fork breaks that, do the replacement ourselves to prevent
            // directory traversal attacks.
                .replace('/', '_').trim()

            logI("Updated filename due to call details change: ${redact(filename)}")
        }
    }

    override fun run() {
        var success = false
        var resultUri: Uri? = null

        try {
            logI("Recording thread started")

            if (isCancelled) {
                logI("Recording cancelled before it began")
            } else {
                val initialFilename = synchronized(filenameLock) { filename }

                val (file, pfd) = openOutputFile(initialFilename)
                resultUri = file.uri

                pfd.use {
                    recordUntilCancelled(it)
                }

                val finalFilename = synchronized(filenameLock) { filename }
                if (finalFilename != initialFilename) {
                    logI("Renaming ${redact(initialFilename)} to ${redact(finalFilename)}")

                    if (file.renameTo(finalFilename)) {
                        resultUri = file.uri
                    } else {
                        logW("Failed to rename to final filename: ${redact(finalFilename)}")
                    }
                }

                success = !captureFailed
            }
        } catch (e: Exception) {
            logE("Error during recording", e)
        } finally {
            logI("Recording thread completed")

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

    data class OutputFile(val file: DocumentFile, val pfd: ParcelFileDescriptor)

    /**
     * Try to create and open a new output file in the user-chosen directory if possible and fall
     * back to the default output directory if not. [name] should not contain a file extension.
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
                logE("Failed to open file in user-specified directory: $userUri", e)
            }
        }

        val fallbackDir = DocumentFile.fromFile(Preferences.getDefaultOutputDir(context))
        logD("Using fallback directory: ${fallbackDir.uri}")

        return openOutputFileInDir(fallbackDir, name)
    }

    /**
     * Create and open a new output file with name [name] inside [directory]. [name] should not
     * contain a file extension. The file extension is automatically determined from [codec].
     *
     * @throws IOException if file creation or opening fails
     */
    private fun openOutputFileInDir(directory: DocumentFile, name: String): OutputFile {
        val file = directory.createFile(codec.mimeTypeContainer, name)
            ?: throw IOException("Failed to create file in ${directory.uri}")
        val pfd = context.contentResolver.openFileDescriptor(file.uri, "rw")
            ?: throw IOException("Failed to open file at ${file.uri}")
        return OutputFile(file, pfd)
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
                // audioRecord.format has the detected native sample rate
                val mediaFormat = codec.getMediaFormat(audioRecord.format, codecParam)
                val mediaCodec = codec.getMediaCodec(mediaFormat)

                try {
                    mediaCodec.start()

                    try {
                        val container = codec.getContainer(pfd.fileDescriptor)

                        try {
                            encodeLoop(audioRecord, mediaCodec, container)
                            container.stop()
                        } finally {
                            container.release()
                        }
                    } finally {
                        mediaCodec.stop()
                    }
                } finally {
                    mediaCodec.release()
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
     * from [audioRecord] and the remaining output data from [mediaCodec] will be written to
     * [container]. If [audioRecord] fails to capture data, the loop will behave as if [cancel] was
     * called (ie. abort, but ensuring that the output file is valid).
     *
     * The approximate amount of time to cancel reading from the audio source is 100ms. This does
     * not include the time required to write out the remaining encoded data to the output file.
     *
     * @param audioRecord [AudioRecord.startRecording] must have been called
     * @param mediaCodec [MediaCodec.start] must have been called
     * @param container [Container.start] must *not* have been called. It will be left in a started
     * state after this method returns.
     *
     * @throws MediaCodec.CodecException if the codec encounters an error
     */
    private fun encodeLoop(audioRecord: AudioRecord, mediaCodec: MediaCodec, container: Container) {
        // This is the most we ever read from audioRecord, even if the codec input buffer is
        // larger. This is purely for fast'ish cancellation and not for latency.
        val maxSamplesInBytes = audioRecord.sampleRate / 10 * getFrameSize(audioRecord.format)

        var inputTimestamp = 0L
        var inputComplete = false
        val bufferInfo = MediaCodec.BufferInfo()
        val frameSize = getFrameSize(audioRecord.format)
        var trackIndex = -1

        while (true) {
            if (!inputComplete) {
                val inputBufferId = mediaCodec.dequeueInputBuffer(10000)
                if (inputBufferId >= 0) {
                    val buffer = mediaCodec.getInputBuffer(inputBufferId)!!

                    val maxRead = min(maxSamplesInBytes, buffer.remaining())
                    val n = audioRecord.read(buffer, maxRead)
                    if (n < 0) {
                        logE("Error when reading samples from ${audioRecord}: $n")
                        isCancelled = true
                        captureFailed = true
                    } else if (n == 0) {
                        // This should never be hit because AOSP guarantees that MediaCodec's
                        // ByteBuffers are direct buffers, but this is not publicly documented
                        // behavior
                        logE( "MediaCodec's ByteBuffer was not a direct buffer")
                        isCancelled = true
                    } else {
                        val frames = n / frameSize
                        inputTimestamp += frames * 1_000_000L / audioRecord.sampleRate
                    }

                    if (isCancelled) {
                        val duration = "%.1f".format(inputTimestamp / 1_000_000.0)
                        logD("Input complete after ${duration}s")
                        inputComplete = true
                    }

                    val flags = if (inputComplete) {
                        MediaCodec.BUFFER_FLAG_END_OF_STREAM
                    } else {
                        0
                    }

                    // Setting the presentation timestamp will cause `c2.android.flac.encoder`
                    // software encoder to crash with SIGABRT
                    mediaCodec.queueInputBuffer(inputBufferId, 0, n, 0, flags)
                } else if (inputBufferId != MediaCodec.INFO_TRY_AGAIN_LATER) {
                    logW("Unexpected input buffer dequeue error: $inputBufferId")
                }
            }

            val outputBufferId = mediaCodec.dequeueOutputBuffer(bufferInfo, 0)
            if (outputBufferId >= 0) {
                val buffer = mediaCodec.getOutputBuffer(outputBufferId)!!

                container.writeSamples(trackIndex, buffer, bufferInfo)

                mediaCodec.releaseOutputBuffer(outputBufferId, false)

                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    // Output has been fully written
                    break
                }
            } else if (outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                val outputFormat = mediaCodec.outputFormat
                logD("Output format changed to: $outputFormat")
                trackIndex = container.addTrack(outputFormat)
                container.start()
            } else if (outputBufferId != MediaCodec.INFO_TRY_AGAIN_LATER) {
                logW("Unexpected output buffer dequeue error: $outputBufferId")
            }
        }
    }

    companion object {
        private val TAG = RecorderThread::class.java.simpleName
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT

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

        private fun getFrameSize(audioFormat: AudioFormat): Int {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                audioFormat.frameSizeInBytes
            } else{
                // Hardcoded for Android 9 compatibility only
                assert(ENCODING == AudioFormat.ENCODING_PCM_16BIT)
                2 * audioFormat.channelCount
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