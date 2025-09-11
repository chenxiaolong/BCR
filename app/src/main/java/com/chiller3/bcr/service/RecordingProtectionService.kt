/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.chiller3.bcr.service

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.FileObserver
import android.os.IBinder
import android.util.Log
import androidx.core.net.toFile
import androidx.documentfile.provider.DocumentFile
import com.chiller3.bcr.Notifications
import com.chiller3.bcr.Preferences
import com.chiller3.bcr.util.RootShell
import java.io.File

class RecordingProtectionService : Service() {
    private lateinit var prefs: Preferences
    private var observer: FileObserver? = null

    override fun onCreate() {
        super.onCreate()
        prefs = Preferences(this)
        startForeground(NOTIFICATION_ID, buildNotification())
        applyPermissionsToExisting()
        startWatching()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        observer?.stopWatching()
        observer = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification =
        Notifications(this).createPersistentNotification(
            com.chiller3.bcr.R.string.notification_protection_active,
            null,
            emptyList(),
        )

    private fun getUserOutputDirFile(): File? {
        val uri: Uri = prefs.outputDirOrDefault
        return try {
            if ("file" == uri.scheme) {
                uri.toFile()
            } else {
                // Best effort: resolve to raw path via DocumentFile tree (limited)
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to resolve output dir to file", e)
            null
        }
    }

    private fun applyPermissionsToExisting() {
        if (!RootShell.isRootAvailable()) {
            Log.w(TAG, "Root not available; cannot protect recordings")
            stopSelf()
            return
        }
        val dir = getUserOutputDirFile() ?: return
        // Recursively make files immutable: chmod 444 and chattr +i if available
        val cmds = arrayListOf<String>()
        cmds += "find \"${dir.absolutePath}\" -type f -print0 | xargs -0 -r chmod 0444"
        cmds += "command -v chattr >/dev/null 2>&1 && find \"${dir.absolutePath}\" -type f -print0 | xargs -0 -r chattr +i || true"
        RootShell.runCommands(*cmds.toTypedArray())
    }

    private fun protectFile(path: String) {
        if (!RootShell.isRootAvailable()) return
        RootShell.runCommands(
            "chmod 0444 \"$path\"",
            "command -v chattr >/dev/null 2>&1 && chattr +i \"$path\" || true"
        )
    }

    private fun startWatching() {
        val dir = getUserOutputDirFile() ?: return
        observer = object : FileObserver(dir.absolutePath, CREATE or MOVED_TO) {
            override fun onEvent(event: Int, path: String?) {
                if (path.isNullOrEmpty()) return
                val full = File(dir, path)
                if (full.isFile) {
                    protectFile(full.absolutePath)
                }
            }
        }
        observer?.startWatching()
    }

    companion object {
        private val TAG = RecordingProtectionService::class.java.simpleName
        private const val NOTIFICATION_ID = 11001
    }
}


