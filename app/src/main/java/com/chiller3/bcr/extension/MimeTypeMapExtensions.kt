/*
 * SPDX-FileCopyrightText: 2024 Andrew Gunnerson
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.chiller3.bcr.extension

import android.webkit.MimeTypeMap

private val MIME_TYPES_COMPAT = hashMapOf(
    // Android 9 and 10 didn't switch to mime-support [1] yet. Instead, their MimeTypeMap used
    // MimeUtils, which supported significantly fewer MIME types.
    // [1] https://android.googlesource.com/platform/external/mime-support/+/refs/heads/main/mime.types
    // [2] https://android.googlesource.com/platform/libcore/+/refs/tags/android-9.0.0_r61/luni/src/main/java/libcore/net/MimeUtils.java
    "audio/mp4" to "m4a",
)

fun MimeTypeMap.hasExtensionCompat(extension: String): Boolean =
    hasExtension(extension) || extension in MIME_TYPES_COMPAT.values

fun MimeTypeMap.getExtensionFromMimeTypeCompat(mimeType: String): String? =
    getExtensionFromMimeType(mimeType) ?: MIME_TYPES_COMPAT[mimeType]
