package com.chiller3.bcr

import android.Manifest
import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import androidx.annotation.RequiresPermission

private val PROJECTION = arrayOf(
    ContactsContract.PhoneLookup.LOOKUP_KEY,
    ContactsContract.PhoneLookup.DISPLAY_NAME,
)

data class ContactInfo(
    val lookupKey: String,
    val displayName: String,
)

@RequiresPermission(Manifest.permission.READ_CONTACTS)
fun findContactsByPhoneNumber(context: Context, number: String): Iterator<ContactInfo> {
    // Same heuristic as InCallUI's PhoneNumberHelper.isUriNumber()
    val numberIsSip = number.contains("@") || number.contains("%40")

    val uri = ContactsContract.PhoneLookup.ENTERPRISE_CONTENT_FILTER_URI.buildUpon()
        .appendPath(number)
        .appendQueryParameter(
            ContactsContract.PhoneLookup.QUERY_PARAMETER_SIP_ADDRESS,
            numberIsSip.toString())
        .build()

    return findContactsByUri(context, uri)
}

@RequiresPermission(Manifest.permission.READ_CONTACTS)
fun findContactByLookupKey(context: Context, lookupKey: String): ContactInfo? {
    val uri = ContactsContract.Contacts.CONTENT_LOOKUP_URI.buildUpon()
        .appendPath(lookupKey)
        .build()

    return findContactsByUri(context, uri).asSequence().firstOrNull()
}

fun findContactsByUri(context: Context, uri: Uri) = iterator {
    context.contentResolver.query(uri, PROJECTION, null, null, null)?.use { cursor ->
        val indexLookupKey = cursor.getColumnIndexOrThrow(ContactsContract.PhoneLookup.LOOKUP_KEY)
        val indexName = cursor.getColumnIndexOrThrow(ContactsContract.PhoneLookup.DISPLAY_NAME)

        if (cursor.moveToFirst()) {
            yield(ContactInfo(cursor.getString(indexLookupKey), cursor.getString(indexName)))

            while (cursor.moveToNext()) {
                yield(ContactInfo(cursor.getString(indexLookupKey), cursor.getString(indexName)))
            }
        }
    }
}
