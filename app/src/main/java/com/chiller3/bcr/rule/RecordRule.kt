package com.chiller3.bcr.rule

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.util.Log
import com.chiller3.bcr.GroupLookup
import com.chiller3.bcr.findContactsByPhoneNumber
import com.chiller3.bcr.getContactGroupMemberships
import com.chiller3.bcr.output.PhoneNumber

sealed class RecordRule {
    abstract val record: Boolean

    /**
     * Check if the rule matches the set of contacts in [contactLookupKeys].
     *
     * @param contactLookupKeys The set of contacts, if any, involved in the call.
     * @param contactGroupIds The list of contact groups associated with [contactLookupKeys].
     */
    abstract fun matches(
        contactLookupKeys: Collection<String>,
        contactGroupIds: Collection<GroupLookup>,
    ): Boolean

    open fun toRawPreferences(editor: SharedPreferences.Editor, prefix: String) {
        editor.putString(prefix + PREF_SUFFIX_TYPE, javaClass.simpleName)
        editor.putBoolean(prefix + PREF_SUFFIX_RECORD, record)
    }

    data class AllCalls(override val record: Boolean) : RecordRule() {
        override fun matches(
            contactLookupKeys: Collection<String>,
            contactGroupIds: Collection<GroupLookup>,
        ): Boolean = true

        companion object {
            fun fromRawPreferences(prefs: SharedPreferences, prefix: String): AllCalls {
                val record = prefs.getBoolean(prefix + PREF_SUFFIX_RECORD, false)

                return AllCalls(record)
            }
        }
    }

    data class UnknownCalls(override val record: Boolean) : RecordRule() {
        override fun matches(
            contactLookupKeys: Collection<String>,
            contactGroupIds: Collection<GroupLookup>,
        ): Boolean = contactLookupKeys.isEmpty()

        companion object {
            fun fromRawPreferences(prefs: SharedPreferences, prefix: String): UnknownCalls {
                val record = prefs.getBoolean(prefix + PREF_SUFFIX_RECORD, false)

                return UnknownCalls(record)
            }
        }
    }

    data class Contact(val lookupKey: String, override val record: Boolean) : RecordRule() {
        override fun matches(
            contactLookupKeys: Collection<String>,
            contactGroupIds: Collection<GroupLookup>,
        ): Boolean = lookupKey in contactLookupKeys

        override fun toRawPreferences(editor: SharedPreferences.Editor, prefix: String) {
            super.toRawPreferences(editor, prefix)
            editor.putString(prefix + PREF_SUFFIX_CONTACT_LOOKUP_KEY, lookupKey)
        }

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

    data class ContactGroup(
        val rowId: Long,
        val sourceId: String,
        override val record: Boolean,
    ) : RecordRule() {
        override fun matches(
            contactLookupKeys: Collection<String>,
            contactGroupIds: Collection<GroupLookup>,
        ): Boolean = contactGroupIds.any {
            when (it) {
                is GroupLookup.RowId -> it.id == rowId
                is GroupLookup.SourceId -> it.id == sourceId
            }
        }

        override fun toRawPreferences(editor: SharedPreferences.Editor, prefix: String) {
            super.toRawPreferences(editor, prefix)
            editor.putLong(prefix + PREF_SUFFIX_CONTACT_GROUP_ROW_ID, rowId)
            editor.putString(prefix + PREF_SUFFIX_CONTACT_GROUP_SOURCE_ID, sourceId)
        }

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
                    ?: throw IllegalStateException("Missing $prefSourceId")

                val record = prefs.getBoolean(prefix + PREF_SUFFIX_RECORD, false)

                return ContactGroup(rowId, sourceId, record)
            }
        }
    }

    companion object {
        private val TAG = RecordRule::class.java.simpleName

        private const val PREF_SUFFIX_TYPE = "type"
        private const val PREF_SUFFIX_RECORD = "record"

        fun fromRawPreferences(prefs: SharedPreferences, prefix: String): RecordRule? {
            return when (val type = prefs.getString(prefix + PREF_SUFFIX_TYPE, null)) {
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

        /**
         * Evaluate list of rules to determine if automatic recording is enabled for any of
         * [numbers].
         *
         * [Contact] and [UnknownCalls] rules are silently ignored if the contacts permission is not
         * granted.
         *
         * @param rules Must contain [AllCalls]
         *
         * @throws IllegalArgumentException if [rules] does not contain [AllCalls]
         */
        fun evaluate(context: Context, rules: List<RecordRule>,
                     numbers: Collection<PhoneNumber>): Boolean {
            val contactsAllowed = context.checkSelfPermission(Manifest.permission.READ_CONTACTS) ==
                    PackageManager.PERMISSION_GRANTED
            var contactLookupKeys = emptySet<String>()
            var contactGroupIds = emptySet<GroupLookup>()

            if (contactsAllowed) {
                contactLookupKeys = hashSetOf<String>().apply {
                    for (number in numbers) {
                        findContactsByPhoneNumber(context, number)
                            .asSequence()
                            .map { it.lookupKey }
                            .toCollection(this)
                    }
                }

                // Avoid doing group membership lookups if we don't need to.
                if (rules.any { it is ContactGroup }) {
                    contactGroupIds = hashSetOf<GroupLookup>().apply {
                        for (lookupKey in contactLookupKeys) {
                            getContactGroupMemberships(context, lookupKey)
                                .asSequence()
                                .toCollection(this)
                        }
                    }
                }
            } else {
                Log.i(TAG, "Contacts permission not granted")
            }

            for (rule in rules) {
                if (rule.matches(contactLookupKeys, contactGroupIds)) {
                    Log.i(TAG, "Matched rule: $rule")
                    return rule.record
                }
            }

            throw IllegalArgumentException("Rule list does not contain AllCalls")
        }
    }
}
