/*
 * SPDX-FileCopyrightText: 2023-2024 Andrew Gunnerson
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.chiller3.bcr.extension

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.core.net.toFile
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.IOException

private const val TAG = "DocumentFileExtensions"

/** Get the internal [Context] for a DocumentsProvider-backed file. */
private val DocumentFile.context: Context?
    get() = when (uri.scheme) {
        ContentResolver.SCHEME_CONTENT -> {
            javaClass.getDeclaredField("mContext").apply {
                isAccessible = true
            }.get(this) as Context
        }
        else -> null
    }

private val DocumentFile.isTree: Boolean
    get() = uri.scheme == ContentResolver.SCHEME_CONTENT && DocumentsContract.isTreeUri(uri)

private val DocumentFile.isLocal: Boolean
    get() = uri.scheme == ContentResolver.SCHEME_FILE ||
            (uri.scheme == ContentResolver.SCHEME_CONTENT && uri.authority == DOCUMENTSUI_AUTHORITY)

private fun <R> DocumentFile.withChildrenWithColumns(
    columns: Array<String>,
    block: (Cursor, Sequence<Pair<DocumentFile, Cursor>>) -> R,
): R {
    require(isTree) { "Not a tree URI" }

    // These reflection calls access private fields, but everything is part of the
    // androidx.documentfile:documentfile dependency and we control the version of that.
    val constructor = javaClass.getDeclaredConstructor(
        DocumentFile::class.java,
        Context::class.java,
        Uri::class.java,
    ).apply {
        isAccessible = true
    }

    val cursor = context!!.contentResolver.query(
        DocumentsContract.buildChildDocumentsUriUsingTree(
            uri,
            DocumentsContract.getDocumentId(uri),
        ),
        columns + arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID),
        null, null, null,
    ) ?: throw IOException("Query returned null cursor: $uri: $columns")

    return cursor.use {
        val indexDocumentId =
            cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)

        block(cursor, cursor.asSequence().map {
            val documentId = it.getString(indexDocumentId)
            val child: DocumentFile = constructor.newInstance(
                this,
                context,
                DocumentsContract.buildDocumentUriUsingTree(uri, documentId),
            )

            Pair(child, it)
        })
    }
}

/**
 * List files along with their display names, but faster for tree URIs.
 *
 * For non-tree URIs, this is equivalent to calling [DocumentFile.listFiles], followed by
 * [DocumentFile.getName] for each entry. For tree URIs, this only performs a single query to the
 * document provider.
 */
fun DocumentFile.listFilesWithNames(): List<Pair<DocumentFile, String>> {
    if (!isTree) {
        return listFiles().map { Pair(it, it.name!!) }
    }

    return try {
        withChildrenWithColumns(arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME)) { c, sequence ->
            val indexDisplayName =
                c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)

            sequence.map { it.first to it.second.getString(indexDisplayName) }.toList()
        }
    } catch (e: Exception) {
        Log.w(TAG, "Failed to query tree URI", e)
        emptyList()
    }
}

/**
 * Recursively list files along with their paths.
 *
 * Uses [listFilesWithNames] for faster iteration with tree URIs.
 */
fun DocumentFile.listFilesWithPathsRecursively(): List<Pair<DocumentFile, List<String>>> {
    val result = mutableListOf<Pair<DocumentFile, List<String>>>()

    fun recurse(dir: DocumentFile, path: List<String>) {
        for ((file, name) in dir.listFilesWithNames()) {
            val subPath = path + name

            result.add(Pair(file, subPath))

            if (file.isDirectory) {
                recurse(file, subPath)
            }
        }
    }

    recurse(this, emptyList())
    return result
}

/**
 * Like [DocumentFile.findFile], but faster for tree URIs.
 *
 * [DocumentFile.findFile] performs a query for the document ID list and then performs separate
 * queries for each document to get the name. This is extremely slow on some devices and is
 * unnecessary because [DocumentsContract.Document.COLUMN_DOCUMENT_ID] and
 * [DocumentsContract.Document.COLUMN_DISPLAY_NAME] can be queried at the same time.
 */
fun DocumentFile.findFileFast(displayName: String): DocumentFile? {
    if (!isTree) {
        return findFile(displayName)
    }

    return try {
        withChildrenWithColumns(arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME)) { c, sequence ->
            val indexDisplayName =
                c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)

            sequence.find { it.second.getString(indexDisplayName) == displayName }?.first
        }
    } catch (e: Exception) {
        Log.w(TAG, "Failed to query tree URI", e)
        null
    }
}

/** Like [DocumentFile.findFileFast], but accepts nested paths. */
fun DocumentFile.findNestedFile(path: List<String>): DocumentFile? {
    var file = this
    for (segment in path) {
        file = file.findFileFast(segment) ?: return null
    }
    return file
}

/**
 * Find the subdirectory [path] or create it if it doesn't already exist, including any intermediate
 * subdirectories.
 */
fun DocumentFile.findOrCreateDirectories(path: List<String>): DocumentFile? {
    var file = this

    // The root may not necessarily exist if it's a regular filesystem path.
    if (uri.scheme == ContentResolver.SCHEME_FILE) {
        uri.toFile().mkdirs()
    }

    for (segment in path) {
        file = file.findFileFast(segment)
            ?: file.createDirectory(segment)
            ?: return null
    }

    return file
}

/**
 * Like [DocumentFile.createFile], but explicitly appends file extensions for certain MIME types
 * that are not supported in older versions of Android. This special handling is only applied for
 * local files.
 */
