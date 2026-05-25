/*
 * SPDX-FileCopyrightText: 2024-2026 Andrew Gunnerson
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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RecordRuleEditorViewModel(application: Application) : AndroidViewModel(application) {
    private val _alerts = MutableStateFlow<List<RecordRuleEditorAlert>>(emptyList())
    val alerts = _alerts.asStateFlow()

    private lateinit var _rule: MutableStateFlow<RecordRule>

    private val _contactInfo = MutableStateFlow<ContactInfo?>(null)
    val contactInfo = _contactInfo.asStateFlow()

    private val _contactGroupInfo = MutableStateFlow<ContactGroupInfo?>(null)
    val contactGroupInfo = _contactGroupInfo.asStateFlow()

    fun acknowledgeFirstAlert() {
        _alerts.update { it.drop(1) }
    }

    fun addAlert(alert: RecordRuleEditorAlert) {
        _alerts.update { it + alert }
    }

    fun initOrGetExisting(initialRule: RecordRule): StateFlow<RecordRule> {
        if (!::_rule.isInitialized) {
            _rule = MutableStateFlow(initialRule)
        }

        return _rule.asStateFlow()
    }

    fun setRule(rule: RecordRule) {
        val oldRule = _rule.getAndUpdate { rule }
        if (oldRule != rule) {
            refresh()
        }
    }

    fun refresh() {
        when (val callNumber = _rule.value.callNumber) {
            RecordRule.CallNumber.Any -> {}
            is RecordRule.CallNumber.Contact ->
                lookUpContact(callNumber.lookupKey)
            is RecordRule.CallNumber.ContactGroup ->
                lookUpContactGroup(callNumber.rowId, callNumber.sourceId)
            RecordRule.CallNumber.Unknown -> {}
        }
    }

    // NOTE: The functions that update the lookup fields are inherently racy. We do not try to abort
    // previous lookups when a new one is scheduled because they basically always complete faster
    // than the user is able to react.

    private fun lookUpContact(lookupKey: String) {
        if (_contactInfo.value?.lookupKey == lookupKey) {
            return
        }

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

                _contactInfo.update { contact }
            }
        }
    }

    private fun lookUpContactGroup(rowId: Long, sourceId: String?) {
        val prev = _contactGroupInfo.value
        if (prev?.rowId == rowId && prev.sourceId == sourceId) {
            return
        }

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

                _contactGroupInfo.update { group }
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
                    return@withContext
                }

                _contactInfo.update { contact }

                val callNumber = RecordRule.CallNumber.Contact(contact.lookupKey)
                setRule(_rule.value.copy(callNumber = callNumber))
            }
        }
    }

    fun selectContactGroup(group: ContactGroupInfo) {
        _contactGroupInfo.update { group }

        val callNumber = RecordRule.CallNumber.ContactGroup(group.rowId, group.sourceId)
        setRule(_rule.value.copy(callNumber = callNumber))
    }

    companion object {
        private val TAG = RecordRuleEditorViewModel::class.java.simpleName
    }
}
