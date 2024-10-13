/*
 * SPDX-FileCopyrightText: 2022-2024 Andrew Gunnerson
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.chiller3.bcr

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.ParcelFileDescriptor
import android.system.Os
import android.telecom.Call
import android.util.Log
import androidx.core.net.toFile
import androidx.documentfile.provider.DocumentFile
import com.chiller3.bcr.extension.deleteIfEmptyDir
import com.chiller3.bcr.extension.frameSizeInBytesCompat
import com.chiller3.bcr.extension.listFilesWithPathsRecursively
import com.chiller3.bcr.extension.phoneNumber
import com.chiller3.bcr.extension.toDocumentFile
import com.chiller3.bcr.format.Encoder
import com.chiller3.bcr.format.Format
import com.chiller3.bcr.format.NoParamInfo
import com.chiller3.bcr.format.RangedParamInfo
import com.chiller3.bcr.format.RangedParamType
import com.chiller3.bcr.output.CallMetadata
import com.chiller3.bcr.output.CallMetadataCollector
import com.chiller3.bcr.output.DaysRetention
import com.chiller3.bcr.output.NoRetention
import com.chiller3.bcr.output.OutputDirUtils
import com.chiller3.bcr.output.OutputFile
import com.chiller3.bcr.output.OutputFilenameGenerator
import com.chiller3.bcr.output.OutputPath
import com.chiller3.bcr.output.PhoneNumber
import com.chiller3.bcr.output.Retention
import com.chiller3.bcr.rule.RecordRule
import org.json.JSONObject
import java.lang.Process
import java.nio.ByteBuffer
import java.time.Duration
import java.util.concurrent.atomic.AtomicReference
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
 * @param parentCall Used for determining the output filename. References to it and its children are
 * kept in the object.
 */
