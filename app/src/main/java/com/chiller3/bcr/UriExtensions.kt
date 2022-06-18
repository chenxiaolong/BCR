package com.chiller3.bcr

import android.content.ContentResolver
import android.net.Uri

val Uri.formattedString: String
    get() = when {
        scheme == ContentResolver.SCHEME_FILE -> path!!
        scheme == ContentResolver.SCHEME_CONTENT
                && authority == "com.android.externalstorage.documents" -> {
            // DocumentsContract.findDocumentPath() may sometimes crash with a permission error
            // when passed a children document URI after an app upgrade, even though the app still
            // has valid persisted URI permissions. Instead, just parse the URI manually. The format
            // of SAF URIs hasn't changed across the versions of Android that BCR supports.
            val segments = pathSegments
            val treeIndex = segments.indexOf("tree")

            if (treeIndex >= 0 && treeIndex < segments.size - 1) {
                segments[treeIndex + 1]
            } else {
                toString()
            }
        }
        else -> toString()
    }