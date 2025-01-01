/*
 * SPDX-FileCopyrightText: 2023-2024 Andrew Gunnerson
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.chiller3.bcr.rule

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chiller3.bcr.ContactGroupInfo
import com.chiller3.bcr.ContactInfo
import com.chiller3.bcr.GroupLookup
import com.chiller3.bcr.Preferences
import com.chiller3.bcr.getContactByLookupKey
import com.chiller3.bcr.getContactGroupById
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class DisplayableRules(
    val rules: List<RecordRule>,
    val contacts: Map<String, ContactInfo?>,
    val groups: Map<Long, ContactGroupInfo?>,
)

class RecordRulesViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = Preferences(getApplication())

    private val _rules =
        MutableStateFlow<DisplayableRules>(DisplayableRules(emptyList(), emptyMap(), emptyMap()))
    val rules = _rules.asStateFlow()

    private val rulesMutex = Mutex()

    init {
        refreshRules()
    }

    private fun refreshRules() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                rulesMutex.withLock {
                    val rules = prefs.recordRules ?: Preferences.DEFAULT_RECORD_RULES
                    val contacts = hashMapOf<String, ContactInfo?>()
                    val groups = hashMapOf<Long, ContactGroupInfo?>()

                    for (rule in rules) {
                        when (val n = rule.callNumber) {
                            is RecordRule.CallNumber.Contact -> {
                                if (n.lookupKey !in contacts) {
                                    contacts[n.lookupKey] = getContact(n.lookupKey)
                                }
                            }
                            is RecordRule.CallNumber.ContactGroup -> {
                                // We're not persisting anything here. Keying by row ID is
                                // sufficient.
                                if (n.rowId !in groups) {
                                    groups[n.rowId] = getContactGroup(n.rowId, n.sourceId)
                                }
                            }
                            else -> {}
                        }
                    }

                    _rules.update { DisplayableRules(rules, contacts, groups) }
                }
            }
        }
    }

    private fun getContact(lookupKey: String): ContactInfo? {
        if (getApplication<Application>().checkSelfPermission(Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED) {
            return null
        }

        return try {
            getContactByLookupKey(getApplication(), lookupKey)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to look up contact", e)
            null
        }
    }

    private fun getContactGroup(rowId: Long, sourceId: String?): ContactGroupInfo? {
        if (getApplication<Application>().checkSelfPermission(Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED) {
            return null
        }

        val groupLookup = if (sourceId != null) {
            GroupLookup.SourceId(sourceId)
        } else {
            GroupLookup.RowId(rowId)
        }

        return try {
            getContactGroupById(getApplication(), groupLookup)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to look up contact group", e)
            null
        }
    }

    fun addRule(newRule: RecordRule) {
        val newRules = arrayListOf(newRule)
        newRules.addAll(rules.value.rules)
        replaceRules(newRules)
    }

    fun replaceRule(position: Int, newRule: RecordRule) {
        val newRules = rules.value.rules.mapIndexed { i, rule ->
            if (i == position) {
                newRule
            } else {
                rule
            }
        }
        replaceRules(newRules)
    }

    fun replaceRules(newRules: List<RecordRule>) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                rulesMutex.withLock {
                    if (newRules == Preferences.DEFAULT_RECORD_RULES) {
                        Log.d(TAG, "New rules match defaults; clearing explicit settings")
                        prefs.recordRules = null
                    } else {
                        Log.d(TAG, "New rules: $newRules")
                        prefs.recordRules = newRules
                    }

                    refreshRules()
                }
            }
        }
    }

    fun reset() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                rulesMutex.withLock {
                    prefs.recordRules = null
                    refreshRules()
                }
            }
        }
    }

    companion object {
        private val TAG = RecordRulesViewModel::class.java.simpleName
    }
}
