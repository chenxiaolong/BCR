/*
 * SPDX-FileCopyrightText: 2024 Andrew Gunnerson
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.chiller3.bcr.rule

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chiller3.bcr.ContactGroupInfo
import com.chiller3.bcr.ContactInfo
import com.chiller3.bcr.GroupLookup
import com.chiller3.bcr.getContactByLookupKey
import com.chiller3.bcr.getContactGroupById
import com.chiller3.bcr.withContactsByUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RecordRuleEditorViewModel(application: Application) : AndroidViewModel(application) {
    private val _contactInfoLookup = MutableStateFlow<ContactInfo?>(null)
    val contactInfoLookup = _contactInfoLookup.asStateFlow()

    private val _contactGroupInfoLookup = MutableStateFlow<ContactGroupInfo?>(null)
    val contactGroupInfoLookup = _contactGroupInfoLookup.asStateFlow()

    private val _contactInfoSelection = MutableStateFlow<ContactInfo?>(null)
    val contactInfoSelection = _contactInfoSelection.asStateFlow()

    private val _contactGroupInfoSelection = MutableStateFlow<ContactGroupInfo?>(null)
    val contactGroupInfoSelection = _contactGroupInfoSelection.asStateFlow()

    // NOTE: The functions that update the lookup fields are inherently racy. We do not try to abort
    // previous lookups when a new one is scheduled because they basically always complete faster
    // than the user is able to react.

    fun lookUpContact(lookupKey: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val contact = try {
                    getContactByLookupKey(getApplication(), lookupKey)
                } catch (e: SecurityException) {
                    Log.w(TAG, "Permission denied when looking up contact", e)
                    null
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to look up contact", e)
                    null
                }

                _contactInfoLookup.update { contact }
            }
        }
    }

    fun lookUpContactGroup(rowId: Long, sourceId: String?) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val groupLookup = if (sourceId != null) {
                    GroupLookup.SourceId(sourceId)
                } else {
                    GroupLookup.RowId(rowId)
                }

                val group = try {
                    getContactGroupById(getApplication(), groupLookup)
                } catch (e: SecurityException) {
                    Log.w(TAG, "Permission denied when looking up contact group", e)
                    null
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to look up contact group", e)
                    null
                }

                _contactGroupInfoLookup.update { group }
            }
        }
    }

    fun selectContact(uri: Uri) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val contact = try {
                    withContactsByUri(getApplication(), uri) { it.firstOrNull() }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to query contact at $uri", e)
                    null
                }
                if (contact == null) {
                    Log.w(TAG, "Contact not found at $uri")
                }

                _contactInfoSelection.update { contact }
            }
        }
    }

    fun selectContactGroup(group: ContactGroupInfo) {
        _contactGroupInfoSelection.update { group }
    }

    fun useSelectedContact() {
        val contact = _contactInfoSelection.getAndUpdate { null }
        _contactInfoLookup.update { contact }
    }

    fun useSelectedContactGroup() {
        val group = _contactGroupInfoSelection.getAndUpdate { null }
        _contactGroupInfoLookup.update { group }
    }

    companion object {
        private val TAG = RecordRuleEditorViewModel::class.java.simpleName
    }
}
