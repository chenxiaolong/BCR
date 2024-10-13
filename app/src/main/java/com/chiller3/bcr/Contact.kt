/*
 * SPDX-FileCopyrightText: 2023-2024 Andrew Gunnerson
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.chiller3.bcr

import android.Manifest
import android.content.Context
import android.net.Uri
import android.os.Parcelable
import android.provider.ContactsContract
import androidx.annotation.RequiresPermission
import androidx.core.database.getLongOrNull
import androidx.core.database.getStringOrNull
import com.chiller3.bcr.output.PhoneNumber
import kotlinx.parcelize.Parcelize

private val PROJECTION = arrayOf(
    ContactsContract.PhoneLookup.LOOKUP_KEY,
    ContactsContract.PhoneLookup.DISPLAY_NAME,
)

private val PROJECTION_GROUP_MEMBERSHIP = arrayOf(
    ContactsContract.CommonDataKinds.GroupMembership.GROUP_ROW_ID,
    ContactsContract.CommonDataKinds.GroupMembership.GROUP_SOURCE_ID,
)

private val CONTACT_GROUP_PROJECTION = arrayOf(
    ContactsContract.Groups._ID,
    ContactsContract.Groups.SOURCE_ID,
    ContactsContract.Groups.TITLE,
)

data class ContactInfo(
    val lookupKey: String,
    val displayName: String,
)

sealed interface GroupLookup {
    data class RowId(val id: Long): GroupLookup

    data class SourceId(val id: String): GroupLookup
}

@Parcelize
data class ContactGroupInfo(
    val rowId: Long,
    val sourceId: String,
    val title: String,
) : Parcelable

@RequiresPermission(Manifest.permission.READ_CONTACTS)
fun findContactsByPhoneNumber(context: Context, number: PhoneNumber): Iterator<ContactInfo> {
    val rawNumber = number.toString()

    // Same heuristic as InCallUI's PhoneNumberHelper.isUriNumber()
    val numberIsSip = rawNumber.contains("@") || rawNumber.contains("%40")

    val uri = ContactsContract.PhoneLookup.ENTERPRISE_CONTENT_FILTER_URI.buildUpon()
        .appendPath(rawNumber)
        .appendQueryParameter(
            ContactsContract.PhoneLookup.QUERY_PARAMETER_SIP_ADDRESS,
            numberIsSip.toString())
        .build()

    return findContactsByUri(context, uri)
}

@RequiresPermission(Manifest.permission.READ_CONTACTS)
fun getContactByLookupKey(context: Context, lookupKey: String): ContactInfo? {
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

@RequiresPermission(Manifest.permission.READ_CONTACTS)
fun getContactGroupMemberships(context: Context, lookupKey: String) = iterator {
    val selection = buildString {
        append(ContactsContract.CommonDataKinds.GroupMembership.LOOKUP_KEY)
        append(" = ? AND ")
        append(ContactsContract.CommonDataKinds.GroupMembership.MIMETYPE)
        append(" = ?")
    }
    val selectionArgs = arrayOf(
        lookupKey,
        ContactsContract.CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE,
    )

    context.contentResolver.query(
        ContactsContract.Data.CONTENT_URI,
        PROJECTION_GROUP_MEMBERSHIP,
        selection,
        selectionArgs,
        null,
    )?.let { cursor ->
        val indexRowId = cursor.getColumnIndexOrThrow(
            ContactsContract.CommonDataKinds.GroupMembership.GROUP_ROW_ID)
        val indexSourceId = cursor.getColumnIndexOrThrow(
            ContactsContract.CommonDataKinds.GroupMembership.GROUP_SOURCE_ID)

        if (cursor.moveToFirst()) {
            cursor.getLongOrNull(indexRowId)?.let {
                yield(GroupLookup.RowId(it))
            }
            cursor.getStringOrNull(indexSourceId)?.let {
                yield(GroupLookup.SourceId(it))
            }

            while (cursor.moveToNext()) {
                cursor.getLongOrNull(indexRowId)?.let {
                    yield(GroupLookup.RowId(it))
                }
                cursor.getStringOrNull(indexSourceId)?.let {
                    yield(GroupLookup.SourceId(it))
                }
            }
        }
    }
}

@RequiresPermission(Manifest.permission.READ_CONTACTS)
fun getContactGroupById(context: Context, id: GroupLookup): ContactGroupInfo? {
    var selectionArgs: Array<String>? = null
    val selection = buildString {
        when (id) {
            is GroupLookup.RowId -> {
                append(ContactsContract.Groups._ID)
                append(" = ")
                append(id.id)
            }
            is GroupLookup.SourceId -> {
                append(ContactsContract.Groups.SOURCE_ID)
                append(" = ?")

                selectionArgs = arrayOf(id.id)
            }
        }
    }

    return findContactGroups(context, selection, selectionArgs).asSequence().firstOrNull()
}

fun findContactGroups(
    context: Context,
    selection: String? = null,
    selectionArgs: Array<String>? = null,
) = iterator {
    context.contentResolver.query(
        ContactsContract.Groups.CONTENT_URI,
        CONTACT_GROUP_PROJECTION,
        selection,
        selectionArgs,
        null,
    )?.use { cursor ->
        val indexRowId = cursor.getColumnIndexOrThrow(ContactsContract.Groups._ID)
        val indexSourceId = cursor.getColumnIndexOrThrow(ContactsContract.Groups.SOURCE_ID)
        val indexTitle = cursor.getColumnIndexOrThrow(ContactsContract.Groups.TITLE)

        if (cursor.moveToFirst()) {
            yield(ContactGroupInfo(
                cursor.getLong(indexRowId),
                cursor.getString(indexSourceId),
                cursor.getString(indexTitle),
            ))

            while (cursor.moveToNext()) {
                yield(ContactGroupInfo(
                    cursor.getLong(indexRowId),
                    cursor.getString(indexSourceId),
                    cursor.getString(indexTitle),
                ))
            }
        }
    }
}
