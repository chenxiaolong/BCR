package com.chiller3.bcr

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log

class RecorderMicTileService : TileService(), RecorderThread.OnRecordingCompletedListener {
    companion object {
        private val TAG = RecorderMicTileService::class.java.simpleName
    }

    private lateinit var notifications: Notifications
    private val handler = Handler(Looper.getMainLooper())

    private var recorder: RecorderThread? = null

    private var tileIsListening = false

    override fun onCreate() {
        super.onCreate()

        notifications = Notifications(this)
    }

    override fun onStartListening() {
        super.onStartListening()

        tileIsListening = true

        refreshTileState()
    }

    override fun onStopListening() {
        super.onStopListening()

        tileIsListening = false
    }

    override fun onClick() {
        super.onClick()

        if (!Permissions.haveRequired(this)) {
            val intent = Intent(this, SettingsActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivityAndCollapse(intent)
        } else if (recorder == null) {
            startRecording()
        } else {
            requestStopRecording()
        }

        refreshTileState()
    }

    private fun refreshTileState() {
        val tile = qsTile

        // Tile.STATE_UNAVAILABLE is intentionally not used when permissions haven't been granted.
        // Clicking the tile in that state does not invoke the click handler, so it wouldn't be
        // possible to launch SettingsActivity to grant the permissions.
        if (Permissions.haveRequired(this) && recorder != null) {
            tile.state = Tile.STATE_ACTIVE
        } else {
            tile.state = Tile.STATE_INACTIVE
        }

        tile.updateTile()
    }

    /**
     * Start the [RecorderThread].
     *
     * If the required permissions aren't granted, then the service will stop.
     *
     * This function is idempotent.
     */
    private fun startRecording() {
        if (recorder == null) {
            recorder = try {
                RecorderThread(this, this, null)
            } catch (e: Exception) {
                notifyFailure(e.message, null)
                throw e
            }

            // Ensure the service lives past the tile lifecycle
            startForegroundService(Intent(this, this::class.java))
            startForeground(1, notifications.createPersistentNotification(
                R.string.notification_recording_mic_in_progress,
                R.drawable.ic_launcher_quick_settings,
            ))
            recorder!!.start()
        }
    }

    /**
     * Request the cancellation of the [RecorderThread].
     *
     * The foreground notification stays alive until the [RecorderThread] exits and reports its
     * status. The thread may exit before this function is called if an error occurs during
     * recording.
     *
     * This function is idempotent.
     */
    private fun requestStopRecording() {
        recorder?.cancel()
    }

    private fun notifySuccess(file: OutputFile) {
        notifications.notifySuccess(
            R.string.notification_recording_mic_succeeded,
            R.drawable.ic_launcher_quick_settings,
            file,
        )
    }

    private fun notifyFailure(errorMsg: String?, file: OutputFile?) {
        notifications.notifyFailure(
            R.string.notification_recording_mic_failed,
            R.drawable.ic_launcher_quick_settings,
            errorMsg,
            file,
        )
    }

    private fun onThreadExited() {
        recorder = null

        if (tileIsListening) {
            refreshTileState()
        }

        // The service no longer needs to live past the tile lifecycle
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onRecordingCompleted(thread: RecorderThread, file: OutputFile) {
        Log.i(TAG, "Recording completed: ${thread.id}: ${file.redacted}")
        handler.post {
            onThreadExited()

            notifySuccess(file)
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