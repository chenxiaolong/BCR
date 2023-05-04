package com.chiller3.bcr.extension

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.documentfile.provider.DocumentFile

private const val TAG = "DocumentFileExtensions"

private fun DocumentFile.iterChildrenWithColumns(extraColumns: Array<String>) = iterator {
    if (!DocumentsContract.isTreeUri(uri)) {
        throw IllegalArgumentException("Not a tree URI")
    }

    val file = this@iterChildrenWithColumns

    // These reflection calls access private fields, but everything is part of the
    // androidx.documentfile:documentfile dependency and we control the version of that.

    val context = file.javaClass.getDeclaredField("mContext").apply {
        isAccessible = true
    }.get(file) as Context

    val constructor = file.javaClass.getDeclaredConstructor(
        DocumentFile::class.java,
        Context::class.java,
        Uri::class.java,
    ).apply {
        isAccessible = true
    }

    context.contentResolver.query(
        DocumentsContract.buildChildDocumentsUriUsingTree(
            uri,
            DocumentsContract.getDocumentId(uri),
        ),
        arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID) + extraColumns,
        null, null, null,
    )?.use {
        while (it.moveToNext()) {
            val child: DocumentFile = constructor.newInstance(
                file,
                context,
                DocumentsContract.buildDocumentUriUsingTree(uri, it.getString(0)),
            )

            yield(Pair(child, it))
        }
    }
}

/**
 * List files along with their display names, but faster for tree URIs.
 *
 * For non-tree URIs, this is equivalent to calling [DocumentFile.listFiles], followed by
 * [DocumentFile.getName] for each entry. For tree URIs, this only performs a single query to the
 * document provider.
 */
fun DocumentFile.listFilesWithNames(): List<Pair<DocumentFile, String?>> {
    if (!DocumentsContract.isTreeUri(uri)) {
        return listFiles().map { Pair(it, it.name) }
    }

    try {
        return iterChildrenWithColumns(arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME))
            .asSequence()
            .map { Pair(it.first, it.second.getString(1)) }
            .toList()
    } catch (e: Exception) {
        Log.w(TAG, "Failed to query tree URI", e)
    }

    return listOf()
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
    if (!DocumentsContract.isTreeUri(uri)) {
        return findFile(displayName)
    }

    try {
        return iterChildrenWithColumns(arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME))
            .asSequence()
            .find { it.second.getString(1) == displayName }
            ?.first
    } catch (e: Exception) {
        Log.w(TAG, "Failed to query tree URI", e)
    }

    return null
}

/**
 * Like [DocumentFile.renameTo], but preserves the extension for file URIs.
 *
 * This fixes [DocumentFile.renameTo]'s behavior so it is the same for both SAF and file URIs.
 */
fun DocumentFile.renameToPreserveExt(displayName: String): Boolean {
    val newName = when (uri.scheme) {
        ContentResolver.SCHEME_FILE -> {
            buildString {
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
        }
        else -> displayName
    }

    return renameTo(newName)
}
