package com.chiller3.bcr

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

class Notifications(
    private val context: Context,
) {
    companion object {
        const val CHANNEL_ID_PERSISTENT = "persistent"
        const val CHANNEL_ID_FAILURE = "failure"
        const val CHANNEL_ID_SUCCESS = "success"

        private val LEGACY_CHANNEL_IDS = arrayOf("alerts")

        private var notificationId = 2
    }

    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    /**
     * Create a low priority notification channel for the persistent notification.
     */
    private fun createPersistentChannel() = NotificationChannel(
        CHANNEL_ID_PERSISTENT,
        context.getString(R.string.notification_channel_persistent_name),
        NotificationManager.IMPORTANCE_LOW,
    ).apply {
        description = context.getString(R.string.notification_channel_persistent_desc)
    }

    /**
     * Create a high priority notification channel for failure alerts.
     */
    private fun createFailureAlertsChannel() = NotificationChannel(
        CHANNEL_ID_FAILURE,
        context.getString(R.string.notification_channel_failure_name),
        NotificationManager.IMPORTANCE_HIGH,
    ).apply {
        description = context.getString(R.string.notification_channel_failure_desc)
    }

    /**
     * Create a high priority notification channel for success alerts.
     */
    private fun createSuccessAlertsChannel() = NotificationChannel(
        CHANNEL_ID_SUCCESS,
        context.getString(R.string.notification_channel_success_name),
        NotificationManager.IMPORTANCE_HIGH,
    ).apply {
        description = context.getString(R.string.notification_channel_success_desc)
    }

    /**
     * Ensure up-to-date notification channels exist, deleting legacy channels.
     */
    fun updateChannels() {
        notificationManager.createNotificationChannels(listOf(
            createPersistentChannel(),
            createFailureAlertsChannel(),
            createSuccessAlertsChannel(),
        ))
        LEGACY_CHANNEL_IDS.forEach { notificationManager.deleteNotificationChannel(it) }
    }

    /**
     * Create a persistent notification for use during recording. The notification appearance is
     * fully static and in progress recording is represented by the presence or absence of the
     * notification.
     */
    fun createPersistentNotification(@StringRes title: Int, @DrawableRes icon: Int): Notification {
        val notificationIntent = Intent(context, SettingsActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        )

        return Notification.Builder(context, CHANNEL_ID_PERSISTENT).run {
            setContentTitle(context.getText(title))
            setSmallIcon(icon)
            setContentIntent(pendingIntent)
            setOngoing(true)

            // Inhibit 10-second delay when showing persistent notification
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
            }
            build()
        }
    }

    private fun createAlertNotification(
        channel: String,
        @StringRes title: Int,
        @DrawableRes icon: Int,
        errorMsg: String?,
        file: OutputFile?,
    ): Notification =
        Notification.Builder(context, channel).run {
            val text = buildString {
                val errorMsgTrimmed = errorMsg?.trim()
                if (!errorMsgTrimmed.isNullOrBlank()) {
                    append(errorMsgTrimmed)
                }
                if (file != null) {
                    if (!isEmpty()) {
                        append("\n\n")
                    }
                    append(file.uri.formattedString)
                }
            }

            setContentTitle(context.getString(title))
            if (text.isNotBlank()) {
                setContentText(text)
                style = Notification.BigTextStyle()
            }
            setSmallIcon(icon)

            if (file != null) {
                // It is not possible to grant access to SAF URIs to other applications
                val wrappedUri = RecorderProvider.fromOrigUri(file.uri)

                val openIntent = PendingIntent.getActivity(
                    context,
                    0,
                    Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(wrappedUri, file.mimeType)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    },
                    PendingIntent.FLAG_IMMUTABLE,
                )
                val shareIntent = PendingIntent.getActivity(
                    context,
                    0,
                    Intent(Intent.ACTION_SEND).apply {
                        type = file.mimeType
                        putExtra(Intent.EXTRA_STREAM, wrappedUri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    },
                    PendingIntent.FLAG_IMMUTABLE,
                )

                addAction(Notification.Action.Builder(
                    null,
                    context.getString(R.string.notification_action_open),
                    openIntent,
                ).build())

                addAction(Notification.Action.Builder(
                    null,
                    context.getString(R.string.notification_action_share),
                    shareIntent,
                ).build())

                // Clicking on the notification behaves like the open action, except the
                // notification gets dismissed. The open and share actions do not dismiss the
                // notification.
                setContentIntent(openIntent)
                setAutoCancel(true)
            }

            build()
        }

    private fun notify(notification: Notification) {
        notificationManager.notify(notificationId, notification)
        ++notificationId
    }

    fun notifySuccess(
        @StringRes title: Int,
        @DrawableRes icon: Int,
        file: OutputFile,
    ) {
        notify(createAlertNotification(CHANNEL_ID_SUCCESS, title, icon, null, file))
    }

    fun notifyFailure(
        @StringRes title: Int,
        @DrawableRes icon: Int,
        errorMsg: String?,
        file: OutputFile?,
    ) {
        notify(createAlertNotification(CHANNEL_ID_FAILURE, title, icon, errorMsg, file))
    }

    fun dismissAll() {
        // This is safe to run at any time because it doesn't dismiss notifications belonging to
        // foreground services.
        notificationManager.cancelAll()
    }
}