package com.chiller3.bcr

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import android.telecom.Call
import android.telecom.PhoneAccount
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.chiller3.bcr.format.Encoder
import com.chiller3.bcr.format.Format
import com.chiller3.bcr.format.Formats
import com.chiller3.bcr.format.SampleRates
import java.io.IOException
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
) : Thread(RecorderThread::class.java.simpleName) {
    private val tag = "${RecorderThread::class.java.simpleName}/${id}"
    private val isDebug = BuildConfig.DEBUG || Preferences.isDebugMode(context)

    // Thread state
    @Volatile private var isCancelled = false
    private var captureFailed = false

    // Filename
    private val filenameLock = Object()
    private lateinit var filename: String
    private val redactions = HashMap<String, String>()

    // Format
    private val format: Format
    private val formatParam: UInt?
    private val sampleRate = SampleRates.fromPreferences(context)

    init {
        Log.i(tag, "Created thread for call: $call")

        onCallDetailsChanged(call.details)

        val savedFormat = Formats.fromPreferences(context)
        format = savedFormat.first
        formatParam = savedFormat.second
    }

    fun redact(msg: String): String {
        synchronized(filenameLock) {
            var result = msg

            for ((source, target) in redactions) {
                result = result.replace(source, target)
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

            Log.i(tag, "Updated filename due to call details change: ${redact(filename)}")
        }
    }

    override fun run() {
        var success = false
        var errorMsg: String? = null
        var resultUri: Uri? = null

        try {
            Log.i(tag, "Recording thread started")

            if (isCancelled) {
                Log.i(tag, "Recording cancelled before it began")
            } else {
                val initialFilename = synchronized(filenameLock) { filename }

                val (file, pfd) = openOutputFile(initialFilename, format.mimeTypeContainer)
                resultUri = file.uri

                pfd.use {
                    recordUntilCancelled(it)
                }

                val finalFilename = synchronized(filenameLock) { filename }
                if (finalFilename != initialFilename) {
                    Log.i(tag, "Renaming ${redact(initialFilename)} to ${redact(finalFilename)}")

                    if (file.renameTo(finalFilename)) {
                        resultUri = file.uri
                    } else {
                        Log.w(tag, "Failed to rename to final filename: ${redact(finalFilename)}")
                    }
                }

                success = !captureFailed
            }
        } catch (e: Exception) {
            Log.e(tag, "Error during recording", e)
            errorMsg = e.localizedMessage
        } finally {
            Log.i(tag, "Recording thread completed")

            try {
                if (isDebug) {
                    Log.d(tag, "Dumping logcat due to debug mode")
                    dumpLogcat()
                }
            } catch (e: Exception) {
                Log.w(tag, "Failed to dump logcat", e)
            }

            if (success) {
                listener.onRecordingCompleted(this, resultUri!!)
            } else {
                listener.onRecordingFailed(this, errorMsg, resultUri)
            }
        }
    }

    /**
     * Cancel current recording. This stops capturing audio after processing the next minimum buffer
     * size, but the thread does not exit until all data encoded so far has been written to the
     * output file.
     *
     * If called before [start], the thread will not record any audio not create an output file. In
     * this scenario, [OnRecordingCompletedListener.onRecordingFailed] will be called with a null
     * [Uri].
     */
    fun cancel() {
        isCancelled = true
    }

    private fun dumpLogcat() {
        openOutputFile("${filename}.log", "text/plain").pfd.use {
            Os.lseek(it.fileDescriptor, 0, OsConstants.SEEK_END)

            val process = ProcessBuilder("logcat", "-d").start()
            try {
                val data = process.inputStream.use { stream -> stream.readBytes() }
                Os.write(it.fileDescriptor, data, 0, data.size)
            } finally {
                process.waitFor()
            }
        }
    }

    data class OutputFile(val file: DocumentFile, val pfd: ParcelFileDescriptor)

    /**
     * Try to create and open a new output file in the user-chosen directory if possible and fall
     * back to the default output directory if not. [name] should not contain a file extension. The
     * file extension is automatically determined from [mimeType].
     *
     * @throws IOException if the file could not be created in either directory
     */
    private fun openOutputFile(name: String, mimeType: String): OutputFile {
        val userUri = Preferences.getSavedOutputDir(context)
        if (userUri != null) {
            try {
                // Only returns null on API <21
                val userDir = DocumentFile.fromTreeUri(context, userUri)!!
                Log.d(tag, "Using user-specified directory: ${userDir.uri}")

                return openOutputFileInDir(userDir, name, mimeType)
            } catch (e: Exception) {
                Log.e(tag, "Failed to open file in user-specified directory: $userUri", e)
            }
        }

        val fallbackDir = DocumentFile.fromFile(Preferences.getDefaultOutputDir(context))
        Log.d(tag, "Using fallback directory: ${fallbackDir.uri}")

        return openOutputFileInDir(fallbackDir, name, mimeType)
    }

    /**
     * Create and open a new output file with name [name] inside [directory]. [name] should not
     * contain a file extension. The extension is determined [mimeType]. The file extension is
     * automatically determined from [format].
     *
     * @throws IOException if file creation or opening fails
     */
    private fun openOutputFileInDir(
        directory: DocumentFile,
        name: String,
        mimeType: String,
    ): OutputFile {
        val file = directory.createFile(mimeType, name)
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
        AndroidProcess.setThreadPriority(AndroidProcess.THREAD_PRIORITY_URGENT_AUDIO)

        val minBufSize = AudioRecord.getMinBufferSize(sampleRate.toInt(), CHANNEL_CONFIG, ENCODING)
        if (minBufSize < 0) {
            throw Exception("Failure when querying minimum buffer size: $minBufSize")
        }
        Log.d(tag, "AudioRecord minimum buffer size: $minBufSize")

        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_CALL,
            sampleRate.toInt(),
            CHANNEL_CONFIG,
            ENCODING,
            // On some devices, MediaCodec occasionally has sudden spikes in processing time, so use
            // a large internal buffer to reduce the chance of overrun on the recording side.
            minBufSize * 20,
        )
        val initialBufSize = audioRecord.bufferSizeInFrames *
                audioRecord.format.frameSizeInBytesCompat
        Log.d(tag, "AudioRecord initial buffer size: $initialBufSize")

        Log.d(tag, "AudioRecord format: ${audioRecord.format}")

        // Where's my RAII? :(
        try {
            audioRecord.startRecording()

            try {
                val container = format.getContainer(pfd.fileDescriptor)

                try {
                    // audioRecord.format has the detected native sample rate
                    val mediaFormat = format.getMediaFormat(audioRecord.format, formatParam)
                    val encoder = format.getEncoder(mediaFormat, container)

                    try {
                        encoder.start()

                        try {
                            encodeLoop(audioRecord, encoder, minBufSize)
                        } finally {
                            encoder.stop()
                        }
                    } finally {
                        encoder.release()
                    }
                } finally {
                    container.release()
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
     * from [audioRecord] and the remaining output data from [encoder] will be written to the output
     * file. If [audioRecord] fails to capture data, the loop will behave as if [cancel] was called
     * (ie. abort, but ensuring that the output file is valid).
     *
     * The approximate amount of time to cancel reading from the audio source is the time it takes
     * to process the minimum buffer size. Additionally, additional time is needed to write out the
     * remaining encoded data to the output file.
     *
     * @param audioRecord [AudioRecord.startRecording] must have been called
     * @param encoder [Encoder.start] must have been called
     * @param bufSize Size of buffer to use for each [AudioRecord.read] operation
     *
     * @throws Exception if the audio recorder or encoder encounters an error
     */
    private fun encodeLoop(audioRecord: AudioRecord, encoder: Encoder, bufSize: Int) {
        var numFrames = 0L
        val frameSize = audioRecord.format.frameSizeInBytesCompat

        // Use a slightly larger buffer to reduce the chance of problems under load
        val buffer = ByteBuffer.allocateDirect(bufSize * 2)
        val bufferFrames = buffer.capacity().toLong() / frameSize
        val bufferNs = bufferFrames * 1_000_000_000L / audioRecord.sampleRate

        while (!isCancelled) {
            val begin = System.nanoTime()
            val n = audioRecord.read(buffer, buffer.remaining())
            val recordElapsed = System.nanoTime() - begin
            var encodeElapsed = 0L

            if (n < 0) {
                Log.e(tag, "Error when reading samples from $audioRecord: $n")
                isCancelled = true
                captureFailed = true
            } else if (n == 0) {
                Log.e(tag,  "Unexpected EOF from AudioRecord")
                isCancelled = true
            } else {
                buffer.limit(n)

                val encodeBegin = System.nanoTime()
                encoder.encode(buffer, false)
                encodeElapsed = System.nanoTime() - encodeBegin

                buffer.clear()

                numFrames += n / frameSize
            }

            val totalElapsed = System.nanoTime() - begin
            if (encodeElapsed > bufferNs) {
                Log.w(tag, "${encoder.javaClass.simpleName} took too long: " +
                        "timestamp=${numFrames.toDouble() / audioRecord.sampleRate}s, " +
                        "buffer=${bufferNs / 1_000_000.0}ms, " +
                        "total=${totalElapsed / 1_000_000.0}ms, " +
                        "record=${recordElapsed / 1_000_000.0}ms, " +
                        "encode=${encodeElapsed / 1_000_000.0}ms")
            }
        }

        // Signal EOF with empty buffer
        Log.d(tag, "Sending EOF to encoder")
        buffer.limit(buffer.position())
        encoder.encode(buffer, true)

        val durationSecs = numFrames.toDouble() / audioRecord.sampleRate
        Log.d(tag, "Input complete after ${"%.1f".format(durationSecs)}s")
    }

    companion object {
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
        fun onRecordingFailed(thread: RecorderThread, errorMsg: String?, uri: Uri?)
    }
}