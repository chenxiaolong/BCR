package com.chiller3.bcr

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.content.IntentCompat
import com.chiller3.bcr.output.OutputFile

class NotificationActionService : Service() {
    companion object {
        private val TAG = NotificationActionService::class.java.simpleName

        private val ACTION_DELETE_URI = "${NotificationActionService::class.java.canonicalName}.delete_uri"
        private const val EXTRA_FILES = "files"
        private const val EXTRA_NOTIFICATION_ID = "notification_id"

        fun createDeleteUriIntent(
            context: Context,
            files: List<OutputFile>,
            notificationId: Int,
        ) = Intent(context, NotificationActionService::class.java).apply {
            action = ACTION_DELETE_URI
            // Unused, but guarantees filterEquals() uniqueness for use with PendingIntents
            val uniqueSsp = files.asSequence().map { it.uri.toString() }.joinToString("\u0000")
            data = Uri.fromParts("unused", uniqueSsp, null)
            putExtra(EXTRA_FILES, ArrayList(files))
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
        }
    }

    private val handler = Handler(Looper.getMainLooper())

    private fun parseDeleteUriIntent(intent: Intent): Pair<List<OutputFile>, Int> {
        // This uses IntentCompat because of an Android 13 bug where using the new APIs that take a
        // class option causes a NullPointerException in release builds.
        // https://issuetracker.google.com/issues/274185314
        val files = IntentCompat.getParcelableArrayListExtra(
            intent, EXTRA_FILES, OutputFile::class.java)
            ?: throw IllegalArgumentException("No files specified")

        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        if (notificationId < 0) {
            throw IllegalArgumentException("Invalid notification ID: $notificationId")
        }

        return Pair(files, notificationId)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int =
        try {
            when (intent?.action) {
                ACTION_DELETE_URI -> {
                    val (files, notificationId) = parseDeleteUriIntent(intent)
                    val notificationManager = getSystemService(NotificationManager::class.java)

                    Thread {
                        for (file in files) {
                            val documentFile = file.toDocumentFile(this)

                            Log.d(TAG, "Deleting: ${file.redacted}")
                            try {
                                documentFile.delete()
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to delete ${file.redacted}", e)
                            }
                        }

                        handler.post {
                            Log.i(TAG, "Cancelling notification $notificationId")
                            notificationManager.cancel(notificationId)
                            stopSelf(startId)
                        }
                    }.start()
                }
                else -> throw IllegalArgumentException("Invalid action: ${intent?.action}")
            }

            START_REDELIVER_INTENT
        } catch (e: Exception) {
            val redactedIntent = intent?.let { Intent(it) }?.apply {
                setDataAndType(Uri.fromParts("redacted", "", ""), type)
            }

            Log.w(TAG, "Failed to handle intent: $redactedIntent", e)
            stopSelf(startId)

            START_NOT_STICKY
        }
}
