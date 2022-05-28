package com.chiller3.bcr

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telecom.Call
import android.telecom.InCallService
import android.util.Log

class RecorderInCallService : InCallService(), RecorderThread.OnRecordingCompletedListener {
    companion object {
        private val TAG = RecorderInCallService::class.java.simpleName
    }

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

    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            super.onStateChanged(call, state)
            Log.d(TAG, "onStateChanged: $call, $state")

            handleStateChange(call)
        }

        override fun onDetailsChanged(call: Call, details: Call.Details) {
            super.onDetailsChanged(call, details)
            Log.d(TAG, "onDetailsChanged: $call, $details")

            handleDetailsChange(call, details)
        }
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        Log.d(TAG, "onCallAdded: $call")

        call.registerCallback(callback)
        handleStateChange(call)
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        Log.d(TAG, "onCallRemoved: $call")

        call.unregisterCallback(callback)
        handleStateChange(call)
    }

    /**
     * Start a new recording thread when a call becomes active and cancel it when it disconnects.
     *
     * When a call disconnects, the call is removed from [recorders] and [pendingExit] is
     * incremented. [pendingExit] gets decremented when the thread actually completes, which may be
     * before the call disconnects if an error occurred.
     */
    private fun handleStateChange(call: Call) {
        val state = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            call.details.state
        } else {
            @Suppress("DEPRECATION")
            call.state
        }

        if (state == Call.STATE_ACTIVE) {
            if (!Preferences.isCallRecordingEnabled(this)) {
                Log.v(TAG, "Call recording is disabled")
            } else if (!Permissions.haveRequired(this)) {
                Log.v(TAG, "Required permissions have not been granted")
            } else if (!recorders.containsKey(call)) {
                val recorder = RecorderThread(this, this, call)
                recorders[call] = recorder

                updateForegroundState()
                recorder.start()
            }
        } else if (state == Call.STATE_DISCONNECTING || state == Call.STATE_DISCONNECTED) {
            val recorder = recorders[call]
            if (recorder != null) {
                recorder.cancel()

                recorders.remove(call)

                // Don't change the foreground state until the thread has exited
                ++pendingExit
            }
        }
    }

    /**
     * Notify recording thread of call details changes.
     *
     * The recording thread uses call details for generating filenames.
     */
    private fun handleDetailsChange(call: Call, details: Call.Details) {
        // The call may not exist if this is called after handleStateChange with STATE_DISCONNECTING
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
        val builder = Notification.Builder(this, RecorderApplication.CHANNEL_ID)
        builder.setContentTitle(getText(R.string.recording_in_progress))
        builder.setSmallIcon(R.drawable.ic_launcher_foreground)
        builder.setContentIntent(pendingIntent)
        builder.setOngoing(true)

        // Inhibit 10-second delay when showing persistent notification
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }
        return builder.build()
    }

    private fun onThreadExited() {
        --pendingExit
        updateForegroundState()
    }

    override fun onRecordingCompleted(thread: RecorderThread, uri: Uri) {
        Log.i(TAG, "Recording completed: ${thread.id}: $uri")
        handler.post {
            onThreadExited()
        }
    }

    override fun onRecordingFailed(thread: RecorderThread, uri: Uri?) {
        Log.w(TAG, "Recording failed: ${thread.id}: $uri")
        handler.post {
            onThreadExited()
        }
    }
}