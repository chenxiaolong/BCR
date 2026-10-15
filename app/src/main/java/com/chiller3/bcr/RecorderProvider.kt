/*
 * SPDX-FileCopyrightText: 2022-2026 Andrew Gunnerson
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.chiller3.bcr

import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.net.toUri
import com.chiller3.bcr.extension.toDocumentFile

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

        private val DEFAULT_PROJECTION: Array<String> = arrayOf(
            OpenableColumns.DISPLAY_NAME,
            OpenableColumns.SIZE,
            MediaStore.MediaColumns.DATE_MODIFIED,
        )

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
                param.toUri()
            } catch (_: Exception) {
                null
            }
        }

        private fun getProjection(projection: Array<String>?): Array<String> =
            if (projection.isNullOrEmpty()) {
                DEFAULT_PROJECTION
            } else {
                projection
            }
    }

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? =
        extractOrigUri(uri)?.let { origUri ->
            // Android already checks the caller's permissions, but we'll force a read-only mode
            // just to be safe.
            context!!.contentResolver.openFileDescriptor(origUri, "r")
        }

    override fun getType(uri: Uri): String? =
        extractOrigUri(uri)?.let { origUri ->
            val file = origUri.toDocumentFile(context!!)

            file.type
        }

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Cursor? =
        extractOrigUri(uri)?.let { origUri ->
            /*
                This intentionally returns OpenableColumns data because OneDrive assumes that the
                shared file comes from a FileProvider and crashes if it can't obtain the filename.

                FATAL EXCEPTION: main
                Process: com.microsoft.skydrive, PID: 24408
                java.lang.UnsupportedOperationException: Empty file name is not supported for uploading.
                      at com.microsoft.skydrive.upload.UploadDataModel.uploadFiles(SourceFile:90)
                      at com.microsoft.skydrive.upload.UploadDataModel.uploadFiles(SourceFile:3)
                      at com.microsoft.skydrive.intent.actionsend.ReceiveActionSendActivity.onOptionsItemSelected(SourceFile:223)
                      at android.app.Activity.onMenuItemSelected(Activity.java:4830)
                      at e.e0.onMenuItemSelected(SourceFile:5)
                      at androidx.fragment.app.h1.onMenuItemSelected(SourceFile:0)
                      at l.s.onMenuItemSelected(SourceFile:0)
                      at l.t0.onMenuItemSelected(SourceFile:2)
                      at ff.b.a(SourceFile:52)
                      at q.q.e(SourceFile:4)
                      at q.q.q(SourceFile:28)
                      at androidx.appcompat.widget.ActionMenuView.c(SourceFile:4)
                      at androidx.appcompat.view.menu.ActionMenuItemView.onClick(SourceFile:6)
                      at android.view.View.performClick(View.java:8241)
                      at android.view.View.performClickInternal(View.java:8218)
                      at android.view.View.-$$Nest$mperformClickInternal(View.java:0)
                      at android.view.View$PerformClick.run(View.java:32182)
                      at android.os.Handler.handleCallback(Handler.java:1095)
                      at android.os.Handler.dispatchMessageImpl(Handler.java:135)
                      at android.os.Handler.dispatchMessage(Handler.java:125)
                      at android.os.Looper.loopOnce(Looper.java:296)
                      at android.os.Looper.loop(Looper.java:397)
                      at android.app.ActivityThread.main(ActivityThread.java:9569)
                      at java.lang.reflect.Method.invoke(Native Method)
                      at com.android.internal.os.RuntimeInit$MethodAndArgsCaller.run(RuntimeInit.java:575)
                      at com.android.internal.os.ZygoteInit.main(ZygoteInit.java:975)
            */

            val projection = getProjection(projection)
            val file = origUri.toDocumentFile(context!!)

            val columns = ArrayList<String>(projection.size)
            val values = ArrayList<Any>(projection.size)

            for (column in projection) {
                when (column) {
                    OpenableColumns.DISPLAY_NAME -> {
                        file.name?.let {
                            columns.add(column)
                            values.add(it)
                        }
                    }
                    OpenableColumns.SIZE -> {
                        // DocumentFile can't signal errors here.
                        columns.add(column)
                        values.add(file.length())
                    }
                    MediaStore.MediaColumns.DATE_MODIFIED -> {
                        columns.add(column)
                        values.add(file.lastModified())
                    }
                }
            }

            MatrixCursor(columns.toTypedArray(), 1).apply {
                addRow(values.toTypedArray())
            }
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