class RecorderThread(
    private val context: Context,
    private val listener: OnRecordingCompletedListener,
    private val parentCall: Call,
) : Thread(RecorderThread::class.java.simpleName) {
    private val tag = "${RecorderThread::class.java.simpleName}/${id}"
    private val prefs = Preferences(context)

    enum class State {
        NOT_STARTED,
        RECORDING,
        FINALIZING,
        COMPLETED,
    }

    // Thread state
    @Volatile var state = State.NOT_STARTED
        private set
    @Volatile private var isCancelled = false

    enum class KeepState {
        KEEP,
        DISCARD,
        DISCARD_TOO_SHORT,
    }

    /**
     * Whether to preserve the recording.
     *
     * This is initially set to null while the [RecordRule]s are being processed. Once computed,
     * this field is set to the computed value. The value can be changed, including from other
     * threads, in case the user wants to override the rules during the middle of the call.
     */
    private val _keepRecording = AtomicReference<KeepState>()
    var keepRecording: KeepState?
        get() = _keepRecording.get()
        set(value) {
            require(value != null)

            _keepRecording.set(value)
            Log.d(tag, "Keep state updated: $value")

            listener.onRecordingStateChanged(this)
        }

    private fun keepRecordingCompareAndSet(expected: KeepState?, value: KeepState?) {
        require(value != null)

        if (_keepRecording.compareAndSet(expected, value)) {
            Log.d(tag, "Keep state updated: $value")

            listener.onRecordingStateChanged(this)
        }
    }

    /**
     * Whether the user paused the recording.
     *
     * Safe to update from other threads.
     */
    // Pause state
    @Volatile var isPaused = false
        set(value) {
            field = value
            Log.d(tag, "Pause state updated: $value")

            listener.onRecordingStateChanged(this)
        }

    /**
     * Whether the call is currently on hold.
     *
     * Safe to update from other threads.
     */
    @Volatile var isHolding = false
        set(value) {
            field = value
            Log.d(tag, "Holding state updated: $value")

            listener.onRecordingStateChanged(this)
        }

    // Filename
    private val callMetadataCollector = CallMetadataCollector(context, parentCall)
    private val outputFilenameGenerator = OutputFilenameGenerator(context)
    private val dirUtils = OutputDirUtils(context, outputFilenameGenerator.redactor)
    val outputPath: OutputPath
        get() = outputFilenameGenerator.generate(callMetadataCollector.callMetadata)

    private val minDuration: Int

    // Format
    private val format: Format
    private val formatParam: UInt?
    private val sampleRate: UInt

    // Logging
    private lateinit var logcatPath: OutputPath
    private lateinit var logcatFile: DocumentFile
    private lateinit var logcatProcess: Process

    init {
        Log.i(tag, "Created thread for call: $parentCall")

        minDuration = prefs.minDuration

        val savedFormat = Format.fromPreferences(prefs)
        format = savedFormat.first
        formatParam = savedFormat.second
        sampleRate = savedFormat.third ?: format.sampleRateInfo.default
    }

    fun onCallDetailsChanged(call: Call, details: Call.Details) {
        callMetadataCollector.updateCallDetails(call, details)
        listener.onRecordingStateChanged(this)
    }

    private fun evaluateRules() {
        if (keepRecording != null) {
            return
        }

        val numbers = hashSetOf<PhoneNumber>()

        if (parentCall.details.hasProperty(Call.Details.PROPERTY_CONFERENCE)) {
            for (childCall in parentCall.children) {
                childCall.details?.phoneNumber?.let { numbers.add(it) }
            }
        } else {
            parentCall.details?.phoneNumber?.let { numbers.add(it) }
        }

        Log.i(tag, "Evaluating record rules for ${numbers.size} phone number(s)")

        val rules = prefs.recordRules ?: Preferences.DEFAULT_RECORD_RULES
        val keep = try {
            RecordRule.evaluate(context, rules, numbers)
        } catch (e: Exception) {
            Log.w(tag, "Failed to evaluate record rules", e)
            // Err on the side of caution
            true
        }

        keepRecordingCompareAndSet(
            null,
            if (keep) {
                if (minDuration > 0) {
                    KeepState.DISCARD_TOO_SHORT
                } else {
                    KeepState.KEEP
                }
            } else {
                KeepState.DISCARD
            },
        )

        listener.onRecordingStateChanged(this)
    }

    override fun run() {
        var status: Status = Status.Cancelled
        var outputDocFile: DocumentFile? = null
        val additionalFiles = ArrayList<OutputFile>()

        startLogcat()

        try {
            Log.i(tag, "Recording thread started")

            if (isCancelled) {
                Log.i(tag, "Recording cancelled before it began")
            } else {
                state = State.RECORDING
                listener.onRecordingStateChanged(this)

                evaluateRules()

                val initialPath = outputPath
                outputDocFile = dirUtils.createFileInDefaultDir(
                    initialPath.value, format.mimeTypeContainer)

                var recordingInfo: RecordingInfo? = null

                try {
                    dirUtils.openFile(outputDocFile, true).use {
                        recordingInfo = recordUntilCancelled(it)
                        Os.fsync(it.fileDescriptor)
                    }

                    status = Status.Succeeded
                } finally {
                    state = State.FINALIZING
                    listener.onRecordingStateChanged(this)

                    callMetadataCollector.update(true)
                    val finalPath = outputPath

                    if (keepRecording == KeepState.KEEP) {
                        dirUtils.tryMoveToOutputDir(
                            outputDocFile,
                            finalPath.value,
                            format.mimeTypeContainer,
                        )?.let {
                            outputDocFile = it
                        }

                        writeMetadataFile(finalPath.value, recordingInfo)?.let {
                            additionalFiles.add(it)
                        }
                    } else {
                        Log.i(tag, "Deleting recording: $finalPath")
                        outputDocFile!!.delete()
                        outputDocFile = null

                        status = Status.Discarded(DiscardReason.Intentional)
                    }

                    processRetention()
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error during recording", e)

            if (e is PureSilenceException) {
                outputDocFile?.delete()
                outputDocFile = null

                additionalFiles.forEach {
                    it.toDocumentFile(context).delete()
                }
                additionalFiles.clear()

                val packageName = parentCall.details.accountHandle.componentName.packageName
                status = Status.Discarded(DiscardReason.Silence(packageName))
            } else {
                val mediaFrame = e.stackTrace.find { it.className.startsWith("android.media.") }
                val component = if (mediaFrame != null) {
                    FailureComponent.AndroidMedia(mediaFrame)
                } else {
                    FailureComponent.Other
                }

                status = Status.Failed(component, e)
            }
        } finally {
            Log.i(tag, "Recording thread completed")

            try {
                val logcatOutput = stopLogcat()

                // Log files are always kept when an error occurs to avoid the hassle of having the
                // user manually enable debug mode and needing to reproduce the problem.
                if (prefs.isDebugMode || status is Status.Failed) {
                    additionalFiles.add(logcatOutput)
                } else {
                    Log.d(tag, "No need to preserve logcat")
                    logcatOutput.toDocumentFile(context).delete()
                }
            } catch (e: Exception) {
                Log.w(tag, "Failed to dump logcat", e)
            }

            val outputFile = outputDocFile?.let {
                OutputFile(
                    it.uri,
                    outputFilenameGenerator.redactor.redact(it.uri),
                    format.mimeTypeContainer,
                )
            }

            state = State.COMPLETED
            listener.onRecordingStateChanged(this)
            listener.onRecordingCompleted(this, outputFile, additionalFiles, status)
        }
    }

    /**
     * Cancel current recording. This stops capturing audio after processing the next minimum buffer
     * size, but the thread does not exit until all data encoded so far has been written to the
     * output file.
     *
     * If called before [start], the thread will not record any audio not create an output file. In
     * this scenario, the status will be reported as [Status.Cancelled].
     */
    fun cancel() {
        Log.d(tag, "Requested cancellation")
        isCancelled = true
    }

    private fun getLogcatPath(): OutputPath {
        return outputPath.let {
            val path = it.value.mapIndexed { i, p ->
                p + if (i == it.value.size - 1) { ".log" } else { "" }
            }

            it.copy(value = path, redacted = it.redacted + ".log")
        }
    }

    private fun startLogcat() {
        assert(!this::logcatProcess.isInitialized) { "logcat already started" }

        Log.d(tag, "Starting log file (${BuildConfig.VERSION_NAME})")

        logcatPath = getLogcatPath()
        logcatFile = dirUtils.createFileInDefaultDir(logcatPath.value, MIME_LOGCAT)
        logcatProcess = ProcessBuilder("logcat", "*:V")
            // This is better than -f because the logcat implementation calls fflush() when the
            // output stream is stdout. logcatFile is guaranteed to have file:// scheme because it's
            // created in the default output directory.
            .redirectOutput(logcatFile.uri.toFile())
            .redirectErrorStream(true)
            .start()
    }

    private fun stopLogcat(): OutputFile {
        assert(this::logcatProcess.isInitialized) { "logcat not started" }

        var uri = logcatFile.uri

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
            val finalLogcatPath = getLogcatPath()
            dirUtils.tryMoveToOutputDir(logcatFile, finalLogcatPath.value, MIME_LOGCAT)?.let {
                uri = it.uri
            }
        }

        return OutputFile(uri, outputFilenameGenerator.redactor.redact(uri), MIME_LOGCAT)
    }

    private fun writeMetadataFile(path: List<String>, recordingInfo: RecordingInfo?): OutputFile? {
        if (!prefs.writeMetadata) {
            Log.i(tag, "Metadata writing is disabled")
            return null
        }

        Log.i(tag, "Writing metadata file")

        try {
            val formatJson = JSONObject().apply {
                put("type", format.name)
                put("mime_type_container", format.mimeTypeContainer)
                put("mime_type_audio", format.mimeTypeAudio)
                put("parameter_type", when (val info = format.paramInfo) {
                    NoParamInfo -> "none"
                    is RangedParamInfo -> when (info.type) {
                        RangedParamType.CompressionLevel -> "compression_level"
                        RangedParamType.Bitrate -> "bitrate"
                    }
                })
                put("parameter", (formatParam ?: format.paramInfo.default).toInt())
            }
            val recordingJson = if (recordingInfo != null) {
                JSONObject().apply {
                    put("frames_total", recordingInfo.framesTotal)
                    put("frames_encoded", recordingInfo.framesEncoded)
                    put("sample_rate", recordingInfo.sampleRate)
                    put("channel_count", recordingInfo.channelCount)
                    put("duration_secs_total", recordingInfo.durationSecsTotal)
                    put("duration_secs_encoded", recordingInfo.durationSecsEncoded)
                    put("buffer_frames", recordingInfo.bufferFrames)
                    put("buffer_overruns", recordingInfo.bufferOverruns)
                    put("was_ever_paused", recordingInfo.wasEverPaused)
                    put("was_ever_holding", recordingInfo.wasEverHolding)
                }
            } else {
                JSONObject.NULL
            }
            val outputJson = JSONObject().apply {
                put("format", formatJson)
                put("recording", recordingJson)
            }
            val metadataJson = callMetadataCollector.callMetadata.toJson(context).apply {
                put("output", outputJson)
            }
            val metadataBytes = metadataJson.toString(4).toByteArray()

            // Always create in the default directory and then move to ensure that we don't race
            // with the direct boot file migration process.
            var metadataFile = dirUtils.createFileInDefaultDir(path, MIME_METADATA)
            dirUtils.openFile(metadataFile, true).use {
                writeFully(it.fileDescriptor, metadataBytes, 0, metadataBytes.size)
            }
            dirUtils.tryMoveToOutputDir(metadataFile, path, MIME_METADATA)?.let {
                metadataFile = it
            }

            return OutputFile(
                metadataFile.uri,
                outputFilenameGenerator.redactor.redact(metadataFile.uri),
                MIME_METADATA,
            )
        } catch (e: Exception) {
            Log.w(tag, "Failed to write metadata file", e)
            return null
        }
    }

    /**
     * Delete files older than the specified retention period.
     *
     * The "current time" is [CallMetadata.timestamp], not the actual current time. The timestamp of
     * past recordings is based on the filename, not the file modification time. Incorrectly-named
     * files are ignored.
     */
    private fun processRetention() {
        val directory = prefs.outputDirOrDefault.toDocumentFile(context)

        val retention = when (val r = Retention.fromPreferences(prefs)) {
            NoRetention -> {
                Log.i(tag, "Keeping all existing files")
                return
            }
            is DaysRetention -> r.toDuration()
        }
        Log.i(tag, "Retention period is $retention")

        val potentiallyEmptyDirs = mutableListOf<Pair<DocumentFile, List<String>>>()

        for ((item, itemPath) in directory.listFilesWithPathsRecursively()) {
            if (item.isDirectory) {
                potentiallyEmptyDirs.add(Pair(item, itemPath))
                continue
            }

            val redacted = OutputFilenameGenerator.redactTruncate(itemPath.joinToString("/"))

            val timestamp = outputFilenameGenerator.parseTimestampFromPath(itemPath)
            if (timestamp == null) {
                Log.w(tag, "Ignoring unrecognized path: $redacted")
                continue
            }

            val diff = Duration.between(timestamp, callMetadataCollector.callMetadata.timestamp)

            if (diff > retention) {
                Log.i(tag, "Deleting $redacted ($timestamp)")
                if (!item.delete()) {
                    Log.w(tag, "Failed to delete: $redacted")
                }
            }
        }

        for ((dir, dirPath) in potentiallyEmptyDirs.asReversed()) {
            if (dir.deleteIfEmptyDir()) {
                val redacted = OutputFilenameGenerator.redactTruncate(dirPath.joinToString("/"))
                Log.i(tag, "Deleted empty directory: $redacted")
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
    private fun recordUntilCancelled(pfd: ParcelFileDescriptor): RecordingInfo {
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
                            return encodeLoop(audioRecord, encoder, minBufSize)
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
    private fun encodeLoop(
        audioRecord: AudioRecord,
        encoder: Encoder,
        bufSize: Int,
    ): RecordingInfo {
        var numFramesTotal = 0L
        var numFramesEncoded = 0L
        var bufferOverruns = 0
        var wasEverPaused = false
        var wasEverHolding = false
        var wasReadSamplesError = false
        var wasPureSilence = true
        val frameSize = audioRecord.format.frameSizeInBytesCompat

        // Use a slightly larger buffer to reduce the chance of problems under load
        val factor = 2
        val buffer = ByteBuffer.allocateDirect(bufSize * factor)
        val bufferFrames = buffer.capacity().toLong() / frameSize
        val bufferNs = bufferFrames * 1_000_000_000L /
                audioRecord.sampleRate /
                audioRecord.channelCount
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
                wasReadSamplesError = true
            } else if (n == 0) {
                // Wait for the wall clock equivalent of the minimum buffer size
                sleep(bufferNs / 1_000_000L / factor)
                continue
            } else {
                buffer.limit(n)

                if (wasPureSilence) {
                    for (i in 0 until n / 2) {
                        if (buffer.getShort(2 * i) != 0.toShort()) {
                            wasPureSilence = false
                            break
                        }
                    }
                }

                val encodeBegin = System.nanoTime()

                // If paused by the user or holding, keep recording, but throw away the data
                if (!isPaused && !isHolding) {
                    encoder.encode(buffer, false)
                    numFramesEncoded += n / frameSize
                } else {
                    wasEverPaused = wasEverPaused || isPaused
                    wasEverHolding = wasEverHolding || isHolding
                }

                numFramesTotal += n / frameSize

                encodeElapsed = System.nanoTime() - encodeBegin

                buffer.clear()
            }

            val totalElapsed = System.nanoTime() - begin
            if (encodeElapsed > bufferNs) {
                bufferOverruns += 1
                Log.w(tag, "${encoder.javaClass.simpleName} took too long: " +
                        "timestampTotal=${numFramesTotal.toDouble() / audioRecord.sampleRate /
                                audioRecord.channelCount}s, " +
                        "timestampEncode=${numFramesEncoded.toDouble() / audioRecord.sampleRate /
                                audioRecord.channelCount}s, " +
                        "buffer=${bufferNs / 1_000_000.0}ms, " +
                        "total=${totalElapsed / 1_000_000.0}ms, " +
                        "record=${recordElapsed / 1_000_000.0}ms, " +
                        "encode=${encodeElapsed / 1_000_000.0}ms")
            }

            val secondsEncoded = numFramesEncoded / audioRecord.sampleRate / audioRecord.channelCount
            if (secondsEncoded >= minDuration) {
                keepRecordingCompareAndSet(KeepState.DISCARD_TOO_SHORT, KeepState.KEEP)
            }
        }

        if (wasReadSamplesError) {
            throw ReadSamplesException()
        } else if (wasPureSilence) {
            throw PureSilenceException()
        }

        // Signal EOF with empty buffer
        Log.d(tag, "Sending EOF to encoder")
        buffer.limit(buffer.position())
        encoder.encode(buffer, true)

        val recordingInfo = RecordingInfo(
            numFramesTotal,
            numFramesEncoded,
            audioRecord.sampleRate,
            audioRecord.channelCount,
            bufferFrames,
            bufferOverruns,
            wasEverPaused,
            wasEverHolding,
        )

        Log.d(tag, "Input complete: $recordingInfo")

        return recordingInfo
    }

    companion object {
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT

        const val MIME_LOGCAT = "text/plain"
        const val MIME_METADATA = "application/json"
    }

    private data class RecordingInfo(
        val framesTotal: Long,
        val framesEncoded: Long,
        val sampleRate: Int,
        val channelCount: Int,
        val bufferFrames: Long,
        val bufferOverruns: Int,
        val wasEverPaused: Boolean,
        val wasEverHolding: Boolean,
    ) {
        val durationSecsTotal = framesTotal.toDouble() / sampleRate / channelCount
        val durationSecsEncoded = framesEncoded.toDouble() / sampleRate / channelCount

        override fun toString() = buildString {
            append("Total: $framesTotal frames (${"%.1f".format(durationSecsTotal)}s)")
            append(", Encoded: $framesEncoded frames (${"%.1f".format(durationSecsEncoded)}s)")
            append(", Sample rate: $sampleRate")
            append(", Channel count: $channelCount")
            append(", Buffer frames: $bufferFrames")
            append(", Buffer overruns: $bufferOverruns")
            append(", Was ever paused: $wasEverPaused")
            append(", Was ever holding: $wasEverHolding")
        }
    }

    private class ReadSamplesException(cause: Throwable? = null)
        : Exception("Failed to read audio samples", cause)

    private class PureSilenceException(cause: Throwable? = null)
        : Exception("Audio contained pure silence", cause)

    sealed interface FailureComponent {
        data class AndroidMedia(val stackFrame: StackTraceElement) : FailureComponent

        data object Other : FailureComponent
    }

    sealed interface DiscardReason {
        data object Intentional : DiscardReason

        data class Silence(val callPackage: String) : DiscardReason
    }

    sealed interface Status {
        data object Succeeded : Status

        data class Failed(val component: FailureComponent, val exception: Exception?) : Status

        data class Discarded(val reason: DiscardReason) : Status

        data object Cancelled : Status
    }

    interface OnRecordingCompletedListener {
        /**
         * Called when the pause state, keep state, or output filename are changed.
         */
        fun onRecordingStateChanged(thread: RecorderThread)

        /**
         * Called when the recording completes, successfully or otherwise. [file] is the output
         * file.
         *
         * [file] may be null in several scenarios:
         * * The call matched a record rule that defaults to discarding the recording
         * * The user intentionally chose to discard the recording via the notification
         * * The output file could not be created
         * * The thread was cancelled before it started
         *
         * Note that for most errors, the output file is *not* deleted.
         *
         * [additionalFiles] are additional files associated with the main output file and should be
         * deleted along with the main file if the user chooses to do so via the notification. These
         * files may exist even if [file] is null (eg. the log file).
         */
        fun onRecordingCompleted(
            thread: RecorderThread,
            file: OutputFile?,
            additionalFiles: List<OutputFile>,
            status: Status,
        )
    }
}
