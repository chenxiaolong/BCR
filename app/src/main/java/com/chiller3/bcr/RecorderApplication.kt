package com.chiller3.bcr

import android.app.Application
import android.util.Log
import androidx.core.net.toFile
import com.google.android.material.color.DynamicColors

class RecorderApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        val props = Preferences(this)
        props.migrateInitiallyPaused()
        // Migrate the old properties file. This is blocking, but oh well. We can remove the
        // migration logic after a few more releases.
        props.migrateLegacyProperties()

        // Enable Material You colors
        DynamicColors.applyToActivitiesIfAvailable(this)

        Notifications(this).updateChannels()

        val oldCrashHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                val redactor = OutputDirUtils.NULL_REDACTOR
                val dirUtils = OutputDirUtils(this, redactor)
                val logcatFile = dirUtils.createFileInDefaultDir("crash.log", "text/plain")

                Log.e(TAG, "Saving logcat to ${redactor.redact(logcatFile.uri)} due to uncaught exception in $t", e)

                try {
                    ProcessBuilder("logcat", "-d", "*:V")
                        .redirectOutput(logcatFile.uri.toFile())
                        .redirectErrorStream(true)
                        .start()
                        .waitFor()
                } finally {
                    dirUtils.tryMoveToUserDir(logcatFile)
                }
            } finally {
                oldCrashHandler?.uncaughtException(t, e)
            }
        }
    }

    companion object {
        private val TAG = RecorderApplication::class.java.simpleName
    }
}
