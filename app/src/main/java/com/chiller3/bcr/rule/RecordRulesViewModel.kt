package com.chiller3.bcr.rule

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chiller3.bcr.Preferences
import com.chiller3.bcr.findContactByLookupKey
import com.chiller3.bcr.findContactsByUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class DisplayedRecordRule : Comparable<DisplayedRecordRule> {
    abstract var record: Boolean

    /**
     * [Contact] comes first, sorted by display name, followed by [UnknownCalls] and [AllCalls].
     */
    override fun compareTo(other: DisplayedRecordRule): Int {
        when (this) {
            is AllCalls -> {
                return when (other) {
                    is AllCalls -> record.compareTo(other.record)
                    else -> 1
                }
            }
            is UnknownCalls -> {
                return when (other) {
                    is UnknownCalls -> record.compareTo(other.record)
                    is AllCalls -> -1
                    is Contact -> 1
                }
            }
            is Contact -> {
                return when (other) {
                    is Contact -> compareValuesBy(
                        this,
                        other,
                        { it.displayName },
                        { it.lookupKey },
                        { it.record },
                    )
                    else -> -1
                }
            }
        }
    }

    data class AllCalls(override var record: Boolean) : DisplayedRecordRule()

    data class UnknownCalls(override var record: Boolean) : DisplayedRecordRule()

    data class Contact(
        val lookupKey: String,
        val displayName: String?,
        override var record: Boolean,
    ) : DisplayedRecordRule()
}

sealed class Message {
    data object RuleAdded : Message()

    data object RuleExists : Message()
}

class RecordRulesViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = Preferences(getApplication())

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages

    private val _rules = MutableStateFlow<List<DisplayedRecordRule>>(emptyList())
    val rules: StateFlow<List<DisplayedRecordRule>> = _rules

    init {
        refreshRules()
    }

    fun acknowledgeFirstMessage() {
        _messages.update { it.drop(1) }
    }

    private fun refreshRules() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val rawRules = prefs.recordRules ?: Preferences.DEFAULT_RECORD_RULES
                val displayRules = rawRules.map { rule ->
                    when (rule) {
                        is RecordRule.AllCalls -> DisplayedRecordRule.AllCalls(rule.record)
                        is RecordRule.UnknownCalls -> DisplayedRecordRule.UnknownCalls(rule.record)
                        is RecordRule.Contact -> DisplayedRecordRule.Contact(
                            rule.lookupKey,
                            getContactDisplayName(rule.lookupKey),
                            rule.record,
                        )
                    }
                }

                // Update and re-save the rules since the display name may have changed, resulting
                // in a new sort order.
                updateAndSaveRules {
                    displayRules
                }
            }
        }
    }

    private fun saveRules(newRules: List<DisplayedRecordRule>) {
        val rawRules = newRules.map { displayedRule ->
            when (displayedRule) {
                is DisplayedRecordRule.AllCalls ->
                    RecordRule.AllCalls(displayedRule.record)
                is DisplayedRecordRule.UnknownCalls ->
                    RecordRule.UnknownCalls(displayedRule.record)
                is DisplayedRecordRule.Contact ->
                    RecordRule.Contact(displayedRule.lookupKey, displayedRule.record)
            }
        }

        if (rawRules == Preferences.DEFAULT_RECORD_RULES) {
            prefs.recordRules = null
        } else {
            prefs.recordRules = rawRules
        }
    }

    private fun updateAndSaveRules(
        block: (old: List<DisplayedRecordRule>) -> List<DisplayedRecordRule>,
    ) {
        _rules.update {
            val newRules = block(it).sorted()
            saveRules(newRules)
            newRules
        }
    }

    private fun getContactDisplayName(lookupKey: String): String? {
        if (getApplication<Application>().checkSelfPermission(Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED) {
            return null
        }

        return try {
            findContactByLookupKey(getApplication(), lookupKey)?.displayName
        } catch (e: Exception) {
            Log.w(TAG, "Failed to look up contact", e)
            null
        }
    }

    fun addContactRule(uri: Uri) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val contact = try {
                    findContactsByUri(getApplication(), uri).asSequence().firstOrNull()
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to query contact at $uri", e)
                    return@withContext
                }
                if (contact == null) {
                    Log.w(TAG, "Contact not found at $uri")
                    return@withContext
                }

                val oldRules = rules.value
                val existingRule = oldRules.find {
                    it is DisplayedRecordRule.Contact && it.lookupKey == contact.lookupKey
                }

                if (existingRule != null) {
                    _messages.update { it + Message.RuleExists }
                } else {
                    val newRules = ArrayList(rules.value)
                    newRules.add(
                        DisplayedRecordRule.Contact(
                            contact.lookupKey,
                            contact.displayName,
                            true
                        )
                    )

                    updateAndSaveRules { newRules }

                    _messages.update { it + Message.RuleAdded }
                }
            }
        }
    }

    fun setRuleRecord(index: Int, record: Boolean) {
        updateAndSaveRules {
            it.mapIndexed { i, displayedRule ->
                if (i == index) {
                    when (displayedRule) {
                        is DisplayedRecordRule.AllCalls -> displayedRule.copy(record = record)
                        is DisplayedRecordRule.UnknownCalls -> displayedRule.copy(record = record)
                        is DisplayedRecordRule.Contact -> displayedRule.copy(record = record)
                    }
                } else {
                    displayedRule
                }
            }
        }
    }

    fun deleteRule(index: Int) {
        updateAndSaveRules {
            it.filterIndexed { i, _ -> i != index }
        }
    }

    fun reset() {
        prefs.recordRules = null
        refreshRules()
    }

    companion object {
        private val TAG = RecordRulesViewModel::class.java.simpleName
    }
}
