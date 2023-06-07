package com.chiller3.bcr.output

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.system.Int64Ref
import android.system.Os
import android.system.OsConstants
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.chiller3.bcr.Preferences
import com.chiller3.bcr.extension.NotEfficientlyMovableException
import com.chiller3.bcr.extension.createNestedFile
import com.chiller3.bcr.extension.deleteIfEmptyDirRecursively
import com.chiller3.bcr.extension.findNestedFile
import com.chiller3.bcr.extension.findOrCreateDirectories
import com.chiller3.bcr.extension.moveToDirectory
import com.chiller3.bcr.extension.renameToPreserveExt
import java.io.FileNotFoundException
import java.io.IOException

class OutputDirUtils(private val context: Context, private val redactor: Redactor) {
    private val prefs = Preferences(context)

    private fun getErrorFallbackPath(path: List<String>) =
        listOf("ERROR_${path.joinToString("_")}")

    private fun getExistingPath(root: DocumentFile, path: List<String>): DocumentFile {
        return root.findNestedFile(path)
            ?: throw FileNotFoundException("Failed to find " + redactor.redact(path) + " in " +
                    redactor.redact(root.uri))
    }

    private fun getOrCreateDirectory(root: DocumentFile, path: List<String>): DocumentFile {
        return root.findOrCreateDirectories(path)
            ?: throw IOException("Failed to find or create " + redactor.redact(path) + " in " +
                    redactor.redact(root.uri))
    }

    private fun createFile(root: DocumentFile, path: List<String>, mimeType: String): DocumentFile {
        require(path.isNotEmpty()) { "Path cannot be empty" }

        val redactedPath = redactor.redact(path)
        val redactedRoot = redactor.redact(root.uri)
        Log.d(TAG, "Creating $redactedPath with MIME type $mimeType in $redactedRoot")

        return root.createNestedFile(mimeType, path)
            ?: throw IOException("Failed to create file $redactedPath in $redactedRoot")
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

    /**
     * Move [sourceFile] to [targetFile] via copy + delete.
     *
     * Both files must have already been created.
     */
    private fun copyAndDelete(sourceFile: DocumentFile, targetFile: DocumentFile) {
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
        } catch (e: Exception) {
            targetFile.delete()
            throw e
        }
    }

    /**
     * Move [sourceTree]/[sourcePath] to [targetTree]/[targetPath].
     *
     * If conditions allow for it, the move will be done efficiently (eg. rename() for file URIs and
     * moveDocument() for DocumentsProvider URIs). Otherwise, the move is done by copying and then
     * deleting the source file.
     *
     * @return The [DocumentFile] for the newly moved file.
     */
    private fun move(
        sourceTree: DocumentFile,
        sourcePath: List<String>,
        targetTree: DocumentFile,
        targetPath: List<String>,
    ): DocumentFile {
        require(targetPath.isNotEmpty()) { "Target path must not be empty" }

        val sourceFile = getExistingPath(sourceTree, sourcePath)
        val targetParentPath = targetPath.dropLast(1)
        val targetParent = getOrCreateDirectory(targetTree, targetParentPath)

        // Try to move efficiently if possible
        try {
            val targetFile = sourceFile.moveToDirectory(targetParent)
            if (targetFile != null) {
                val oldFilename = targetFile.name!!.substringBeforeLast('.')
                val newFilename = targetPath.last()

                if (oldFilename != newFilename && !targetFile.renameToPreserveExt(newFilename)) {
                    // We intentionally don't report this error so that the user can be shown the
                    // valid, but incorrectly named, target file instead of the now non-existent
                    // source file.
                    Log.w(TAG, "Failed to rename target file from " +
                            redactor.redact(oldFilename) + " to "  + redactor.redact(newFilename))
                }

                return targetFile
            } else {
                Log.w(TAG, "Failed to efficiently move ${redactor.redact(sourceFile.uri)} to " +
                        redactor.redact(targetParent.uri))
            }
        } catch (e: NotEfficientlyMovableException) {
            // Intentionally omitting stack trace
            Log.w(TAG, "${redactor.redact(sourceFile.uri)} cannot be efficiently moved to " +
                    "${redactor.redact(targetParent.uri)}: ${e.message}")
        }

        val targetFile = createFile(targetTree, targetPath, sourceFile.type!!)
        copyAndDelete(sourceFile, targetFile)
        return targetFile
    }

    /**
     * Create [path] in the default output directory.
     *
     * @param path The last element is the filename, which should not contain a file extension
     * @param mimeType Determines the file extension
     *
     * @throws IOException if the file could not be created in the default directory
     */
    fun createFileInDefaultDir(path: List<String>, mimeType: String): DocumentFile {
        val defaultDir = DocumentFile.fromFile(prefs.defaultOutputDir)

        return try {
            createFile(defaultDir, path, mimeType)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to create file; using fallback path", e)

            // If the failure is due to eg. running out of space, then there's nothing we can do and
            // this will just fail the same way again. However, if the failure is due to eg. an
            // intermediate path segment being a file instead of a directory, then at least the user
            // will still have the recording, even if the directory structure is wrong.
            createFile(defaultDir, getErrorFallbackPath(path), mimeType)
        }
    }

    /**
     * Try to move [sourceFile] to the user output directory at [path].
     *
     * @return Whether the user output directory is set and the file was successfully moved
     */
    fun tryMoveToOutputDir(sourceFile: DocumentFile, path: List<String>): DocumentFile? {
        val userDir = prefs.outputDir?.let {
            // Only returns null on API <21
            DocumentFile.fromTreeUri(context, it)!!
        } ?: DocumentFile.fromFile(prefs.defaultOutputDir)

        val redactedSource = redactor.redact(sourceFile.uri)

        return try {
            val targetFile = try {
                move(sourceFile, emptyList(), userDir, path)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to move file; using fallback path", e)
                move(sourceFile, emptyList(), userDir, getErrorFallbackPath(path))
            }
            val redactedTarget = redactor.redact(targetFile.uri)

            Log.i(TAG, "Successfully moved $redactedSource to $redactedTarget")

            try {
                sourceFile.parentFile?.deleteIfEmptyDirRecursively()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to clean up empty source directories", e)
            }

            targetFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to move $redactedSource to $userDir", e)
            null
        }
    }

    companion object {
        private val TAG = OutputDirUtils::class.java.simpleName

        val NULL_REDACTOR = object : Redactor {
            override fun redact(msg: String): String = msg
        }
    }

    interface Redactor {
        fun redact(msg: String): String

        fun redact(uri: Uri): String = redact(Uri.decode(uri.toString()))

        fun redact(path: List<String>): String = redact(path.joinToString("/"))
    }
}
