package com.chiller3.bcr.output

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.CallLog
import android.telecom.Call
import android.telephony.PhoneNumberUtils
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.database.getStringOrNull
import com.chiller3.bcr.Preferences
import com.chiller3.bcr.extension.phoneNumber
import com.chiller3.bcr.findContactsByPhoneNumber
import com.chiller3.bcr.template.Template
import java.text.ParsePosition
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatterBuilder
import java.time.format.DateTimeParseException
import java.time.format.SignStyle
import java.time.temporal.ChronoField
import java.time.temporal.Temporal
import java.util.Locale

data class OutputPath(
    val value: List<String>,
    val redacted: String,
) {
    val unredacted = value.joinToString("/")

    override fun toString() = redacted
}

/**
 * Helper class for determining a recording's output filename based on information from a call.
 */
class OutputFilenameGenerator(
    private val context: Context,
    private val parentCall: Call,
) {
    // Templates
    private val filenameTemplate = Preferences(context).filenameTemplate
        ?: Preferences.DEFAULT_FILENAME_TEMPLATE
    private val dateVarLocations = filenameTemplate.findVariableRef(DATE_VAR)?.second

    // Call information
    private val callDetails = mutableMapOf<Call, Call.Details>()
    private val isConference = parentCall.details.hasProperty(Call.Details.PROPERTY_CONFERENCE)

    // Timestamps
    private lateinit var _callTimestamp: ZonedDateTime
    val callTimestamp: ZonedDateTime
        get() = synchronized(this) {
            _callTimestamp
        }
    private var formatter = FORMATTER

    // Redactions
    private val redactions = HashMap<String, String>()
    private val redactionsSorted = mutableListOf<Pair<String, String>>()
    val redactor = object : OutputDirUtils.Redactor {
        override fun redact(msg: String): String {
            synchronized(this@OutputFilenameGenerator) {
                var result = msg

                for ((source, target) in redactionsSorted) {
                    result = result.replace(source, target)
                }

                return result
            }
        }
    }

    private lateinit var _path: OutputPath
    val path: OutputPath
        get() = synchronized(this) {
            _path
        }

    init {
        Log.i(TAG, "Filename template: $filenameTemplate")

        callDetails[parentCall] = parentCall.details
        if (isConference) {
            for (childCall in parentCall.children) {
                callDetails[childCall] = childCall.details
            }
        }

        update(false)
    }

    /**
     * Update [path] with information from [details].
     *
     * @param call Either the parent call or a child of the parent (for conference calls)
     * @param details The updated call details belonging to [call]
     */
    fun updateCallDetails(call: Call, details: Call.Details): OutputPath {
        if (call !== parentCall && call.parent !== parentCall) {
            throw IllegalStateException("Not the parent call nor one of its children: $call")
        }

        synchronized(this) {
            callDetails[call] = details

            return update(false)
        }
    }

    private fun addRedaction(source: String, target: String) {
        synchronized(this) {
            redactions[source] = target

            // Keyword-based redaction with arbitrary filenames can never be 100% foolproof, but we
            // can improve the odds by replacing the longest strings first
            redactionsSorted.clear()
            redactions.entries
                .mapTo(redactionsSorted) { it.key to it.value }
                .sortByDescending { it.first.length }
        }
    }

    /**
     * Get the current ISO country code for phone number formatting.
     */
    private fun getIsoCountryCode(): String? {
        val telephonyManager = context.getSystemService(TelephonyManager::class.java)
        var result: String? = null

        if (telephonyManager.phoneType == TelephonyManager.PHONE_TYPE_GSM) {
            result = telephonyManager.networkCountryIso
        }
        if (result.isNullOrEmpty()) {
            result = telephonyManager.simCountryIso
        }
        if (result.isNullOrEmpty()) {
            result = Locale.getDefault().country
        }
        if (result.isNullOrEmpty()) {
            return null
        }
        return result.uppercase()
    }

    private fun getPhoneNumber(details: Call.Details, arg: String?): String? {
        val number = details.phoneNumber ?: return null

        when (arg) {
            null, "E.164" -> {
                // Default is already E.164
                return number
            }
            "digits_only" -> {
                return number.filter { Character.digit(it, 10) != -1 }
            }
            "formatted" -> {
                val country = getIsoCountryCode()
                if (country == null) {
                    Log.w(TAG, "Failed to detect country")
                    return number
                }

                val formatted = PhoneNumberUtils.formatNumber(number, country)
                if (formatted == null) {
                    Log.w(TAG, "Phone number cannot be formatted for country $country")
                    // Don't fail since this isn't the user's fault
                    return number
                }

                return formatted
            }
            else -> {
                Log.w(TAG, "Unknown phone_number format arg: $arg")
                return null
            }
        }
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

        val number = getPhoneNumber(details, null)
        if (number == null) {
            Log.w(TAG, "Cannot determine phone number from call")
            return null
        }

        for (contact in findContactsByPhoneNumber(context, number)) {
            Log.d(TAG, "Found contact display name via manual lookup")
            return contact.displayName
        }

        Log.d(TAG, "Contact not found via manual lookup")
        return null
    }

    private fun getCallLogCachedName(
        parentDetails: Call.Details,
        allowBlockingCalls: Boolean,
    ): String? {
        // This is disabled until the very last filename update because it's synchronous.
        if (!allowBlockingCalls) {
            Log.d(TAG, "Call log lookup is disabled for this invocation")
            return null
        }

        if (context.checkSelfPermission(Manifest.permission.READ_CALL_LOG) !=
            PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Permissions not granted for looking up call log")
            return null
        }

        // The call log does not show all participants in a conference call
        if (isConference) {
            Log.w(TAG, "Skipping call log lookup due to conference call")
            return null
        }

        val uri = CallLog.Calls.CONTENT_URI.buildUpon()
            .appendQueryParameter(CallLog.Calls.LIMIT_PARAM_KEY, "1")
            .build()

        // System.nanoTime() is more likely to be monotonic than Instant.now()
        val start = System.nanoTime()
        var attempt = 1

        while (true) {
            val now = System.nanoTime()
            if (now >= start + CALL_LOG_QUERY_TIMEOUT_NANOS) {
                break
            }

            val prefix = "[Attempt #$attempt @ ${(now - start) / 1_000_000}ms] "

            context.contentResolver.query(
                uri,
                arrayOf(CallLog.Calls.CACHED_NAME),
                "${CallLog.Calls.DATE} = ?",
                arrayOf(parentDetails.creationTimeMillis.toString()),
                "${CallLog.Calls._ID} DESC",
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    Log.d(TAG, "${prefix}Found call log entry")

                    val index = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME)
                    if (index != -1) {
                        val name = cursor.getStringOrNull(index)
                        if (name != null) {
                            Log.d(TAG, "${prefix}Found call log cached name")
                            return name
                        }
                    }

                    Log.d(TAG, "${prefix}Call log entry has no cached name")
                } else {
                    Log.d(TAG, "${prefix}Call log entry not found")
                }
            }

            attempt += 1
            Thread.sleep(CALL_LOG_QUERY_RETRY_DELAY_MILLIS)
        }

        Log.d(TAG, "Call log cached name not found after all ${attempt - 1} attempts")
        return null
    }

    private fun evaluateVars(
        name: String,
        arg: String?,
        parentDetails: Call.Details,
        displayDetails: List<Call.Details>,
        allowBlockingCalls: Boolean,
    ): String? {
        when (name) {
            "date" -> {
                val instant = Instant.ofEpochMilli(parentDetails.creationTimeMillis)
                _callTimestamp = ZonedDateTime.ofInstant(instant, ZoneId.systemDefault())

                if (arg != null) {
                    Log.d(TAG, "Using custom datetime pattern: $arg")

                    try {
                        formatter = DateTimeFormatterBuilder()
                            .appendPattern(arg)
                            .toFormatter()
                    } catch (e: Exception) {
                        Log.w(TAG, "Invalid custom datetime pattern: $arg; using default", e)
                    }
                }

                return formatter.format(_callTimestamp)
            }
            "direction" -> {
                // AOSP's telephony framework has internal documentation that specifies that the
                // call direction is meaningless for conference calls until enough participants hang
                // up that it becomes an emulated one-on-one call.
                if (isConference) {
                    return "conference"
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    when (parentDetails.callDirection) {
                        Call.Details.DIRECTION_INCOMING -> return "in"
                        Call.Details.DIRECTION_OUTGOING -> return "out"
                        Call.Details.DIRECTION_UNKNOWN -> {}
                    }
                }
            }
            "sim_slot" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                    && context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE)
                    == PackageManager.PERMISSION_GRANTED
                    && context.packageManager.hasSystemFeature(
                        PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION)) {
                    val subscriptionManager = context.getSystemService(SubscriptionManager::class.java)

                    // Only append SIM slot ID if the device has multiple active SIMs
                    if (subscriptionManager.activeSubscriptionInfoCount > 1) {
                        val telephonyManager = context.getSystemService(TelephonyManager::class.java)
                        val subscriptionId = telephonyManager.getSubscriptionId(parentDetails.accountHandle)
                        val subscriptionInfo = subscriptionManager.getActiveSubscriptionInfo(subscriptionId)

                        return "${subscriptionInfo.simSlotIndex + 1}"
                    }
                }
            }
            "phone_number" -> {
                val joined = displayDetails.asSequence()
                    .map { d -> getPhoneNumber(d, arg) }
                    .filterNotNull()
                    .joinToString(",")
                if (joined.isNotEmpty()) {
                    addRedaction(joined, if (isConference) {
                        "<conference phone numbers>"
                    } else {
                        "<phone number>"
                    })

                    return joined
                }
            }
            "caller_name" -> {
                val joined = displayDetails.asSequence()
                    .map { d -> d.callerDisplayName?.trim() }
                    .filter { n -> !n.isNullOrEmpty() }
                    .joinToString(",")
                if (joined.isNotEmpty()) {
                    addRedaction(joined, if (isConference) {
                        "<conference caller names>"
                    } else {
                        "<caller name>"
                    })

                    return joined
                }
            }
            "contact_name" -> {
                val joined = displayDetails.asSequence()
                    .map { d -> getContactDisplayName(d, allowBlockingCalls)?.trim() }
                    .filter { n -> !n.isNullOrEmpty() }
                    .joinToString(",")
                if (joined.isNotEmpty()) {
                    addRedaction(joined, if (isConference) {
                        "<conference contact names>"
                    } else {
                        "<contact name>"
                    })

                    return joined
                }
            }
            "call_log_name" -> {
                val cachedName = getCallLogCachedName(parentDetails, allowBlockingCalls)?.trim()
                if (!cachedName.isNullOrEmpty()) {
                    addRedaction(cachedName, "<call log name>")
                    return cachedName
                }
            }
            else -> {
                Log.w(TAG, "Unknown filename template variable: $name")
            }
        }

        return null
    }

    private fun generate(template: Template, allowBlockingCalls: Boolean): OutputPath {
        synchronized(this) {
            val parentDetails = callDetails[parentCall]!!
            val displayDetails = if (isConference) {
                callDetails.entries.asSequence()
                    .filter { it.key != parentCall }
                    .map { it.value }
                    .toList()
            } else {
                listOf(parentDetails)
            }

            val newPathString = template.evaluate { name, arg ->
                val result = evaluateVars(
                    name, arg, parentDetails, displayDetails, allowBlockingCalls)?.trim()

                // Directories are allowed in the template, but not in a variable's value unless
                // it's part of the timestamp because that's fully user controlled.
                when (name) {
                    DATE_VAR -> result
                    else -> result?.replace('/', '_')
                }
            }
            val newPath = splitPath(newPathString)

            return OutputPath(newPath, redactor.redact(newPath))
        }
    }

    fun update(allowBlockingCalls: Boolean): OutputPath {
        synchronized(this) {
            _path = try {
                generate(filenameTemplate, allowBlockingCalls)
            } catch (e: Exception) {
                if (filenameTemplate === Preferences.DEFAULT_FILENAME_TEMPLATE) {
                    throw e
                } else {
                    Log.w(TAG, "Failed to evaluate custom template: $filenameTemplate", e)
                    generate(Preferences.DEFAULT_FILENAME_TEMPLATE, allowBlockingCalls)
                }
            }

            Log.i(TAG, "Updated filename: $_path")

            return _path
        }
    }

    private fun parseTimestamp(input: String, startPos: Int): Temporal? {
        val pos = ParsePosition(startPos)
        val parsed = formatter.parse(input, pos)

        return try {
            parsed.query(ZonedDateTime::from)
        } catch (e: DateTimeException) {
            try {
                // A custom pattern might not specify the time zone
                parsed.query(LocalDateTime::from)
            } catch (e: DateTimeException) {
                // A custom pattern might only specify a date with no time
                parsed.query(LocalDate::from).atStartOfDay()
            }
        }
    }

    private fun parseTimestamp(input: String): Temporal? {
        if (dateVarLocations != null) {
            for (location in dateVarLocations) {
                when (location) {
                    is Template.VariableRefLocation.AfterPrefix -> {
                        var searchIndex = 0

                        while (true) {
                            val literalPos = input.indexOf(location.literal, searchIndex)
                            if (literalPos < 0) {
                                break
                            }

                            val timestampPos = literalPos + location.literal.length

                            try {
                                return parseTimestamp(input, timestampPos)
                            } catch (e: DateTimeParseException) {
                                // Ignore
                            } catch (e: DateTimeException) {
                                Log.w(TAG, "Unexpected non-DateTimeParseException error", e)
                            }

                            if (location.atStart) {
                                break
                            } else {
                                searchIndex = timestampPos
                            }
                        }
                    }
                    Template.VariableRefLocation.Arbitrary -> {
                        Log.d(TAG, "Date might be at an arbitrary location")
                    }
                }
            }
        }

        return null
    }

    fun parseTimestampFromPath(path: List<String>): Temporal? {
        val pathString = path.joinToString("/")
        val redacted = redactTruncate(pathString)
        val timestamp = parseTimestamp(pathString)

        Log.d(TAG, "Parsed $timestamp from $redacted")

        return timestamp
    }

    enum class ValidationErrorType {
        UNKNOWN_VARIABLE,
        HAS_ARGUMENT,
        INVALID_ARGUMENT,
    }

    data class ValidationError(
        val type: ValidationErrorType,
        val varRef: Template.VariableRef,
    )

    companion object {
        private val TAG = OutputFilenameGenerator::class.java.simpleName

        const val DATE_VAR = "date"

        /**
         * List of supported variables.
         *
         * Keep these in the same order as in [Preferences.DEFAULT_FILENAME_TEMPLATE].
         */
        val KNOWN_VARS = arrayOf(
            DATE_VAR,
            "direction",
            "sim_slot",
            "phone_number",
            "contact_name",
            "caller_name",
            "call_log_name",
        )

        // Eg. 20220429_180249.123-0400
        private val FORMATTER = DateTimeFormatterBuilder()
            .appendValue(ChronoField.YEAR, 4, 10, SignStyle.EXCEEDS_PAD)
            .appendValue(ChronoField.MONTH_OF_YEAR, 2)
            .appendValue(ChronoField.DAY_OF_MONTH, 2)
            .appendLiteral('_')
            .appendValue(ChronoField.HOUR_OF_DAY, 2)
            .appendValue(ChronoField.MINUTE_OF_HOUR, 2)
            .appendValue(ChronoField.SECOND_OF_MINUTE, 2)
            .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true)
            .appendOffset("+HHMMss", "+0000")
            .toFormatter()

        private const val CALL_LOG_QUERY_TIMEOUT_NANOS = 2_000_000_000L
        private const val CALL_LOG_QUERY_RETRY_DELAY_MILLIS = 100L

        private fun splitPath(pathString: String) = pathString
            .splitToSequence('/')
            .filter { it.isNotEmpty() && it != "." && it != ".." }
            .toList()

        fun redactTruncate(msg: String): String = buildString {
            val n = 2

            if (msg.length > 2 * n) {
                append(msg.substring(0, n))
            }
            append("<...>")
            if (msg.length > 2 * n) {
                append(msg.substring(msg.length - n))
            }
        }

        fun validate(template: Template): List<ValidationError> {
            val errors = mutableListOf<ValidationError>()

            for (varRef in template.findAllVariableRefs()) {
                when (varRef.name) {
                    "date" -> {
                        if (varRef.arg != null) {
                            try {
                                DateTimeFormatterBuilder()
                                    .appendPattern(varRef.arg)
                                    .toFormatter()
                            } catch (e: Exception) {
                                errors.add(ValidationError(
                                    ValidationErrorType.INVALID_ARGUMENT, varRef))
                            }
                        }
                    }
                    "phone_number" -> {
                        if (varRef.arg !in arrayOf(null, "E.164", "digits_only", "formatted")) {
                            errors.add(ValidationError(
                                ValidationErrorType.INVALID_ARGUMENT, varRef))
                        }
                    }
                    "direction", "sim_slot", "caller_name", "contact_name", "call_log_name" -> {
                        if (varRef.arg != null) {
                            errors.add(ValidationError(
                                ValidationErrorType.HAS_ARGUMENT, varRef))
                        }
                    }
                    else -> errors.add(ValidationError(
                        ValidationErrorType.UNKNOWN_VARIABLE, varRef))
                }
            }

            return errors
        }
    }
}