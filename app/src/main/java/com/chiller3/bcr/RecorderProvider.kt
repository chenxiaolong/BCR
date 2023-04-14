package com.chiller3.bcr

import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor

/**
 * This is an extremely minimal content provider so that BCR can provide an openable/shareable URI
 * to other applications. SAF URIs cannot be shared directly because permission grants cannot be
 * propagated to the target app.
 *
 * This content provider is not exported and access is only granted to specific URIs via
 * [Intent.FLAG_GRANT_READ_URI_PERMISSION].
 */
class RecorderProvider : ContentProvider() {
    companion object {
        private const val QUERY_ORIG = "orig"

        fun fromOrigUri(origUri: Uri): Uri =
            Uri.Builder().run {
                scheme(ContentResolver.SCHEME_CONTENT)
                authority(BuildConfig.PROVIDER_AUTHORITY)
                appendQueryParameter(QUERY_ORIG, origUri.toString())

                build()
            }

        private fun extractOrigUri(uri: Uri): Uri? {
            val param = uri.getQueryParameter(QUERY_ORIG)
            if (param.isNullOrBlank()) {
                return null
            }

            return try {
                Uri.parse(param)
            } catch (e: Exception) {
                null
            }
        }
    }

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? =
        extractOrigUri(uri)?.let {
            context?.contentResolver?.openFileDescriptor(it, mode)
        }

    override fun getType(uri: Uri): String? =
        extractOrigUri(uri)?.let {
            context?.contentResolver?.getType(it)
        }

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Cursor? =
        extractOrigUri(uri)?.let {
            context?.contentResolver?.query(
                it, projection, selection, selectionArgs, sortOrder)
        }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<String>?,
    ): Int = 0
}
