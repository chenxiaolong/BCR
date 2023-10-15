package com.chiller3.bcr.output

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.CallLog
import android.telecom.Call
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.database.getStringOrNull
import com.chiller3.bcr.extension.phoneNumber
import com.chiller3.bcr.findContactsByPhoneNumber
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

class CallMetadataCollector(
    private val context: Context,
    private val parentCall: Call,
) {
    // Call information
    private val callDetails = mutableMapOf<Call, Call.Details>()
    private val isConference = parentCall.details.hasProperty(Call.Details.PROPERTY_CONFERENCE)
    private lateinit var _callMetadata: CallMetadata
    val callMetadata: CallMetadata
        get() = synchronized(this) {
            _callMetadata
        }

    init {
        callDetails[parentCall] = parentCall.details
        if (isConference) {
            for (childCall in parentCall.children) {
                callDetails[childCall] = childCall.details
            }
        }

        update(false)
    }

    private fun getContactDisplayNameByNumber(number: PhoneNumber, allowManualLookup: Boolean): String? {
        // This is disabled until the very last filename update because it's synchronous.
        if (!allowManualLookup) {
            Log.d(TAG, "Manual contact lookup is disabled for this invocation")
            return null
        }

        if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS) !=
            PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Permissions not granted for looking up contacts")
            return null
        }

        Log.d(TAG, "Performing manual contact lookup")

        for (contact in findContactsByPhoneNumber(context, number.toString())) {
            Log.d(TAG, "Found contact display name via manual lookup")
            return contact.displayName
        }

        Log.d(TAG, "Contact not found via manual lookup")
        return null
    }

    private fun getContactDisplayName(details: Call.Details, allowManualLookup: Boolean): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val name = details.contactDisplayName
            if (name != null) {
                return name
            }
        }

        // In conference calls, the telephony framework sometimes doesn't return the contact display
        // name for every party in the call, so do the lookup ourselves. This is similar to what
        // InCallUI does, except it doesn't even try to look at contactDisplayName.
        if (isConference) {
            Log.w(TAG, "Contact display name missing in conference child call")
        }

        val number = details.phoneNumber
        if (number == null) {
            Log.w(TAG, "Cannot determine phone number from call")
            return null
        }

        return getContactDisplayNameByNumber(number, allowManualLookup)
    }

    private fun getCallLogDetails(
        parentDetails: Call.Details,
        allowBlockingCalls: Boolean,
    ): Pair<PhoneNumber?, String?> {
        // This is disabled until the very last filename update because it's synchronous.
        if (!allowBlockingCalls) {
            Log.d(TAG, "Call log lookup is disabled for this invocation")
            return Pair(null, null)
        }

        if (context.checkSelfPermission(Manifest.permission.READ_CALL_LOG) !=
            PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Permissions not granted for looking up call log")
            return Pair(null, null)
        }

        // The call log does not show all participants in a conference call
        if (isConference) {
            Log.w(TAG, "Skipping call log lookup due to conference call")
            return Pair(null, null)
        }

        val uri = CallLog.Calls.CONTENT_URI.buildUpon()
            .appendQueryParameter(CallLog.Calls.LIMIT_PARAM_KEY, "1")
            .build()

        // System.nanoTime() is more likely to be monotonic than Instant.now()
        val start = System.nanoTime()
        var attempt = 1

        var number: PhoneNumber? = null
        var name: String? = null

        while (true) {
            val now = System.nanoTime()
            if (now >= start + CALL_LOG_QUERY_TIMEOUT_NANOS) {
                break
            }

            val prefix = "[Attempt #$attempt @ ${(now - start) / 1_000_000}ms] "

            context.contentResolver.query(
                uri,
                arrayOf(CallLog.Calls.CACHED_NAME, CallLog.Calls.NUMBER),
                "${CallLog.Calls.DATE} = ?",
                arrayOf(parentDetails.creationTimeMillis.toString()),
                "${CallLog.Calls._ID} DESC",
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    Log.d(TAG, "${prefix}Found call log entry")

                    if (number == null) {
                        val index = cursor.getColumnIndex(CallLog.Calls.NUMBER)
                        if (index != -1) {
                            number = cursor.getStringOrNull(index)?.let {
                                Log.d(TAG, "${prefix}Found call log phone number")
                                PhoneNumber(it)
                            }
                        } else {
                            Log.d(TAG, "${prefix}Call log entry has no phone number")
                        }
                    }

                    if (name == null) {
                        val index = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME)
                        if (index != -1) {
                            name = cursor.getStringOrNull(index)?.let {
                                Log.d(TAG, "${prefix}Found call log cached name")
                                it
                            }
                        } else {
                            Log.d(TAG, "${prefix}Call log entry has no cached name")
                        }
                    }

                    Unit
                } else {
                    Log.d(TAG, "${prefix}Call log entry not found")
                }
            }

            attempt += 1

            if (number != null && name != null) {
                break
            }

            Thread.sleep(CALL_LOG_QUERY_RETRY_DELAY_MILLIS)
        }

        if (number != null && name != null) {
            Log.d(TAG, "Found all call log details after ${attempt - 1} attempts")
        } else {
            Log.d(TAG, "Incomplete call log details after all ${attempt - 1} attempts")
        }

        return Pair(number, name)
    }

    private fun computeMetadata(
        parentDetails: Call.Details,
        displayDetails: List<Call.Details>,
        allowBlockingCalls: Boolean,
    ): CallMetadata {
        val instant = Instant.ofEpochMilli(parentDetails.creationTimeMillis)
        val timestamp = ZonedDateTime.ofInstant(instant, ZoneId.systemDefault())

        // AOSP's telephony framework has internal documentation that specifies that the call
        // direction is meaningless for conference calls until enough participants hang up that it
        // becomes an emulated one-on-one call.
        val direction = if (isConference) {
            CallDirection.CONFERENCE
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            when (parentDetails.callDirection) {
                Call.Details.DIRECTION_INCOMING -> CallDirection.IN
                Call.Details.DIRECTION_OUTGOING -> CallDirection.OUT
                Call.Details.DIRECTION_UNKNOWN -> null
                else -> null
            }
        } else {
            null
        }

        var simCount: Int? = null
        var simSlot: Int? = null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
            && context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE)
                == PackageManager.PERMISSION_GRANTED
            && context.packageManager.hasSystemFeature(
                PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION)) {
            val subscriptionManager = context.getSystemService(SubscriptionManager::class.java)

            val telephonyManager = context.getSystemService(TelephonyManager::class.java)
            val subscriptionId = telephonyManager.getSubscriptionId(parentDetails.accountHandle)
            val subscriptionInfo = subscriptionManager.getActiveSubscriptionInfo(subscriptionId)

            simCount = subscriptionManager.activeSubscriptionInfoCount
            simSlot = subscriptionInfo.simSlotIndex + 1
        }

        val (callLogNumber, callLogName) = getCallLogDetails(parentDetails, allowBlockingCalls)

        var calls = displayDetails.map {
            CallPartyDetails(
                it.phoneNumber,
                it.callerDisplayName,
                getContactDisplayName(it, allowBlockingCalls),
            )
        }

        if (callLogNumber != null && !calls.any { it.phoneNumber == callLogNumber }) {
            Log.w(TAG, "Call log phone number does not match any call handle")
            Log.w(TAG, "Assuming call redirection and trusting call log instead")

            calls = listOf(
                CallPartyDetails(
                    callLogNumber,
                    null,
                    getContactDisplayNameByNumber(callLogNumber, allowBlockingCalls),
                )
            )
        }

        return CallMetadata(
            timestamp,
            direction,
            simCount,
            simSlot,
            callLogName,
            calls,
        )
    }

    fun update(allowBlockingCalls: Boolean): CallMetadata {
        val parentDetails = callDetails[parentCall]!!
        val displayDetails = if (isConference) {
            callDetails.entries.asSequence()
                .filter { it.key != parentCall }
                .map { it.value }
                .toList()
        } else {
            listOf(parentDetails)
        }

        val metadata = computeMetadata(parentDetails, displayDetails, allowBlockingCalls)
        synchronized(this) {
            _callMetadata = metadata
        }

        return metadata
    }

    /**
     * Update state with information from [details].
     *
     * @param call Either the parent call or a child of the parent (for conference calls)
     * @param details The updated call details belonging to [call]
     */
    fun updateCallDetails(call: Call, details: Call.Details): CallMetadata {
        if (call !== parentCall && call.parent !== parentCall) {
            throw IllegalStateException("Not the parent call nor one of its children: $call")
        }

        synchronized(this) {
            callDetails[call] = details

            return update(false)
        }
    }

    companion object {
        private val TAG = CallMetadataCollector::class.java.simpleName

        private const val CALL_LOG_QUERY_TIMEOUT_NANOS = 2_000_000_000L
        private const val CALL_LOG_QUERY_RETRY_DELAY_MILLIS = 100L
    }
}