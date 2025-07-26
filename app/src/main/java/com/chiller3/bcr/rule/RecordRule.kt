/*
 * SPDX-FileCopyrightText: 2023-2024 Andrew Gunnerson
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.chiller3.bcr.rule

import android.content.Context
import android.content.SharedPreferences
import android.os.Parcelable
import android.util.Log
import com.chiller3.bcr.GroupLookup
import com.chiller3.bcr.output.CallDirection
import com.chiller3.bcr.output.PhoneNumber
import com.chiller3.bcr.withContactGroupMemberships
import com.chiller3.bcr.withContactsByPhoneNumber
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A rule specifies several conditions, which are ANDed together, and if a call matches, then the
 * specified action is taken.
 */
@Parcelize
@Serializable
data class RecordRule(
    @SerialName("call_number")
    val callNumber: CallNumber,
    @SerialName("call_type")
    val callType: CallType,
    @SerialName("sim_slot")
    val simSlot: SimSlot,
    val action: Action,
) : Parcelable {
    @Parcelize
    @Serializable
    sealed interface CallNumber : Parcelable {
        /**
         * Check if the condition matches the set of contacts in [contactLookupKeys] or contact groups
         * in [contactGroupIds].
         *
         * @param contactLookupKeys The set of contacts, if any, involved in the call.
         * @param contactGroupIds The list of contact groups associated with [contactLookupKeys].
         */
        fun matches(
            contactLookupKeys: Collection<String>,
            contactGroupIds: Collection<GroupLookup>,
        ): Boolean

        @Parcelize
        @Serializable
        @SerialName("any")
        data object Any : CallNumber {
            override fun matches(
                contactLookupKeys: Collection<String>,
                contactGroupIds: Collection<GroupLookup>
            ): Boolean = true
        }

        @Parcelize
        @Serializable
        @SerialName("contact")
        data class Contact(val lookupKey: String) : CallNumber {
            override fun matches(
                contactLookupKeys: Collection<String>,
                contactGroupIds: Collection<GroupLookup>,
            ): Boolean = lookupKey in contactLookupKeys
        }

        @Parcelize
        @Serializable
        @SerialName("contact_group")
        data class ContactGroup(val rowId: Long, val sourceId: String?) : CallNumber {
            override fun matches(
                contactLookupKeys: Collection<String>,
                contactGroupIds: Collection<GroupLookup>,
            ): Boolean = contactGroupIds.any {
                when (it) {
                    is GroupLookup.RowId -> it.id == rowId
                    is GroupLookup.SourceId -> it.id == sourceId
                }
            }
        }

        @Parcelize
        @Serializable
        @SerialName("unknown")
        data object Unknown : CallNumber {
            override fun matches(
                contactLookupKeys: Collection<String>,
                contactGroupIds: Collection<GroupLookup>,
            ): Boolean = contactLookupKeys.isEmpty()
        }
    }

    @Parcelize
    @Serializable
    enum class CallType : Parcelable {
        ANY,
        INCOMING,
        OUTGOING,
        CONFERENCE;

        fun matches(direction: CallDirection?): Boolean = when (this) {
            ANY -> true
            INCOMING -> direction == CallDirection.IN
            OUTGOING -> direction == CallDirection.OUT
            CONFERENCE -> direction == CallDirection.CONFERENCE
        }
    }

    @Parcelize
    @Serializable
    sealed interface SimSlot : Parcelable {
        fun matches(simSlot: Int?): Boolean

        @Parcelize
        @Serializable
        @SerialName("any")
        data object Any : SimSlot {
            override fun matches(simSlot: Int?): Boolean = true
        }

        @Parcelize
        @Serializable
        @SerialName("specific")
        data class Specific(val slot: Int) : SimSlot {
            override fun matches(simSlot: Int?): Boolean = simSlot == slot
        }
    }

    @Parcelize
    @Serializable
    enum class Action : Parcelable {
        /**
         * Save the recording when the call ends unless the user chooses to delete it during the call
         * via the notification.
         */
        SAVE,

        /**
         * Delete the recording when the call ends unless the user chooses to preserve it during the
         * call via the notification.
         */
        DISCARD,

        /**
         * Completely ignore the call. [com.chiller3.bcr.RecorderThread] will exit without writing any
         * output files. It is not possible to start a recording later for a matching call.
         */
        IGNORE,
    }

    companion object {
        private val TAG = RecordRule::class.java.simpleName

        /**
         * Evaluate list of rules to determine the action to take.
         *
         * @throws IllegalArgumentException if no [rules] match the call
         */
        fun evaluate(
            context: Context,
            rules: List<RecordRule>,
            numbers: Collection<PhoneNumber>,
            direction: CallDirection?,
            simSlot: Int?,
        ): Action {
            var contactLookupKeys = emptySet<String>()
            var contactGroupIds = emptySet<GroupLookup>()

            try {
                contactLookupKeys = hashSetOf<String>().apply {
                    for (number in numbers) {
                        withContactsByPhoneNumber(context, number) { contacts ->
                            contacts.map { it.lookupKey }.toCollection(this)
                        }
                    }
                }

                // Avoid doing group membership lookups if we don't need to.
                if (rules.any { it.callNumber is CallNumber.ContactGroup }) {
                    contactGroupIds = hashSetOf<GroupLookup>().apply {
                        for (lookupKey in contactLookupKeys) {
                            withContactGroupMemberships(context, lookupKey) {
                                it.toCollection(this)
                            }
                        }
                    }
                }
            } catch (e: SecurityException) {
                Log.w(TAG, "Permission denied when querying contacts and contact groups", e)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to query contacts and contact groups", e)
            }

            Log.d(TAG, "Contact lookup keys: $contactLookupKeys")
            Log.d(TAG, "Contact group IDs: $contactGroupIds")

            for (rule in rules) {
                Log.d(TAG, "Checking rule: $rule")

                val callNumberMatches = rule.callNumber.matches(contactLookupKeys, contactGroupIds)
                val callTypeMatches = rule.callType.matches(direction)
                val simSlotMatches = rule.simSlot.matches(simSlot)
                Log.d(TAG, "- Matches: callNumber=$callNumberMatches, callType=$callTypeMatches, simSlot=${simSlotMatches}")

                if (callNumberMatches && callTypeMatches && simSlotMatches) {
                    Log.i(TAG, "- Matched this rule")
                    return rule.action
                }
            }

            throw IllegalArgumentException("Call does not match any rule")
        }
    }
}

