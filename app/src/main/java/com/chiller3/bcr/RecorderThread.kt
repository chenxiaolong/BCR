package com.chiller3.bcr

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.system.Os
import android.telecom.Call
import android.telecom.PhoneAccount
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.net.toFile
import androidx.documentfile.provider.DocumentFile
import com.chiller3.bcr.format.Encoder
import com.chiller3.bcr.format.Format
import com.chiller3.bcr.format.SampleRate
import java.lang.Process
import java.nio.ByteBuffer
import java.text.ParsePosition
import java.time.*
import java.time.format.DateTimeFormatterBuilder
import java.time.format.DateTimeParseException
import java.time.format.SignStyle
import java.time.temporal.ChronoField
import java.time.temporal.Temporal
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
    private val prefs = Preferences(context)
    private val isDebug = prefs.isDebugMode

    // Thread state
    @Volatile private var isCancelled = false
    private var captureFailed = false

    // Pause state
    @Volatile var isPaused = prefs.initiallyPaused
        set(value) {
            field = value
            if (!value) {
                wasEverResumed = true
            }

            Log.d(tag, "Pause state updated: $value")
        }
    private var wasEverResumed = !isPaused

    // Timestamp
    private lateinit var callTimestamp: ZonedDateTime
    private var formatter = FORMATTER

    // Filename
    private val filenameLock = Object()
    private var pendingCallDetails: Call.Details? = null
    private lateinit var lastCallDetails: Call.Details
    private lateinit var filenameTemplate: FilenameTemplate
    private lateinit var filename: String
    private val redactions = HashMap<String, String>()
    private val redactor = object : OutputDirUtils.Redactor {
        override fun redact(msg: String): String {
            synchronized(filenameLock) {
                var result = msg

                for ((source, target) in redactions) {
                    result = result.replace(source, target)
                }

                return result
            }
        }

        override fun redact(uri: Uri): String = redact(Uri.decode(uri.toString()))
    }
    private val dirUtils = OutputDirUtils(context, redactor)

    // Format
    private val format: Format
    private val formatParam: UInt?
    private val sampleRate = SampleRate.fromPreferences(prefs)

    // Logging
    private lateinit var logcatFilename: String
    private lateinit var logcatFile: DocumentFile
    private lateinit var logcatProcess: Process

    init {
        Log.i(tag, "Created thread for call: $call")
        Log.i(tag, "Initially paused: $isPaused")

        onCallDetailsChanged(call.details)

        val savedFormat = Format.fromPreferences(prefs)
        format = savedFormat.first
        formatParam = savedFormat.second
    }

    /**
     * Update [filename] with information from [details].
     *
     * This function holds a lock on [filenameLock] until it returns.
     */
    fun onCallDetailsChanged(details: Call.Details) {
        synchronized(filenameLock) {
            if (!this::filenameTemplate.isInitialized) {
                // Thread hasn't started yet, so we haven't loaded the filename template
                pendingCallDetails = details
                return
            }

            lastCallDetails = details

            filename = filenameTemplate.evaluate {
                when {
                    it == "date" || it.startsWith("date:") -> {
                        val instant = Instant.ofEpochMilli(details.creationTimeMillis)
                        callTimestamp = ZonedDateTime.ofInstant(instant, ZoneId.systemDefault())

                        val colon = it.indexOf(":")
                        if (colon >= 0) {
                            val pattern = it.substring(colon + 1)
                            Log.d(tag, "Using custom datetime pattern: $pattern")

                            try {
                                formatter = DateTimeFormatterBuilder()
                                    .appendPattern(pattern)
                                    .toFormatter()
                            } catch (e: Exception) {
                                Log.w(tag, "Invalid custom datetime pattern: $pattern; using default", e)
                            }
                        }

                        return@evaluate formatter.format(callTimestamp)
                    }
                    it == "direction" -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            when (details.callDirection) {
                                Call.Details.DIRECTION_INCOMING -> return@evaluate "in"
                                Call.Details.DIRECTION_OUTGOING -> return@evaluate "out"
                                Call.Details.DIRECTION_UNKNOWN -> {}
                            }
                        }
                    }
                    it == "sim_slot" -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                            && context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE)
                            == PackageManager.PERMISSION_GRANTED
                            && context.packageManager.hasSystemFeature(
                                PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION)) {
                            val subscriptionManager = context.getSystemService(SubscriptionManager::class.java)

                            // Only append SIM slot ID if the device has multiple active SIMs
                            if (subscriptionManager.activeSubscriptionInfoCount > 1) {
                                val telephonyManager = context.getSystemService(TelephonyManager::class.java)
                                val subscriptionId = telephonyManager.getSubscriptionId(details.accountHandle)
                                val subscriptionInfo = subscriptionManager.getActiveSubscriptionInfo(subscriptionId)

                                return@evaluate "${subscriptionInfo.simSlotIndex + 1}"
                            }
                        }
                    }
                    it == "phone_number" -> {
                        if (details.handle?.scheme == PhoneAccount.SCHEME_TEL) {
                            redactions[details.handle.schemeSpecificPart] = "<phone number>"

                            return@evaluate details.handle.schemeSpecificPart
                        }
                    }
                    it == "caller_name" -> {
                        val callerName = details.callerDisplayName?.trim()
                        if (!callerName.isNullOrBlank()) {
                            redactions[callerName] = "<caller name>"

                            return@evaluate callerName
                        }
                    }
                    it == "contact_name" -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            val contactName = details.contactDisplayName?.trim()
                            if (!contactName.isNullOrBlank()) {
                                redactions[contactName] = "<contact name>"

                                return@evaluate contactName
                            }
                        }
                    }
                    else -> {
                        Log.w(tag, "Unknown filename template variable: $it")
                    }
                }

                null
            }
            // AOSP's SAF automatically replaces invalid characters with underscores, but just in
            // case an OEM fork breaks that, do the replacement ourselves to prevent directory
            // traversal attacks.
                .replace('/', '_').trim()

            Log.i(tag, "Updated filename due to call details change: ${redactor.redact(filename)}")
        }
    }

    override fun run() {
        var success = false
        var errorMsg: String? = null
        var resultUri: Uri? = null

        synchronized(filenameLock) {
            // We initially do not allow custom filename templates because SAF is extraordinarily
            // slow on some devices. Even with the our custom findFileFast() implementation, simply
            // checking for the existence of the template may take >500ms.
            filenameTemplate = FilenameTemplate.load(context, false)

            onCallDetailsChanged(pendingCallDetails!!)
            pendingCallDetails = null
        }

        startLogcat()

        try {
            Log.i(tag, "Recording thread started")

            if (isCancelled) {
                Log.i(tag, "Recording cancelled before it began")
            } else {
                val initialFilename = synchronized(filenameLock) { filename }
                val outputFile = dirUtils.createFileInDefaultDir(initialFilename, format.mimeTypeContainer)
                resultUri = outputFile.uri

                try {
                    dirUtils.openFile(outputFile, true).use {
                        recordUntilCancelled(it)
                        Os.fsync(it.fileDescriptor)
                    }
                } finally {
                    val finalFilename = synchronized(filenameLock) {
                        filenameTemplate = FilenameTemplate.load(context, true)

                        onCallDetailsChanged(lastCallDetails)
                        filename
                    }
                    if (finalFilename != initialFilename) {
                        Log.i(tag, "Renaming ${redactor.redact(initialFilename)} to ${redactor.redact(finalFilename)}")

                        if (outputFile.renameToPreserveExt(finalFilename)) {
                            resultUri = outputFile.uri
                        } else {
                            Log.w(tag, "Failed to rename to final filename: ${redactor.redact(finalFilename)}")
                        }
                    }

                    if (wasEverResumed) {
                        dirUtils.tryMoveToUserDir(outputFile)?.let {
                            resultUri = it.uri
                        }
                    } else {
                        Log.i(tag, "Deleting because recording was never resumed: ${redactor.redact(finalFilename)}")
                        outputFile.delete()
                        resultUri = null
                    }

                    processRetention()
                }

                success = !captureFailed
            }
        } catch (e: Exception) {
            Log.e(tag, "Error during recording", e)

            errorMsg = buildString {
                val elem = e.stackTrace.find { it.className.startsWith("android.media.") }
                if (elem != null) {
                    append(context.getString(R.string.notification_internal_android_error,
                        "${elem.className}.${elem.methodName}"))
                    append("\n\n")
                }

                append(e.localizedMessage)
            }
        } finally {
            Log.i(tag, "Recording thread completed")

            try {
                stopLogcat()
            } catch (e: Exception) {
                Log.w(tag, "Failed to dump logcat", e)
            }

            val outputFile = resultUri?.let {
                OutputFile(it, redactor.redact(it), format.mimeTypeContainer)
            }

            if (success) {
                listener.onRecordingCompleted(this, outputFile)
            } else {
                listener.onRecordingFailed(this, errorMsg, outputFile)
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
        Log.d(tag, "Requested cancellation")
        isCancelled = true
    }

    private fun startLogcat() {
        if (!isDebug) {
            return
        }

        assert(!this::logcatProcess.isInitialized) { "logcat already started" }

        Log.d(tag, "Starting log file (${BuildConfig.VERSION_NAME})")

        logcatFilename = synchronized(filenameLock) { "${filename}.log" }
        logcatFile = dirUtils.createFileInDefaultDir(logcatFilename, "text/plain")
        logcatProcess = ProcessBuilder("logcat", "*:V")
            // This is better than -f because the logcat implementation calls fflush() when the
            // output stream is stdout. logcatFile is guaranteed to have file:// scheme because it's
            // created in the default output directory.
            .redirectOutput(logcatFile.uri.toFile())
            .redirectErrorStream(true)
            .start()
    }

    private fun stopLogcat() {
        if (!isDebug) {
            return
        }

        assert(this::logcatProcess.isInitialized) { "logcat not started" }

        try {
            try {
                Log.d(tag, "Stopping log file")

                // Give logcat a bit of time to flush the output. It does not have any special
                // handling to flush buffers when interrupted.
                sleep(1000)

                logcatProcess.destroy()
            } finally {
                logcatProcess.waitFor()
            }
        } finally {
            val finalLogcatFilename = synchronized(filenameLock) { "${filename}.log" }

            if (finalLogcatFilename != logcatFilename) {
                Log.i(tag, "Renaming ${redactor.redact(logcatFilename)} to ${redactor.redact(finalLogcatFilename)}")

                if (!logcatFile.renameToPreserveExt(finalLogcatFilename)) {
                    Log.w(tag, "Failed to rename to final filename: ${redactor.redact(finalLogcatFilename)}")
                }
            }

            dirUtils.tryMoveToUserDir(logcatFile)
        }
    }

    private fun timestampFromFilename(name: String): Temporal? {
        try {
            val redacted = redactTruncate(name)

            // The date is guaranteed to be at the beginning of the filename. Try to parse it,
            // ignoring unparsed text at the end.
            val pos = ParsePosition(0)
            val parsed = formatter.parse(name, pos)

            val timestamp = try {
                parsed.query(ZonedDateTime::from)
            } catch (e: DateTimeException) {
                // A custom pattern might not specify the time zone
                parsed.query(LocalDateTime::from)
            }

            Log.d(tag, "Parsed $timestamp from $redacted; length=${name.length}; parsed=${pos.index}")

            return timestamp
        } catch (e: DateTimeParseException) {
            // Ignore
        }

        return null
    }

    /**
     * Delete files older than the specified retention period.
     *
     * The "current time" is [callTimestamp], not the actual current time and the timestamp of past
     * recordings is based on the filename, not the file modification time. Incorrectly-named files
     * are ignored.
     */
    private fun processRetention() {
        val directory = prefs.outputDir?.let {
            // Only returns null on API <21
            DocumentFile.fromTreeUri(context, it)!!
        } ?: DocumentFile.fromFile(prefs.defaultOutputDir)

        val retention = when (val r = Retention.fromPreferences(prefs)) {
            NoRetention -> {
                Log.i(tag, "Keeping all existing files")
                return
            }
            is DaysRetention -> r.toDuration()
        }
        Log.i(tag, "Retention period is $retention")

        for ((item, name) in directory.listFilesWithNames()) {
            if (name == null) {
                continue
            }
            val redacted = redactTruncate(name)

            val timestamp = timestampFromFilename(name)
            if (timestamp == null) {
                Log.w(tag, "Ignoring unrecognized filename: $redacted")
                continue
            }

            val diff = Duration.between(timestamp, callTimestamp)

            if (diff > retention) {
                Log.i(tag, "Deleting $redacted ($timestamp)")
                if (!item.delete()) {
                    Log.w(tag, "Failed to delete: $redacted")
                }
            }
        }
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

        val minBufSize = AudioRecord.getMinBufferSize(
            sampleRate.value.toInt(), CHANNEL_CONFIG, ENCODING)
        if (minBufSize < 0) {
            throw Exception("Failure when querying minimum buffer size: $minBufSize")
        }
        Log.d(tag, "AudioRecord minimum buffer size: $minBufSize")

        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_CALL,
            sampleRate.value.toInt(),
            CHANNEL_CONFIG,
            ENCODING,
            // On some devices, MediaCodec occasionally has sudden spikes in processing time, so use
            // a larger internal buffer to reduce the chance of overrun on the recording side.
            minBufSize * 6,
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
     * @param bufSize Minimum buffer size for each [AudioRecord.read] operation
     *
     * @throws Exception if the audio recorder or encoder encounters an error
     */
    private fun encodeLoop(audioRecord: AudioRecord, encoder: Encoder, bufSize: Int) {
        var numFramesTotal = 0L
        var numFramesEncoded = 0L
        val frameSize = audioRecord.format.frameSizeInBytesCompat

        // Use a slightly larger buffer to reduce the chance of problems under load
        val factor = 2
        val buffer = ByteBuffer.allocateDirect(bufSize * factor)
        val bufferFrames = buffer.capacity().toLong() / frameSize
        val bufferNs = bufferFrames * 1_000_000_000L / audioRecord.sampleRate
        Log.d(tag, "Buffer is ${buffer.capacity()} bytes, $bufferFrames frames, ${bufferNs}ns")

        while (!isCancelled) {
            val begin = System.nanoTime()
            // We do a non-blocking read because on Samsung devices, when the call ends, the audio
            // device immediately stops producing data and blocks forever until the next call is
            // active.
            val n = audioRecord.read(buffer, buffer.remaining(), AudioRecord.READ_NON_BLOCKING)
            val recordElapsed = System.nanoTime() - begin
            var encodeElapsed = 0L

            if (n < 0) {
                Log.e(tag, "Error when reading samples from $audioRecord: $n")
                isCancelled = true
                captureFailed = true
            } else if (n == 0) {
                // Wait for the wall clock equivalent of the minimum buffer size
                sleep(bufferNs / 1_000_000L / factor)
                continue
            } else {
                buffer.limit(n)

                val encodeBegin = System.nanoTime()

                // If paused, keep recording, but throw away the data
                if (!isPaused) {
                    encoder.encode(buffer, false)
                    numFramesEncoded += n / frameSize
                }

                numFramesTotal += n / frameSize

                encodeElapsed = System.nanoTime() - encodeBegin

                buffer.clear()
            }

            val totalElapsed = System.nanoTime() - begin
            if (encodeElapsed > bufferNs) {
                Log.w(tag, "${encoder.javaClass.simpleName} took too long: " +
                        "timestampTotal=${numFramesTotal.toDouble() / audioRecord.sampleRate}s, " +
                        "timestampEncode=${numFramesEncoded.toDouble() / audioRecord.sampleRate}s, " +
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

        val durationSecsTotal = numFramesTotal.toDouble() / audioRecord.sampleRate
        val durationSecsEncoded = numFramesEncoded.toDouble() / audioRecord.sampleRate
        Log.d(tag, "Input complete after ${"%.1f".format(durationSecsTotal)}s " +
                "(${"%.1f".format(durationSecsEncoded)}s encoded)")
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

        private fun redactTruncate(msg: String): String = buildString {
            val n = 2

            if (msg.length > 2 * n) {
                append(msg.substring(0, n))
            }
            append("<...>")
            if (msg.length > 2 * n) {
                append(msg.substring(msg.length - n))
            }
        }
    }

    interface OnRecordingCompletedListener {
        /**
         * Called when the recording completes successfully. [file] is the output file. If [file] is
         * null, then the recording was started in the paused state and the output file was deleted
         * because the user never resumed it.
         */
        fun onRecordingCompleted(thread: RecorderThread, file: OutputFile?)

        /**
         * Called when an error occurs during recording. If [file] is not null, it points to the
         * output file containing partially recorded audio. If [file] is null, then either the
         * output file could not be created or the thread was cancelled before it was started.
         */
        fun onRecordingFailed(thread: RecorderThread, errorMsg: String?, file: OutputFile?)
    }
}