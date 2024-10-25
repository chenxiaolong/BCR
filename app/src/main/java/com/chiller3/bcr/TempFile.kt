/*
 * SPDX-FileCopyrightText: 2023 Andrew Gunnerson
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.chiller3.bcr

import android.content.Context
import java.nio.file.Path
import kotlin.io.path.deleteExisting

fun <R> withTempFile(context: Context, prefix: String? = null, block: (Path) -> R): R {
    // Kotlin/Java has no O_CREAT|O_EXCL-based mechanism for securely creating temp files
    val temp = kotlin.io.path.createTempFile(context.cacheDir.toPath(), prefix)
    try {
        return block(temp)
    } finally {
        temp.deleteExisting()
    }
}