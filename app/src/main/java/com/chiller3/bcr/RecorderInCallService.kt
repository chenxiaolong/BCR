package com.chiller3.bcr

import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telecom.Call
import android.telecom.InCallService
import android.util.Log
import kotlin.random.Random

class RecorderInCallService : InCallService(), RecorderThread.OnRecordingCompletedListener {
    companion object {
        private val TAG = RecorderInCallService::class.java.simpleName

        private val ACTION_PAUSE = "${RecorderInCallService::class.java.canonicalName}.pause"
        private val ACTION_RESUME = "${RecorderInCallService::class.java.canonicalName}.resume"
        private const val EXTRA_TOKEN = "token"
    }

    private lateinit var prefs: Preferences
    private lateinit var notifications: Notifications
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

    /**
     * Token value for all intents received by this instance of the service.
     *
     * For the pause/resume functionality, we cannot use a bound service because [InCallService]
     * uses its own non-extensible [onBind] implementation. So instead, we rely on [onStartCommand].
     * However, because this service is required to be exported, the intents could potentially come
     * from third party apps and we don't want those interfering with the recordings.
     */
    private val token = Random.Default.nextBytes(128)

    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            super.onStateChanged(call, state)
            Log.d(TAG, "onStateChanged: $call, $state")

            handleStateChange(call, state)
        }

        override fun onDetailsChanged(call: Call, details: Call.Details) {
            super.onDetailsChanged(call, details)
            Log.d(TAG, "onDetailsChanged: $call, $details")

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

    private fun createBaseIntent(): Intent =
        Intent(this, RecorderInCallService::class.java).apply {
            putExtra(EXTRA_TOKEN, token)
        }

    private fun createPauseIntent(): Intent =
        createBaseIntent().apply {
            action = ACTION_PAUSE
        }

    private fun createResumeIntent(): Intent =
        createBaseIntent().apply {
            action = ACTION_RESUME
        }

    override fun onCreate() {
        super.onCreate()

        prefs = Preferences(this)
        notifications = Notifications(this)
    }

    /** Handle intents triggered from notification actions for pausing and resuming. */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            val receivedToken = intent?.getByteArrayExtra(EXTRA_TOKEN)
            if (!receivedToken.contentEquals(token)) {
                throw IllegalArgumentException("Invalid token")
            }

            when (val action = intent?.action) {
                ACTION_PAUSE, ACTION_RESUME -> {
                    for ((_, recorder) in recorders) {
                        recorder.isPaused = action == ACTION_PAUSE
                    }
                    updateForegroundState()
                }
                else -> throw IllegalArgumentException("Invalid action: $action")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to handle intent: $intent", e)
        }

        // All actions are oneshot actions that should not be redelivered if a restart occurs
        stopSelf(startId)
        return START_NOT_STICKY
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

        Log.d(TAG, "handleStateChange: $call, $state, $callState")

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
                notifyFailure(e.message, null)
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
            if (recorders.any { it.value.isPaused }) {
                startForeground(1, notifications.createPersistentNotification(
                    R.string.notification_recording_paused,
                    R.drawable.ic_launcher_quick_settings,
                    R.string.notification_action_resume,
                    createResumeIntent(),
                ))
            } else {
                startForeground(1, notifications.createPersistentNotification(
                    R.string.notification_recording_in_progress,
                    R.drawable.ic_launcher_quick_settings,
                    R.string.notification_action_pause,
                    createPauseIntent(),
                ))
            }
            notifications.vibrateIfEnabled(Notifications.CHANNEL_ID_PERSISTENT)
        }
    }

    private fun notifySuccess(file: OutputFile) {
        notifications.notifySuccess(
            R.string.notification_recording_succeeded,
            R.drawable.ic_launcher_quick_settings,
            file,
        )
    }

    private fun notifyFailure(errorMsg: String?, file: OutputFile?) {
        notifications.notifyFailure(
            R.string.notification_recording_failed,
            R.drawable.ic_launcher_quick_settings,
            errorMsg,
            file,
        )
    }

    private fun onThreadExited() {
        --pendingExit
        updateForegroundState()
    }

    override fun onRecordingCompleted(thread: RecorderThread, file: OutputFile?) {
        Log.i(TAG, "Recording completed: ${thread.id}: ${file?.redacted}")
        handler.post {
            onThreadExited()

            // If the recording was initially paused and the user never resumed it, there's no
            // output file, so nothing needs to be shown.
            if (file != null) {
                notifySuccess(file)
            }
        }
    }

    override fun onRecordingFailed(thread: RecorderThread, errorMsg: String?, file: OutputFile?) {
        Log.w(TAG, "Recording failed: ${thread.id}: ${file?.redacted}")
        handler.post {
            onThreadExited()

            notifyFailure(errorMsg, file)
        }
    }
}
