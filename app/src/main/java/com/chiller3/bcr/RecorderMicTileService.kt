package com.chiller3.bcr

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import kotlin.random.Random

class RecorderMicTileService : TileService(), RecorderThread.OnRecordingCompletedListener {
    companion object {
        private val TAG = RecorderMicTileService::class.java.simpleName

        private val ACTION_PAUSE = "${RecorderMicTileService::class.java.canonicalName}.pause"
        private val ACTION_RESUME = "${RecorderMicTileService::class.java.canonicalName}.resume"
        private const val EXTRA_TOKEN = "token"
    }

    private lateinit var notifications: Notifications
    private val handler = Handler(Looper.getMainLooper())

    private var recorder: RecorderThread? = null

    private var tileIsListening = false

    /**
     * Token value for all intents received by this instance of the service.
     *
     * For the pause/resume functionality, we cannot use a bound service because [TileService]
     * uses its own non-extensible [onBind] implementation. So instead, we rely on [onStartCommand].
     * However, because this service is required to be exported, the intents could potentially come
     * from third party apps and we don't want those interfering with the recordings.
     */
    private val token = Random.Default.nextBytes(128)

    private fun createBaseIntent(): Intent =
        Intent(this, RecorderMicTileService::class.java).apply {
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

        notifications = Notifications(this)
    }

    /** Handle intents triggered from notification actions for pausing and resuming. */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            val receivedToken = intent?.getByteArrayExtra(EXTRA_TOKEN)
            if (intent?.action != null && !receivedToken.contentEquals(token)) {
                throw IllegalArgumentException("Invalid token")
            }

            when (val action = intent?.action) {
                ACTION_PAUSE, ACTION_RESUME -> {
                    recorder!!.isPaused = action == ACTION_PAUSE
                    updateForegroundState()
                }
                null -> {
                    // Ignore. Hack to keep service alive longer than the tile lifecycle.
                }
                else -> throw IllegalArgumentException("Invalid action: $action")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to handle intent: $intent", e)
        }

        // Kill service if the only reason it is started is due to the intent
        if (recorder == null) {
            stopSelf(startId)
        }
        return START_NOT_STICKY
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
            updateForegroundState()
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

    private fun updateForegroundState() {
        if (recorder == null) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            if (recorder!!.isPaused) {
                startForeground(1, notifications.createPersistentNotification(
                    R.string.notification_recording_mic_paused,
                    R.drawable.ic_launcher_quick_settings,
                    R.string.notification_action_resume,
                    createResumeIntent(),
                ))
            } else {
                startForeground(1, notifications.createPersistentNotification(
                    R.string.notification_recording_mic_in_progress,
                    R.drawable.ic_launcher_quick_settings,
                    R.string.notification_action_pause,
                    createPauseIntent(),
                ))
            }
        }
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
        updateForegroundState()
        stopSelf()
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