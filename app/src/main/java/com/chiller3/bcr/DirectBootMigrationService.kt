package com.chiller3.bcr

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import com.chiller3.bcr.format.Format
import com.chiller3.bcr.output.OutputDirUtils
import com.chiller3.bcr.output.OutputFile
import com.chiller3.bcr.output.OutputFilenameGenerator
import java.io.File

class DirectBootMigrationService : Service() {
    companion object {
        private val TAG = DirectBootMigrationService::class.java.simpleName

        private fun isKnownExtension(extension: String): Boolean {
            return extension == "log" || MimeTypeMap.getSingleton().hasExtension(extension)
        }

        private fun splitKnownExtension(name: String): Pair<String, String> {
            val dot = name.lastIndexOf('.')
            if (dot > 0) {
                val extension = name.substring(dot + 1)
                if (isKnownExtension(extension)) {
                    return name.substring(0, dot) to extension
                }
            }

            return name to ""
        }

        private data class MimeType(val isAudio: Boolean, val type: String)

        private val FALLBACK_MIME_TYPE = MimeType(false, "application/octet-stream")

        /**
         * Get the MIME type based on the extension if it is known.
         *
         * We do not use [MimeTypeMap.getMimeTypeFromExtension] because the mime type <-> extension
         * mapping is not 1:1. When showing notifications for moved files, we want to use the same
         * MIME type that we would have used for the initial file creation.
         */
        private fun mimeTypeForExtension(extension: String): MimeType? {
            val knownMimeTypes = sequence {
                yieldAll(Format.all.asSequence().map { MimeType(true, it.mimeTypeContainer) })
                yield(MimeType(false, RecorderThread.MIME_LOGCAT))
                yield(MimeType(false, RecorderThread.MIME_METADATA))
            }

            return knownMimeTypes.find {
                MimeTypeMap.getSingleton().getExtensionFromMimeType(it.type) == extension
            }
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var prefs: Preferences
    private lateinit var notifications: Notifications
    private lateinit var outputFilenameGenerator: OutputFilenameGenerator
    private val redactor = object : OutputDirUtils.Redactor {
        override fun redact(msg: String): String = OutputFilenameGenerator.redactTruncate(msg)
    }
    private lateinit var dirUtils: OutputDirUtils
    private var ranOnce = false
    private val thread = Thread {
        try {
            migrateFiles()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to migrate files", e)
            onFailure(e.localizedMessage)
        } finally {
            handler.post {
                tryStop()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        prefs = Preferences(this)
        notifications = Notifications(this)
        outputFilenameGenerator = OutputFilenameGenerator(this)
        dirUtils = OutputDirUtils(this, redactor)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!ranOnce) {
            ranOnce = true
            startThread()
        } else {
            tryStop()
        }

        return START_NOT_STICKY
    }

    private fun startThread() {
        Log.i(TAG, "Starting direct boot file migration")
        thread.start()
    }

    private fun tryStop() {
        if (!thread.isAlive) {
            Log.d(TAG, "Stopping service")
            stopSelf()
        }
    }

    private fun migrateFiles() {
        val sourceDir = prefs.directBootCompletedDir

        val filesToMove = sourceDir.walkTopDown().filter { it.isFile }.toList()
        Log.i(TAG, "${filesToMove.size} files to migrate")

        data class FileInfo(
            val file: File,
            val path: List<String>,
            val mime: MimeType,
        )

        // Group the files by prefix to form logical groups. If the group has an audio file, then
        // we'll show a notification similar to when a recording normally completes so that the user
        // can easily open, share, or delete the file.
        val byPrefix = mutableMapOf<String?, ArrayDeque<FileInfo>>()
        val ungrouped = ArrayDeque<FileInfo>()

        for (file in filesToMove) {
            // This is used for actual file creation with SAF.
            val (baseName, extension) = splitKnownExtension(file.name)
            val mimeType = mimeTypeForExtension(extension) ?: FALLBACK_MIME_TYPE

            // The name with all known extensions removed is only used for grouping.
            var prefixName = baseName
            while (true) {
                val (name, ext) = splitKnownExtension(prefixName)
                if (ext.isEmpty()) {
                    break
                } else {
                    prefixName = name
                }
            }

            val relParent = file.parentFile!!.relativeTo(sourceDir)
            val relBasePath = File(relParent, baseName)
            val prefix = File(relParent, prefixName)
            val group = byPrefix.getOrPut(prefix.toString()) { ArrayDeque() }
            val fileInfo = FileInfo(
                file,
                OutputFilenameGenerator.splitPath(relBasePath.toString()),
                mimeType,
            )

            if (mimeType.isAudio) {
                group.addFirst(fileInfo)
            } else {
                group.addLast(fileInfo)
            }
        }

        // Get rid of groups that have no audio.
        val byPrefixIterator = byPrefix.iterator()
        while (byPrefixIterator.hasNext()) {
            val (_, files) = byPrefixIterator.next()
            if (!files.first().mime.isAudio) {
                ungrouped.addAll(files)
                byPrefixIterator.remove()
            }
        }

        if (ungrouped.isNotEmpty()) {
            byPrefix[null] = ungrouped
        }

        var succeeded = 0
        var failed = 0

        for ((prefix, group) in byPrefix) {
            var notifySuccess = prefix != null
            val groupFiles = ArrayDeque<OutputFile>()

            for (fileInfo in group) {
                val newFile = dirUtils.tryMoveToOutputDir(
                    DocumentFile.fromFile(fileInfo.file),
                    fileInfo.path,
                    fileInfo.mime.type,
                )

                if (newFile != null) {
                    groupFiles.add(
                        OutputFile(
                            newFile.uri,
                            redactor.redact(newFile.uri),
                            fileInfo.mime.type,
                        )
                    )
                    succeeded += 1
                } else {
                    notifySuccess = false
                    failed += 1
                }
            }

            if (notifySuccess) {
                // This is not perfect, but it's good enough. A file may exist even though the
                // recording failed. In this scenario, the user would see the failure notification
                // from the recorder thread and a success notification from us moving the file.
                onSuccess(groupFiles.removeFirst(), groupFiles)
            }
        }

        if (failed != 0) {
            onFailure(getString(R.string.notification_direct_boot_migration_error))
        }

        Log.i(TAG, "$succeeded succeeded, $failed failed")
    }

    private fun onSuccess(file: OutputFile, additionalFiles: List<OutputFile>) {
        handler.post {
            notifications.notifyRecordingSuccess(file, additionalFiles)
        }
    }

    private fun onFailure(errorMsg: String?) {
        handler.post {
            notifications.notifyMigrationFailure(errorMsg)
        }
    }
}
