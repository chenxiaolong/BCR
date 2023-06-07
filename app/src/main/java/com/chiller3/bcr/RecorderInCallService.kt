package com.chiller3.bcr

import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telecom.Call
import android.telecom.InCallService
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.chiller3.bcr.output.OutputFile
import kotlin.random.Random

class RecorderInCallService : InCallService(), RecorderThread.OnRecordingCompletedListener {
    companion object {
        private val TAG = RecorderInCallService::class.java.simpleName

        private val ACTION_PAUSE = "${RecorderInCallService::class.java.canonicalName}.pause"
        private val ACTION_RESUME = "${RecorderInCallService::class.java.canonicalName}.resume"
        private val ACTION_RESTORE = "${RecorderInCallService::class.java.canonicalName}.restore"
        private val ACTION_DELETE = "${RecorderInCallService::class.java.canonicalName}.delete"
        private const val EXTRA_TOKEN = "token"
        private const val EXTRA_NOTIFICATION_ID = "notification_id"
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var notificationManager: NotificationManager
    private lateinit var prefs: Preferences
    private lateinit var notifications: Notifications

    /**
     * Notification ID to use for the foreground service. Throughout the lifetime of the service, it
     * may be associated with different calls. It is not cancelled until all recorders exit.
     */
    private val foregroundNotificationId = Notifications.allocateNotificationId()

    /**
     * Notification IDs and their associated recorders. This indicates the desired state of the
     * notifications. It may not match the actual state until [updateForegroundState] is called.
     */
    private val notificationIdsToRecorders = HashMap<Int, RecorderThread>()

    private data class NotificationState(
        @StringRes val titleResId: Int,
        val message: String?,
        @DrawableRes val iconResId: Int,
        // We don't store the intents because Intent does not override equals()
        val actionsResIds: List<Int>,
    )

    /**
     * All notification IDs currently shown, along with their state. This is used to determine which
     * notifications should be cancelled after items are removed from [notificationIdsToRecorders].
     * The state is used for only applying updates when the state actually changes. Otherwise,
     * Android will block updates if they exceed the rate limit (10 updates per second).
     */
    private val allNotificationIds = HashMap<Int, NotificationState>()

    /**
     * Recording threads for each active call. When a call is disconnected, it is immediately
     * removed from this map.
     */
    private val callsToRecorders = HashMap<Call, RecorderThread>()

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

    private fun createActionIntent(notificationId: Int, action: String): Intent =
        Intent(this, RecorderInCallService::class.java).apply {
            this.action = action
            // The URI is not used for anything besides ensuring that the PendingIntents across
            // different notifications are unique. PendingIntent treats Intents that differ only in
            // the extras as the same.
            data = Uri.fromParts("notification", notificationId.toString(), null)
            putExtra(EXTRA_TOKEN, token)
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
        }

    override fun onCreate() {
        super.onCreate()

        notificationManager = getSystemService(NotificationManager::class.java)
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

            val notificationId = intent?.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
            if (notificationId == -1) {
                throw IllegalArgumentException("Invalid notification ID")
            }

            when (val action = intent?.action) {
                ACTION_PAUSE, ACTION_RESUME -> {
                    notificationIdsToRecorders[notificationId]?.isPaused = action == ACTION_PAUSE
                }
                ACTION_RESTORE, ACTION_DELETE -> {
                    notificationIdsToRecorders[notificationId]?.keepRecording =
                        action == ACTION_RESTORE
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

        if (call.parent != null) {
            Log.v(TAG, "Ignoring state change of conference call child")
        } else if (callState == Call.STATE_ACTIVE) {
            startRecording(call)
        } else if (callState == Call.STATE_DISCONNECTING || callState == Call.STATE_DISCONNECTED) {
            // This is necessary because onCallRemoved() might not be called due to firmware bugs
            requestStopRecording(call)
        }

        callsToRecorders[call]?.isHolding = callState == Call.STATE_HOLDING
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
        } else if (!callsToRecorders.containsKey(call)) {
            val recorder = try {
                RecorderThread(this, this, call)
            } catch (e: Exception) {
                notifyFailure(e.message, null)
                throw e
            }
            callsToRecorders[call] = recorder

            val notificationId = if (notificationIdsToRecorders.isEmpty()) {
                foregroundNotificationId
            } else {
                Notifications.allocateNotificationId()
            }
            notificationIdsToRecorders[notificationId] = recorder

            updateForegroundState()
            recorder.start()
        }
    }

    /**
     * Request the cancellation of the [RecorderThread].
     *
     * The [RecorderThread] is immediately removed from [callsToRecorders], but will remain in
     * [notificationIdsToRecorders] to keep the foreground service alive until the [RecorderThread]
     * exits and reports its status. The thread may exit and be removed from [callsToRecorders]
     * before this function is called if an error occurs during recording.
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

        val recorder = callsToRecorders[call]
        if (recorder != null) {
            recorder.cancel()

            callsToRecorders.remove(call)

            // Don't change the foreground state until the thread has exited
        }
    }

    /**
     * Notify the recording thread of call details changes.
     *
     * The recording thread uses call details for generating filenames.
     */
    private fun handleDetailsChange(call: Call, details: Call.Details) {
        val parentCall = call.parent
        val recorder = if (parentCall != null) {
            callsToRecorders[parentCall]
        } else {
            callsToRecorders[call]
        }

        recorder?.onCallDetailsChanged(call, details)
    }

    /**
     * Move to foreground, creating a persistent notification, when there are active calls or
     * recording threads that haven't finished exiting yet.
     */
    private fun updateForegroundState() {
        if (notificationIdsToRecorders.isEmpty()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            // Cancel and remove notifications for recorders that have exited
            for (notificationId in allNotificationIds.keys.minus(notificationIdsToRecorders.keys)) {
                // The foreground notification will be overwritten
                if (notificationId != foregroundNotificationId) {
                    notificationManager.cancel(notificationId)
                }
                allNotificationIds.remove(notificationId)
            }

            // Reassign the foreground notification to another recorder
            if (foregroundNotificationId !in notificationIdsToRecorders) {
                val iterator = notificationIdsToRecorders.iterator()
                val (notificationId, recorder) = iterator.next()
                iterator.remove()
                notificationManager.cancel(notificationId)
                allNotificationIds.remove(notificationId)
                notificationIdsToRecorders[foregroundNotificationId] = recorder
            }

            // Create/update notifications
            for ((notificationId, recorder) in notificationIdsToRecorders) {
                val titleResId: Int
                val actionResIds = mutableListOf<Int>()
                val actionIntents = mutableListOf<Intent>()
                val canShowDelete: Boolean

                when (recorder.state) {
                    RecorderThread.State.NOT_STARTED -> {
                        titleResId = R.string.notification_recording_initializing
                        canShowDelete = true
                    }
                    RecorderThread.State.RECORDING -> {
                        if (recorder.isHolding) {
                            titleResId = R.string.notification_recording_on_hold
                            // Don't allow changing the pause state while holding
                        } else if (recorder.isPaused) {
                            titleResId = R.string.notification_recording_paused
                            actionResIds.add(R.string.notification_action_resume)
                            actionIntents.add(createActionIntent(notificationId, ACTION_RESUME))
                        } else {
                            titleResId = R.string.notification_recording_in_progress
                            actionResIds.add(R.string.notification_action_pause)
                            actionIntents.add(createActionIntent(notificationId, ACTION_PAUSE))
                        }
                        canShowDelete = true
                    }
                    RecorderThread.State.FINALIZING, RecorderThread.State.COMPLETED -> {
                        titleResId = R.string.notification_recording_finalizing
                        canShowDelete = false
                    }
                }

                val message = StringBuilder(recorder.path.unredacted)

                if (canShowDelete) {
                    recorder.keepRecording?.let {
                        if (it) {
                            actionResIds.add(R.string.notification_action_delete)
                            actionIntents.add(createActionIntent(notificationId, ACTION_DELETE))
                        } else {
                            message.append("\n\n")
                            message.append(getString(R.string.notification_message_delete_at_end))
                            actionResIds.add(R.string.notification_action_restore)
                            actionIntents.add(createActionIntent(notificationId, ACTION_RESTORE))
                        }
                    }
                }

                val state = NotificationState(
                    titleResId,
                    message.toString(),
                    R.drawable.ic_launcher_quick_settings,
                    actionResIds,
                )
                if (state == allNotificationIds[notificationId]) {
                    // Avoid rate limiting
                    continue
                }

                val notification = notifications.createPersistentNotification(
                    state.titleResId,
                    state.message,
                    state.iconResId,
                    state.actionsResIds.zip(actionIntents),
                )

                if (notificationId == foregroundNotificationId) {
                    startForeground(notificationId, notification)
                } else {
                    notificationManager.notify(notificationId, notification)
                }

                allNotificationIds[notificationId] = state
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

    private fun onRecorderExited(recorder: RecorderThread) {
        // This may be an early exit if an error occurred while recording. Remove from the map to
        // make sure the thread doesn't receive any more call-related callbacks.
        if (callsToRecorders.entries.removeIf { it.value === recorder }) {
            Log.w(TAG, "$recorder exited before cancellation")
        }

        // The notification no longer needs to be shown. If this recorder was associated with the
        // foreground service notification, updateForegroundState() will reassign
        // foregroundNotificationId to another recorder.
        assert(notificationIdsToRecorders.entries.removeIf { it.value === recorder }) {
            "$recorder not found"
        }

        updateForegroundState()
    }

    override fun onRecordingStateChanged(thread: RecorderThread) {
        handler.post {
            updateForegroundState()
        }
    }

    override fun onRecordingCompleted(thread: RecorderThread, file: OutputFile?) {
        Log.i(TAG, "Recording completed: ${thread.id}: ${file?.redacted}")
        handler.post {
            onRecorderExited(thread)

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
            onRecorderExited(thread)

            notifyFailure(errorMsg, file)
        }
    }
}
