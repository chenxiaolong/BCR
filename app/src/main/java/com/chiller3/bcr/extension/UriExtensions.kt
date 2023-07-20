package com.chiller3.bcr.extension

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.telecom.PhoneAccount

val DOCUMENTSUI_AUTHORITY = "com.android.externalstorage.documents"

val Uri.formattedString: String
    get() = when (scheme) {
        ContentResolver.SCHEME_FILE -> path!!
        ContentResolver.SCHEME_CONTENT -> {
            val prefix = when (authority) {
                DOCUMENTSUI_AUTHORITY -> ""
                // Include the authority to reduce ambiguity when this isn't a SAF URI provided by
                // Android's local filesystem document provider
                else -> "[$authority] "
            }
            val segments = pathSegments

            // If this looks like a SAF tree/document URI, then try and show the document ID. This
            // cannot be implemented in a way that prevents all false positives.
            if (segments.size == 4 && segments[0] == "tree" && segments[2] == "document") {
                prefix + segments[3]
            } else if (segments.size == 2 && segments[0] == "tree") {
                prefix + segments[1]
            } else {
                toString()
            }
        }
        else -> toString()
    }

val Uri.phoneNumber: String?
    get() = when (scheme) {
        PhoneAccount.SCHEME_TEL -> schemeSpecificPart
        else -> null
    }

fun Uri.safTreeToDocument(): Uri {
    require(scheme == ContentResolver.SCHEME_CONTENT) { "Not a content URI" }

    val documentId = DocumentsContract.getTreeDocumentId(this)
    return DocumentsContract.buildDocumentUri(authority, documentId)
}
