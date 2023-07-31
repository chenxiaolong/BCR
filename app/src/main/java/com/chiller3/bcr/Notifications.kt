package com.chiller3.bcr

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.chiller3.bcr.extension.formattedString
import com.chiller3.bcr.output.OutputFile
import com.chiller3.bcr.settings.SettingsActivity
import java.util.*

class Notifications(
    private val context: Context,
) {
    companion object {
        private val TAG = Notifications::class.java.simpleName

        const val CHANNEL_ID_PERSISTENT = "persistent"
        const val CHANNEL_ID_FAILURE = "failure"
        const val CHANNEL_ID_SUCCESS = "success"

        private val LEGACY_CHANNEL_IDS = arrayOf("alerts")

        /** For access to system/internal resource values. */
        private val systemRes = Resources.getSystem()

        /**
         * Hardcoded fallback vibration pattern.
         *
         * This is the same as what AOSP defines in VibratorHelper (newer versions) or
         * NotificationManagerService (older versions). In practice, unless an OEM completely
         * removes the config_defaultNotificationVibePattern array resource, this is never used.
         */
        private val DEFAULT_VIBRATE_PATTERN = longArrayOf(0, 250, 250, 250)

        /** Get resource integer array as a long array. */
        @Suppress("SameParameterValue")
        private fun getLongArray(resources: Resources, resId: Int): LongArray {
            val array = resources.getIntArray(resId)
            val result = LongArray(array.size)
            for (i in array.indices) {
                result[i] = array[i].toLong()
            }
            return result
        }

        /**
         * Get the default notification pattern from the system internal resources.
         *
         * This is the pattern that is used by default for notifications.
         */
        @SuppressLint("DiscouragedApi")
        private val defaultPattern = try {
            getLongArray(systemRes, systemRes.getIdentifier(
                "config_defaultNotificationVibePattern", "array", "android"))
        } catch (e: Exception) {
            Log.w(TAG, "System vibration pattern not found; using hardcoded default", e)
            DEFAULT_VIBRATE_PATTERN
        }
    }

    private val prefs = Preferences(context)
    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    /**
     * Create a high priority notification channel for the persistent notification.
     */
    private fun createPersistentChannel() = NotificationChannel(
        CHANNEL_ID_PERSISTENT,
        context.getString(R.string.notification_channel_persistent_name),
        NotificationManager.IMPORTANCE_HIGH,
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
     * Ensure notification channels are up-to-date.
     *
     * Legacy notification channels are deleted without migrating settings.
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
    fun createPersistentNotification(
        @StringRes titleResId: Int,
        message: String?,
        @DrawableRes iconResId: Int,
        actions: List<Pair<Int, Intent>>,
    ): Notification {
        val notificationIntent = Intent(context, SettingsActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        )

        return Notification.Builder(context, CHANNEL_ID_PERSISTENT).run {
            setContentTitle(context.getText(titleResId))
            if (message != null) {
                setContentText(message)
                style = Notification.BigTextStyle().bigText(message)
            }
            setSmallIcon(iconResId)
            setContentIntent(pendingIntent)
            setOngoing(true)
            setOnlyAlertOnce(true)

            for ((actionTextResId, actionIntent) in actions) {
                val actionPendingIntent = PendingIntent.getService(
                    context,
                    0,
                    actionIntent,
                    PendingIntent.FLAG_IMMUTABLE or
                            PendingIntent.FLAG_UPDATE_CURRENT or
                            PendingIntent.FLAG_ONE_SHOT,
                )

                addAction(Notification.Action.Builder(
                    null,
                    context.getString(actionTextResId),
                    actionPendingIntent,
                ).build())
            }

            // Inhibit 10-second delay when showing persistent notification
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
            }
            build()
        }
    }

    /**
     * Send an alert notification with the given [title] and [icon].
     *
     * * If [errorMsg] is not null, then it is appended to the text with a black line before it.
     * * If [file] is not null, the human-readable URI path is appended to the text with a blank
     *   line before it if needed. In addition, three actions, open/share/delete, are added to the
     *   notification. The delete action dismisses the notification, but open and share do not.
     *   Clicking on the notification itself will behave like the open action, except the
     *   notification will be dismissed.
     */
    private fun sendAlertNotification(
        channel: String,
        @StringRes title: Int,
        @DrawableRes icon: Int,
        errorMsg: String?,
        file: OutputFile?,
        additionalFiles: List<OutputFile>,
    ) {
        val notificationId = prefs.nextNotificationId

        val notification = Notification.Builder(context, channel).run {
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
                        // The data is not used for ACTION_SEND, but it makes Intent.filterEquals()
                        // return false and prevents the same PendingIntent being used when multiple
                        // notifications are shown.
                        setDataAndType(wrappedUri, file.mimeType)
                        putExtra(Intent.EXTRA_STREAM, wrappedUri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    },
                    PendingIntent.FLAG_IMMUTABLE,
                )
                val deleteIntent = PendingIntent.getService(
                    context,
                    0,
                    NotificationActionService.createDeleteUriIntent(
                        context,
                        listOf(file) + additionalFiles,
                        notificationId,
                    ),
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

                addAction(Notification.Action.Builder(
                    null,
                    context.getString(R.string.notification_action_delete),
                    deleteIntent,
                ).build())

                // Clicking on the notification behaves like the open action, except the
                // notification gets dismissed. The open and share actions do not dismiss the
                // notification.
                setContentIntent(openIntent)
                setAutoCancel(true)
            }

            build()
        }

        notificationManager.notify(notificationId, notification)
    }

    /**
     * Send a success alert notification.
     *
     * This will explicitly vibrate the device if the user enabled vibration for
     * [CHANNEL_ID_SUCCESS]. This is necessary because Android itself will not vibrate for a
     * notification during a phone call.
     */
    fun notifySuccess(
        @StringRes title: Int,
        @DrawableRes icon: Int,
        file: OutputFile,
        additionalFiles: List<OutputFile>,
    ) {
        sendAlertNotification(CHANNEL_ID_SUCCESS, title, icon, null, file, additionalFiles)
        vibrateIfEnabled(CHANNEL_ID_SUCCESS)
    }

    /**
     * Send a failure alert notification.
     *
     * This will explicitly vibrate the device if the user enabled vibration for
     * [CHANNEL_ID_FAILURE]. This is necessary because Android itself will not vibrate for a
     * notification during a phone call.
     */
    fun notifyFailure(
        @StringRes title: Int,
        @DrawableRes icon: Int,
        errorMsg: String?,
        file: OutputFile?,
        additionalFiles: List<OutputFile>,
    ) {
        sendAlertNotification(CHANNEL_ID_FAILURE, title, icon, errorMsg, file, additionalFiles)
        vibrateIfEnabled(CHANNEL_ID_FAILURE)
    }

    /** Dismiss all alert (non-persistent) notifications. */
    fun dismissAll() {
        for (notification in notificationManager.activeNotifications) {
            if (notification.isClearable) {
                notificationManager.cancel(notification.tag, notification.id)
            }
        }
    }

    /**
     * Explicitly vibrate device if the user enabled vibration for the [channelId] channel.
     *
     * If the notification channel has a specific vibration pattern associated with it, that
     * vibration pattern will be used. Otherwise, this function tries to mimic the system vibration
     * pattern as much as possible. The system default vibration pattern is queried from Android's
     * internal resources and will fall back to a hardcoded default (same as AOSP) if the query
     * fails.
     *
     * This function does not try to play PWLE waveforms. The API did not end up stabilizing in the
     * Android 13 release and it's not worth the effort to use reflection when no devices support it
     * yet.
     *
     * Ideally, using NotificationRecord.getVibration() would be best for ensuring the vibration
     * pattern is identical to what the system would generate, but that class is not available in
     * regular apps' classpath.
     */
    fun vibrateIfEnabled(channelId: String) {
        val channel = notificationManager.getNotificationChannel(channelId)
        if (channel.shouldVibrate()) {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(VibratorManager::class.java)
                vibratorManager.defaultVibrator
            } else {
                context.getSystemService(Vibrator::class.java)
            }

            if (vibrator.hasVibrator()) {
                val pattern = channel.vibrationPattern ?: defaultPattern
                val effect = VibrationEffect.createWaveform(pattern, -1)

                vibrator.vibrate(effect)
            }
        }
    }
}