fun DocumentFile.createFileCompat(mimeType: String, displayName: String): DocumentFile? {
    val finalDisplayName = if (isLocal) {
        buildString {
            append(displayName)

            val mimeTypeMap = MimeTypeMap.getSingleton()

            if (!mimeTypeMap.hasMimeType(mimeType)) {
                val ext = mimeTypeMap.getExtensionFromMimeTypeCompat(mimeType)
                if (ext != null) {
                    append('.')
                    append(ext)
                }
            }
        }
    } else {
        displayName
    }

    return createFile(mimeType, finalDisplayName)
}

/**
 * Like [DocumentFile.createFile], but accepts a nested path and automatically creates intermediate
 * subdirectories.
 *
 * @param path Path to the file, which must be non-empty. The last element specifies the filename
 * and should not include a file extension.
 * @param mimeType MIME type of the file, which determines the file extension.
 */
fun DocumentFile.createNestedFile(mimeType: String, path: List<String>): DocumentFile? {
    require(path.isNotEmpty()) { "Path cannot be empty" }

    return findOrCreateDirectories(path.dropLast(1))
        ?.createFileCompat(mimeType, path.last())
}

/** Like [DocumentFile.renameTo], but preserves the file extension. */
fun DocumentFile.renameToPreserveExt(displayName: String): Boolean {
    val newName = buildString {
        append(displayName)

        // This intentionally just does simple string operations because MimeTypeMap's
        // getExtensionFromMimeType() and getMimeTypeFromExtension() are not consistent with
        // each other. Eg. audio/mp4 -> m4a -> audio/mpeg -> mp3.

        val ext = name!!.substringAfterLast('.', "")
        if (ext.isNotEmpty()) {
            append('.')
            append(ext)
        }
    }

    return renameTo(newName)
}

/** Get the flags for a DocumentsProvider-backed file. */
private val DocumentFile.flags: Int
    get() {
        require(uri.scheme == ContentResolver.SCHEME_CONTENT) {
            throw IllegalArgumentException("Not a DocumentsProvider URI")
        }

        context!!.contentResolver.query(
            uri,
            arrayOf(DocumentsContract.Document.COLUMN_FLAGS),
            null, null, null,
        )?.use {
            if (it.moveToFirst()) {
                return it.getInt(0)
            }
        }

        return 0
    }

class NotEfficientlyMovableException(msg: String, cause: Throwable? = null)
    : IllegalArgumentException(msg, cause)

/**
 * Try to efficiently move the file to the [targetParent] directory.
 *
 * A file can be efficiently (without copy + delete) moved if:
 * * the source and destination are the same type (file:// or DocumentsProvider authority)
 * * the source and destination must be on the same mount point (for file://)
 * * the source file has a known parent (via [DocumentFile.getParentFile])
 * * the source file advertises [DocumentsContract.Document.FLAG_SUPPORTS_MOVE]
 *
 * After moving, the filename remains the same. What happens when [targetParent] already has a file
 * with the same name is unspecified because the [DocumentsContract] API provides no guarantees with
 * regards to this scenario.
 */
fun DocumentFile.moveToDirectory(targetParent: DocumentFile): DocumentFile? {
    if (uri.scheme != targetParent.uri.scheme) {
        throw NotEfficientlyMovableException("Source scheme (${uri.scheme}) != " +
                "target parent scheme (${targetParent.uri.scheme})")
    }

    when (uri.scheme) {
        ContentResolver.SCHEME_FILE -> {
            val sourceFile = uri.toFile()
            val targetFile = File(targetParent.uri.toFile(), sourceFile.name)

            return if (sourceFile.absolutePath == targetFile.absolutePath) {
                this
            } else if (sourceFile.renameTo(targetFile)) {
                DocumentFile.fromFile(targetFile)
            } else {
                null
            }
        }
        ContentResolver.SCHEME_CONTENT -> {
            if (uri.authority != targetParent.uri.authority) {
                throw NotEfficientlyMovableException("Source authority (${uri.authority}) != " +
                        "target parent authority (${targetParent.uri.authority})")
            } else if (flags and DocumentsContract.Document.FLAG_SUPPORTS_MOVE == 0) {
                throw NotEfficientlyMovableException("File does not advertise move flag")
            } else if (parentFile == null) {
                throw NotEfficientlyMovableException("File does not have known parent")
            }

            if (parentFile!!.uri == targetParent.uri) {
                return this
            }

            return try {
                val targetUri = DocumentsContract.moveDocument(
                    context!!.contentResolver,
                    uri,
                    parentFile!!.uri,
                    targetParent.uri,
                )

                targetUri?.let { DocumentFile.fromTreeUri(context!!, targetUri) }
            } catch (e: Exception) {
                null
            }
        }
        else -> throw IllegalArgumentException("Unsupported scheme: ${uri.scheme}")
    }
}

private fun DocumentFile.isEmpty(): Boolean {
    require(isDirectory) { "Not a directory" }

    return if (isTree) {
        withChildrenWithColumns(emptyArray()) { _, sequence ->
            sequence.none()
        }
    } else {
        listFiles().isEmpty()
    }
}

/**
 * Delete this document if it refers to an empty directory.
 *
 * This is subject to TOCTTOU issues because SAF does not have a non-recursive delete function.
 */
fun DocumentFile.deleteIfEmptyDir(): Boolean {
    if (isDirectory && isEmpty()) {
        return delete()
    }

    return false
}

fun DocumentFile.deleteIfEmptyDirRecursively() {
    var current: DocumentFile? = this

    while (current != null) {
        if (!current.deleteIfEmptyDir()) {
            return
        }
        current = current.parentFile
    }
}
