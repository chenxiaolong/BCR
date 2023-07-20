package com.chiller3.bcr.output

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Parcelable
import androidx.core.net.toFile
import androidx.documentfile.provider.DocumentFile
import kotlinx.parcelize.Parcelize

@Parcelize
data class OutputFile(
    /**
     * URI to a single file, which may have a [ContentResolver.SCHEME_FILE] or
     * [ContentResolver.SCHEME_CONTENT] scheme.
     */
    val uri: Uri,

    /** String representation of [uri] with private information redacted. */
    val redacted: String,

    /** MIME type of [uri]'s contents. */
    val mimeType: String,
) : Parcelable {
    fun toDocumentFile(context: Context): DocumentFile =
        when (uri.scheme) {
            ContentResolver.SCHEME_FILE -> DocumentFile.fromFile(uri.toFile())
            // Only returns null on API <19
            ContentResolver.SCHEME_CONTENT -> DocumentFile.fromSingleUri(context, uri)!!
            else -> throw IllegalArgumentException("Invalid URI scheme: $redacted")
        }
}
