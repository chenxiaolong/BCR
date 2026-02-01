/*
 * SPDX-FileCopyrightText: 2023-2026 Andrew Gunnerson
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.chiller3.bcr.rule

import android.content.Context
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

            for (rule in rules) {
                val matches = rule.callNumber.matches(contactLookupKeys, contactGroupIds)
                        && rule.callType.matches(direction)
                        && rule.simSlot.matches(simSlot)

                if (matches) {
                    Log.i(TAG, "Matched rule: $rule")
                    return rule.action
                }
            }

            throw IllegalArgumentException("Call does not match any rule")
        }
    }
}
