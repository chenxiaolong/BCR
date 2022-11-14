package link.cure.recorder

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telecom.Call
import android.telecom.InCallService
import android.util.Log
import link.cure.recorder.api.MessagingAPIInterface
import link.cure.recorder.api.RetrofitBuilder
import link.cure.recorder.models.APIResponse
import link.cure.recorder.models.CallLogEvent
import link.cure.recorder.models.CallLogEventsBody
import io.sentry.Sentry
import kotlinx.coroutines.*
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.coroutines.CoroutineContext


class RecorderInCallService : InCallService(), RecorderThread.OnRecordingCompletedListener,
    CoroutineScope {
    companion object {
        private val TAG = RecorderInCallService::class.java.simpleName
    }

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job

    private lateinit var job: Job

    private val messagingAPIInterface: MessagingAPIInterface = RetrofitBuilder.messagingAPIInterface

    private lateinit var prefs: Preferences
    private val handler = Handler(Looper.getMainLooper())

    /**
     * Recording threads for each active call. When a call is disconnected, it is immediately
     * removed from this map and [pendingExit] is incremented.
     */
    private val recorders = HashMap<Call, RecorderThread>()

    /**
     * Number of threads pending exit after the call has been disconnected. This can be negative if
     * the recording thread fails before the call is disconnected.
     */
    private var pendingExit = 0

    private var failedNotificationId = 2


    /**
     * Returns the name for the given [Call] state.
     */
    private fun getStateName(state: Int): String {
        return when (state) {
            0 -> "NEW"
            1 -> "DIALING"
            2 -> "RINGING"
            3 -> "HOLDING"
            4 -> "ACTIVE"
            7 -> "DISCONNECTED"
            8 -> "SELECT_PHONE_ACCOUNT"
            9 -> "CONNECTING"
            10 -> "DISCONNECTING"
            11 -> "PULLING_CALL"
            13 -> "SIMULATED_RINGING"
            else -> "UNKNOWN"
        }
    }

    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            super.onStateChanged(call, state)
            Log.d(TAG, "onStateChanged: $call, $state")

            syncCallState(call = call, state)

            handleStateChange(call, state)
        }

        override fun onDetailsChanged(call: Call, details: Call.Details) {
            super.onDetailsChanged(call, details)
            Log.d(TAG, "onDetailsChanged: ${call.details}, ${details.handle}, $details")

            handleDetailsChange(call, details)

            // Due to firmware bugs, on older Samsung firmware, this callback (with the DISCONNECTED
            // state) is the only notification we receive that a call ended
            handleStateChange(call, null)
        }

        override fun onCallDestroyed(call: Call) {
            super.onCallDestroyed(call)
            Log.d(TAG, "onCallDestroyed: $call")

            requestStopRecording(call)
        }
    }

    override fun onCreate() {
        super.onCreate()
        job = Job()
        prefs = Preferences(this)
    }

    /**
     * Always called when the telephony framework becomes aware of a new call.
     *
     * This is the entry point for a new call. [callback] is always registered to keep track of
     * state changes.
     */
    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        Log.d(TAG, "onCallAdded: $call")

        // The callback is unregistered in requestStopRecording()
        call.registerCallback(callback)

        syncCallState(call = call, null)

        // In case the call is already in the active state
        handleStateChange(call, null)
    }

    /**
     * Called when the telephony framework destroys a call.
     *
     * This will request the cancellation of the recording, even if [call] happens to not be in one
     * of the disconnecting states.
     *
     * This is NOT guaranteed to be called, notably on older Samsung firmware, due to bugs in the
     * telephony framework. As a result, [handleStateChange] stop the recording if the call enters a
     * disconnecting state.
     */
    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        Log.d(TAG, "onCallRemoved: $call")

        // Unconditionally request the recording to stop, even if it's not in a disconnecting state
        // since no further events will be received for the call.
        requestStopRecording(call)
    }

    private fun syncCallState(call: Call, state: Int?) {
        val callState = state ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            call.details.state
        } else {
            @Suppress("DEPRECATION")
            call.state
        }

        launch {
            val token: String = "Token " + prefs.userToken
            try {
                val response: APIResponse? = withContext(Dispatchers.IO) {
                    messagingAPIInterface.postCallLogs(
                        token = token,
                        body = CallLogEventsBody(
                            logs = arrayListOf(
                                CallLogEvent(
                                    phone = call.details.handle.schemeSpecificPart.toString(),
                                    direction = if (call.details.callDirection == 0) "INCOMING" else "OUTGOING",
                                    creationTimestamp = (call.details.creationTimeMillis / 1000),
                                    timestamp = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC),
                                    type = getStateName(callState)
                                )
                            )
                        )
                    )
                }
                Log.d(TAG, response.toString())
            } catch (e: Exception) {
                Sentry.captureException(e)
            }

        }
    }

    /**
     * Start or stop recording based on the [call] state.
     *
     * If the state is [Call.STATE_ACTIVE], then recording will begin. If the state is either
     * [Call.STATE_DISCONNECTING] or [Call.STATE_DISCONNECTED], then the cancellation of the active
     * recording will be requested. If [state] is null, then the call state is queried from [call].
     */
    private fun handleStateChange(call: Call, state: Int?) {
        val callState = state ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            call.details.state
        } else {
            @Suppress("DEPRECATION")
            call.state
        }

        Log.d(
            TAG,
            "handleStateChange: ${call.details.handle}, ${call.details.creationTimeMillis}, $state, $callState"
        )

        if (callState == Call.STATE_ACTIVE) {
            startRecording(call)

        } else if (callState == Call.STATE_DISCONNECTING || callState == Call.STATE_DISCONNECTED) {
            // This is necessary because onCallRemoved() might not be called due to firmware bugs
            requestStopRecording(call)
        }
    }

    /**
     * Start a [RecorderThread] for [call].
     *
     * If call recording is disabled or the required permissions aren't granted, then no
     * [RecorderThread] will be created.
     *
     * This function is idempotent.
     */
    private fun startRecording(call: Call) {
        if (!prefs.isCallRecordingEnabled) {
            Log.v(TAG, "Call recording is disabled")
        } else if (!Permissions.haveRequired(this)) {
            Log.v(TAG, "Required permissions have not been granted")
        } else if (!recorders.containsKey(call)) {
            val recorder = try {
                RecorderThread(this, this, call)
            } catch (e: Exception) {
                notifyError(e.message, null)
                throw e
            }
            recorders[call] = recorder

            updateForegroundState()
            recorder.start()
        }
    }

    /**
     * Request the cancellation of the [RecorderThread].
     *
     * The [RecorderThread] is immediately removed from [recorders], but [pendingExit] will be
     * incremented to keep the foreground notification alive until the [RecorderThread] exits and
     * reports its status. The thread may exit, decrementing [pendingExit], before this function is
     * called if an error occurs during recording.
     *
     * This function will also unregister [callback] from the call since it's no longer necessary to
     * track further state changes.
     *
     * This function is idempotent.
     */
    private fun requestStopRecording(call: Call) {
        // This is safe to call multiple times in the AOSP implementation and also in heavily
        // modified builds, like Samsung's firmware. If this ever becomes a problem, we can keep
        // track of which calls have callbacks registered.
        call.unregisterCallback(callback)

        val recorder = recorders[call]
        if (recorder != null) {
            recorder.cancel()

            recorders.remove(call)

            // Don't change the foreground state until the thread has exited
            ++pendingExit
        }
    }

    /**
     * Notify the recording thread of call details changes.
     *
     * The recording thread uses call details for generating filenames.
     */
    private fun handleDetailsChange(call: Call, details: Call.Details) {
        recorders[call]?.onCallDetailsChanged(details)
    }

    /**
     * Move to foreground, creating a persistent notification, when there are active calls or
     * recording threads that haven't finished exiting yet.
     */
    private fun updateForegroundState() {
        if (recorders.isEmpty() && pendingExit == 0) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            startForeground(1, createPersistentNotification())
        }
    }

    /**
     * Create a persistent notification for use during recording. The notification appearance is
     * fully static and in progress call recording is represented by the presence or absence of the
     * notification.
     */
    private fun createPersistentNotification(): Notification {
        val notificationIntent = Intent(this, SettingsActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        )

        return Notification.Builder(this, RecorderApplication.CHANNEL_ID_PERSISTENT).run {
            setContentTitle(getText(R.string.notification_recording_in_progress))
            setSmallIcon(R.drawable.ic_launcher_quick_settings)
            setContentIntent(pendingIntent)
            setOngoing(true)

            // Inhibit 10-second delay when showing persistent notification
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
            }
            build()
        }
    }

    private fun createRecordingFailedNotification(errorMsg: String?, uri: Uri?): Notification =
        Notification.Builder(this, RecorderApplication.CHANNEL_ID_ALERTS).run {
            val text = buildString {
                val errorMsgTrimmed = errorMsg?.trim()
                if (!errorMsgTrimmed.isNullOrBlank()) {
                    append(errorMsgTrimmed)
                }
                if (uri != null) {
                    if (!isEmpty()) {
                        append("\n\n")
                    }
                    append(uri)
                }
            }

            setContentTitle(getString(R.string.notification_recording_failed))
            if (text.isNotBlank()) {
                setContentText(text)
                style = Notification.BigTextStyle()
            }
            setSmallIcon(R.drawable.ic_launcher_quick_settings)

            build()
        }

    private fun notifyError(errorMsg: String?, uri: Uri?) {
        val notification = createRecordingFailedNotification(errorMsg, uri)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(failedNotificationId, notification)
        ++failedNotificationId
    }

    private fun onThreadExited() {
        --pendingExit
        updateForegroundState()
    }

    override fun onRecordingCompleted(thread: RecorderThread, uri: Uri) {
        Log.i(TAG, "Recording completed: ${thread.id}: ${thread.redact(uri)}")
        launch {
            try {
                val token: String = "Token " + prefs.userToken
                val response: APIResponse? = withContext(Dispatchers.IO) {
                    Log.d(TAG, "making Request")
                    val recording = File(uri.path!!)
                    val filePart = MultipartBody.Part.createFormData(
                        "recording_file",
                        recording.name,
                        RequestBody.create(MediaType.parse("audio/ogg"), recording)
                    )
                    val creationTimestamp = MultipartBody.Part.createFormData(
                        "creation_timestamp", RecorderThread.timestampFromFilename(recording.name)!!
                            .toEpochSecond().toString()
                    )
                    Log.d(
                        TAG,
                        RecorderThread.timestampFromFilename(recording.name)!!.toEpochSecond()
                            .toString()
                    )
                    Log.d(TAG, filePart.toString())
                    messagingAPIInterface.postRecordingFile(
                        token = token,
                        file = filePart,
                        creationTimestamp = creationTimestamp

                    )
                }
                Log.d(TAG, response.toString())
            } catch (e: Exception) {
                Sentry.captureException(e)
            }

        }
        handler.post {
            onThreadExited()
        }
    }

    override fun onRecordingFailed(thread: RecorderThread, errorMsg: String?, uri: Uri?) {
        Log.w(TAG, "Recording failed: ${thread.id}: ${uri?.let { thread.redact(it) }}")
        handler.post {
            onThreadExited()

            notifyError(errorMsg, uri)
        }
    }
}