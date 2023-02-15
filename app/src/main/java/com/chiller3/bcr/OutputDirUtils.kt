package com.chiller3.bcr

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.system.Int64Ref
import android.system.Os
import android.system.OsConstants
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import java.io.IOException

class OutputDirUtils(private val context: Context, private val redactor: Redactor) {
    private val prefs = Preferences(context)

    /**
     * Try to move [sourceFile] to the user output directory.
     *
     * @return Whether the user output directory is set and the file was successfully moved
     */
    fun tryMoveToUserDir(sourceFile: DocumentFile): DocumentFile? {
        val userDir = prefs.outputDir?.let {
            // Only returns null on API <21
            DocumentFile.fromTreeUri(context, it)!!
        } ?: return null

        val redactedSource = redactor.redact(sourceFile.uri)

        return try {
            val targetFile = moveFileToDir(sourceFile, userDir)
            val redactedTarget = redactor.redact(targetFile.uri)

            Log.i(TAG, "Successfully moved $redactedSource to $redactedTarget")
            sourceFile.delete()

            targetFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to move $redactedSource to $userDir", e)
            null
        }
    }

    /**
     * Move [sourceFile] to [targetDir].
     *
     * @return The [DocumentFile] for the newly moved file.
     */
    private fun moveFileToDir(sourceFile: DocumentFile, targetDir: DocumentFile): DocumentFile {
        val targetFile = createFileInDir(targetDir, sourceFile.name!!, sourceFile.type!!)

        try {
            openFile(sourceFile, false).use { sourcePfd ->
                openFile(targetFile, true).use { targetPfd ->
                    var remain = Os.lseek(sourcePfd.fileDescriptor, 0, OsConstants.SEEK_END)
                    val offset = Int64Ref(0)

                    while (remain > 0) {
                        val ret = Os.sendfile(
                            targetPfd.fileDescriptor, sourcePfd.fileDescriptor, offset, remain)
                        if (ret == 0L) {
                            throw IOException("Unexpected EOF in sendfile()")
                        }

                        remain -= ret
                    }

                    Os.fsync(targetPfd.fileDescriptor)
                }
            }

            sourceFile.delete()
            return targetFile
        } catch (e: Exception) {
            targetFile.delete()
            throw e
        }
    }

    /**
     * Create [name] in the default output directory.
     *
     * @param name Should not contain a file extension
     * @param mimeType Determines the file extension
     *
     * @throws IOException if the file could not be created in the default directory
     */
    fun createFileInDefaultDir(name: String, mimeType: String): DocumentFile {
        val defaultDir = DocumentFile.fromFile(prefs.defaultOutputDir)
        return createFileInDir(defaultDir, name, mimeType)
    }

    /**
     * Create a new file with name [name] inside [dir].
     *
     * @param name Should not contain a file extension
     * @param mimeType Determines the file extension
     *
     * @throws IOException if file creation fails
     */
    private fun createFileInDir(dir: DocumentFile, name: String, mimeType: String): DocumentFile {
        Log.d(TAG, "Creating ${redactor.redact(name)} with MIME type $mimeType in ${dir.uri}")

        return dir.createFile(mimeType, name)
            ?: throw IOException("Failed to create file in ${dir.uri}")
    }

    /**
     * Open seekable file descriptor to [file].
     *
     * @throws IOException if [file] cannot be opened
     */
    fun openFile(file: DocumentFile, truncate: Boolean): ParcelFileDescriptor {
        val truncParam = if (truncate) { "t" } else { "" }
        return context.contentResolver.openFileDescriptor(file.uri, "rw$truncParam")
            ?: throw IOException("Failed to open file at ${file.uri}")
    }

    companion object {
        private val TAG = OutputDirUtils::class.java.simpleName

        val NULL_REDACTOR = object : Redactor {
            override fun redact(msg: String): String = msg

            override fun redact(uri: Uri): String = Uri.decode(uri.toString())
        }
    }

    interface Redactor {
        fun redact(msg: String): String

        fun redact(uri: Uri): String
    }
}