package com.chiller3.bcr

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log

class NotificationActionService : Service() {
    companion object {
        private val TAG = NotificationActionService::class.java.simpleName

        private val ACTION_DELETE_URI = "${NotificationActionService::class.java.canonicalName}.delete_uri"
        private const val EXTRA_REDACTED = "redacted"
        private const val EXTRA_NOTIFICATION_ID = "notification_id"

        private fun intentFromFile(context: Context, file: OutputFile): Intent =
            Intent(context, NotificationActionService::class.java).apply {
                setDataAndType(file.uri, file.mimeType)
                putExtra(EXTRA_REDACTED, file.redacted)
            }

        fun createDeleteUriIntent(context: Context, file: OutputFile, notificationId: Int): Intent =
            intentFromFile(context, file).apply {
                action = ACTION_DELETE_URI
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            }
    }

    private val handler = Handler(Looper.getMainLooper())

    private fun parseFileFromIntent(intent: Intent): OutputFile =
        OutputFile(
            intent.data!!,
            intent.getStringExtra(EXTRA_REDACTED)!!,
            intent.type!!,
        )

    private fun parseDeleteUriIntent(intent: Intent): Pair<OutputFile, Int> {
        val file = parseFileFromIntent(intent)

        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        if (notificationId < 0) {
            throw IllegalArgumentException("Invalid notification ID: $notificationId")
        }

        return Pair(file, notificationId)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int =
        try {
            when (intent?.action) {
                ACTION_DELETE_URI -> {
                    val (file, notificationId) = parseDeleteUriIntent(intent)
                    val documentFile = file.toDocumentFile(this)
                    val notificationManager = getSystemService(NotificationManager::class.java)

                    Thread {
                        Log.d(TAG, "Deleting: ${file.redacted}")
                        try {
                            documentFile.delete()
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to delete ${file.redacted}", e)
                        }

                        handler.post {
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
