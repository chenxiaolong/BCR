/*
 * SPDX-FileCopyrightText: 2023 Andrew Gunnerson
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.chiller3.bcr

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.net.toFile
import java.io.File
import java.io.IOException
import kotlin.io.path.inputStream

object Logcat {
    private val TAG = Logcat::class.java.simpleName

    const val FILENAME_DEFAULT = "logcat.log"
    const val FILENAME_CRASH = "crash.log"
    const val MIMETYPE = "text/plain"

    // We only need this for opening file descriptors. Configuration changes aren't relevant here.
    private lateinit var applicationContext: Context

    fun init(context: Context) {
        applicationContext = context.applicationContext
    }

    fun dump(file: File) {
        Log.d(TAG, "Dumping logs to $file")

        ProcessBuilder("logcat", "-d", "*:V")
            .redirectOutput(file)
            .redirectErrorStream(true)
            .start()
            .waitFor()
    }

    fun dump(uri: Uri) {
        if (uri.scheme == ContentResolver.SCHEME_FILE) {
            dump(uri.toFile())
            return
        }

        Log.d(TAG, "Dumping logs to $uri")

        withTempFile(applicationContext, FILENAME_DEFAULT) { tempFile ->
            dump(tempFile.toFile())

            Log.d(TAG, "Moving $tempFile to $uri")

            val out = applicationContext.contentResolver.openOutputStream(uri)
                ?: throw IOException("Failed to open URI: $uri")
            out.use { outStream ->
                tempFile.inputStream().use { inStream ->
                    inStream.copyTo(outStream)
                }
            }
        }
    }
}