/*
 * SPDX-FileCopyrightText: 2023-2026 Andrew Gunnerson
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
import kotlinx.coroutines.withContext

data class DisplayableRule(
    val id: Int,
    val rule: RecordRule,
)

data class DisplayableRules(
    val rules: List<DisplayableRule>,
    val contacts: Map<String, ContactInfo?>,
    val groups: Map<Long, ContactGroupInfo?>,
)

class RecordRulesViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = Preferences(getApplication())

    private val _rules = MutableStateFlow(emptyList<DisplayableRule>())
    val rules = _rules.asStateFlow()

    private val _contacts = MutableStateFlow(emptyMap<String, ContactInfo?>())
    val contacts = _contacts.asStateFlow()

    private val _groups = MutableStateFlow(emptyMap<Long, ContactGroupInfo?>())
    val groups = _groups.asStateFlow()

    // We need a stable ID for each element that's not position dependent. A position dependent key
    // works fine with just LazyColumn, but once rememberSwipeToDismissBoxState() is added to the
    // mix, the remembered state is applied to the wrong rule after deleting a rule. We can't use
    // the rule itself as a key since there can be duplicates.
    private var nextId = 0

    init {
        loadFromPreferences()
    }

    private fun newDisplayableRule(rule: RecordRule) =
        DisplayableRule(nextId, rule).apply { nextId++ }

    private fun loadFromPreferences() {
        _rules.update {
            (prefs.recordRules ?: Preferences.DEFAULT_RECORD_RULES)
                .map(::newDisplayableRule)
        }
        refreshContactsAndGroups()
    }

    private fun refreshContactsAndGroups() {
        val rules = rules.value

        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val contacts = hashMapOf<String, ContactInfo?>()
                val groups = hashMapOf<Long, ContactGroupInfo?>()

                for (rule in rules) {
                    when (val n = rule.rule.callNumber) {
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

                _contacts.update { contacts }
                _groups.update { groups }
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
        val newRules = arrayListOf(newDisplayableRule(newRule))
        newRules.addAll(rules.value)

        replaceRules(newRules)
        refreshContactsAndGroups()
    }

    fun replaceRule(position: Int, newRule: RecordRule) {
        val newRules = rules.value.mapIndexed { i, rule ->
            when (i) {
                position -> newDisplayableRule(newRule)
                else -> rule
            }
        }

        replaceRules(newRules)
        refreshContactsAndGroups()
    }

    fun moveRule(oldPosition: Int, newPosition: Int) {
        val oldRules = rules.value
        val newRules = oldRules.mapIndexed { i, rule ->
            when (i) {
                oldPosition -> oldRules[newPosition]
                newPosition -> oldRules[oldPosition]
                else -> rule
            }
        }

        replaceRules(newRules)
    }

    fun removeRule(position: Int) {
        val oldRules = rules.value
        val newRules = oldRules.mapIndexedNotNull { i, rule ->
            when (i) {
                position -> null
                else -> rule
            }
        }

        replaceRules(newRules)
        // We intentionally do not clean up contact and group info.
    }

    private fun replaceRules(newRules: List<DisplayableRule>) {
        if (newRules == Preferences.DEFAULT_RECORD_RULES) {
            Log.d(TAG, "New rules match defaults; clearing explicit settings")
            prefs.recordRules = null
        } else {
            Log.d(TAG, "New rules: $newRules")
            prefs.recordRules = newRules.map { it.rule }
        }

        _rules.update { newRules }
    }

    fun reset() {
        prefs.recordRules = null
        loadFromPreferences()
    }

    companion object {
        private val TAG = RecordRulesViewModel::class.java.simpleName
    }
}