sealed class LegacyRecordRule {
    abstract val record: Boolean

    private data class AllCalls(override val record: Boolean) : LegacyRecordRule() {
        companion object {
            fun fromRawPreferences(prefs: SharedPreferences, prefix: String): AllCalls {
                val record = prefs.getBoolean(prefix + PREF_SUFFIX_RECORD, false)

                return AllCalls(record)
            }
        }
    }

    private data class UnknownCalls(override val record: Boolean) : LegacyRecordRule() {
        companion object {
            fun fromRawPreferences(prefs: SharedPreferences, prefix: String): UnknownCalls {
                val record = prefs.getBoolean(prefix + PREF_SUFFIX_RECORD, false)

                return UnknownCalls(record)
            }
        }
    }

    private data class Contact(val lookupKey: String, override val record: Boolean) : LegacyRecordRule() {
        companion object {
            private const val PREF_SUFFIX_CONTACT_LOOKUP_KEY = "contact_lookup_key"

            fun fromRawPreferences(prefs: SharedPreferences, prefix: String): Contact {
                val prefLookupKey = prefix + PREF_SUFFIX_CONTACT_LOOKUP_KEY
                val contactId = prefs.getString(prefLookupKey, null)
                    ?: throw IllegalArgumentException("Missing $prefLookupKey")
                val record = prefs.getBoolean(prefix + PREF_SUFFIX_RECORD, false)

                return Contact(contactId, record)
            }
        }
    }

    private data class ContactGroup(
        val rowId: Long,
        val sourceId: String?,
        override val record: Boolean,
    ) : LegacyRecordRule() {
        companion object {
            private const val PREF_SUFFIX_CONTACT_GROUP_ROW_ID = "contact_group_row_id"
            private const val PREF_SUFFIX_CONTACT_GROUP_SOURCE_ID = "contact_group_source_id"

            fun fromRawPreferences(prefs: SharedPreferences, prefix: String): ContactGroup {
                val prefRowId = prefix + PREF_SUFFIX_CONTACT_GROUP_ROW_ID
                val rowId = prefs.getLong(prefRowId, -1)
                if (rowId == -1L) {
                    throw IllegalStateException("Missing $prefRowId")
                }

                val prefSourceId = prefix + PREF_SUFFIX_CONTACT_GROUP_SOURCE_ID
                val sourceId = prefs.getString(prefSourceId, null)

                val record = prefs.getBoolean(prefix + PREF_SUFFIX_RECORD, false)

                return ContactGroup(rowId, sourceId, record)
            }
        }
    }

    companion object {
        private val TAG = LegacyRecordRule::class.java.simpleName

        private const val PREF_SUFFIX_TYPE = "type"
        private const val PREF_SUFFIX_RECORD = "record"

        fun fromRawPreferences(prefs: SharedPreferences, prefix: String): LegacyRecordRule? {
            // The type was named after the class name, which used to not have the Legacy prefix.
            val type = prefs.getString(prefix + PREF_SUFFIX_TYPE, null)?.let { "Legacy$it" }

            return when (type) {
                AllCalls::class.java.simpleName ->
                    AllCalls.fromRawPreferences(prefs, prefix)
                UnknownCalls::class.java.simpleName ->
                    UnknownCalls.fromRawPreferences(prefs, prefix)
                Contact::class.java.simpleName ->
                    Contact.fromRawPreferences(prefs, prefix)
                ContactGroup::class.java.simpleName ->
                    ContactGroup.fromRawPreferences(prefs, prefix)
                null -> null
                else -> {
                    Log.w(TAG, "Unknown record rule type: $type")
                    null
                }
            }
        }

        fun convertToModernRules(legacyRules: List<LegacyRecordRule>): List<RecordRule> {
            val modernRules = mutableListOf<RecordRule>()

            for (legacyRule in legacyRules) {
                val callNumber = when (legacyRule) {
                    is AllCalls -> RecordRule.CallNumber.Any
                    is Contact -> RecordRule.CallNumber.Contact(legacyRule.lookupKey)
                    is ContactGroup -> RecordRule.CallNumber.ContactGroup(
                        legacyRule.rowId,
                        legacyRule.sourceId,
                    )
                    is UnknownCalls -> RecordRule.CallNumber.Unknown
                }

                modernRules.add(RecordRule(
                    callNumber = callNumber,
                    callType = RecordRule.CallType.ANY,
                    simSlot = RecordRule.SimSlot.Any,
                    action = if (legacyRule.record) {
                        RecordRule.Action.SAVE
                    } else {
                        RecordRule.Action.DISCARD
                    },
                ))
            }

            return modernRules
        }
    }
}
