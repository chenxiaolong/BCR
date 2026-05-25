/*
 * SPDX-FileCopyrightText: 2022-2026 Andrew Gunnerson
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.chiller3.bcr

import android.app.Application
import android.util.Log
import androidx.core.net.toFile
import com.chiller3.bcr.output.OutputDirUtils

class RecorderApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        Logcat.init(this)

        val oldCrashHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                val redactor = OutputDirUtils.NULL_REDACTOR
                val dirUtils = OutputDirUtils(this, redactor)
                val logcatPath = listOf(Logcat.FILENAME_CRASH)
                val logcatFile = dirUtils.createFileInDefaultDir(logcatPath, "text/plain")

                Log.e(TAG, "Saving logcat to ${redactor.redact(logcatFile.uri)} due to uncaught exception in $t", e)

                try {
                    Logcat.dump(logcatFile.uri.toFile())
                } finally {
                    try {
                        dirUtils.moveToOutputDir(logcatFile, logcatPath, "text/plain")
                    } catch (_: Exception) {
                        // Ignore.
                    }
                }
            } finally {
                oldCrashHandler?.uncaughtException(t, e)
            }
        }

        Notifications(this).updateChannels()

        val prefs = Preferences(this)
        prefs.migrateTemplate()
        prefs.migrateAudioSource()
        prefs.migrateRecordRules()
    }

    companion object {
        private val TAG = RecorderApplication::class.java.simpleName
    }
}
