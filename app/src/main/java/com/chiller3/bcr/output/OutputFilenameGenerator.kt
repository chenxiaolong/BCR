package com.chiller3.bcr.output

import android.content.Context
import android.util.Log
import com.chiller3.bcr.Preferences
import com.chiller3.bcr.template.Template
import java.text.ParsePosition
import java.time.DateTimeException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatterBuilder
import java.time.format.DateTimeParseException
import java.time.format.SignStyle
import java.time.temporal.ChronoField
import java.time.temporal.Temporal

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
) {
    // Templates
    private val filenameTemplate = Preferences(context).filenameTemplate
        ?: Preferences.DEFAULT_FILENAME_TEMPLATE
    private val dateVarLocations = filenameTemplate.findVariableRef(DATE_VAR)?.second

    // Timestamps
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

    init {
        Log.i(TAG, "Filename template: $filenameTemplate")
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

    private fun formatPhoneNumber(number: PhoneNumber, arg: String?): String? {
        return when (arg) {
            // Default is already E.164
            null, "E.164" -> number.toString()
            "digits_only" -> number.format(context, PhoneNumber.Format.DIGITS_ONLY)
            "formatted" -> number.format(context, PhoneNumber.Format.COUNTRY_SPECIFIC)
                // Don't fail since this isn't the user's fault
                ?: number.toString()
            else -> {
                Log.w(TAG, "Unknown phone_number format arg: $arg")
                null
            }
        }
    }

    private fun evaluateVars(
        name: String,
        arg: String?,
        metadata: CallMetadata,
    ): String? {
        when (name) {
            "date" -> {
                if (arg != null) {
                    Log.d(TAG, "Using custom datetime pattern: $arg")

                    synchronized(this) {
                        try {
                            formatter = DateTimeFormatterBuilder()
                                .appendPattern(arg)
                                .toFormatter()
                        } catch (e: Exception) {
                            Log.w(TAG, "Invalid custom datetime pattern: $arg; using default", e)
                        }
                    }
                }

                return formatter.format(metadata.timestamp)
            }
            "direction" -> return metadata.direction?.toString()
            "sim_slot" -> {
                // Only append SIM slot ID if the device has multiple active SIMs
                if (arg == "always" || (metadata.simCount != null && metadata.simCount > 1)) {
                    return metadata.simSlot?.toString()
                }
            }
            "phone_number" -> {
                val joined = metadata.calls.asSequence()
                    .map { it.phoneNumber?.let { number -> formatPhoneNumber(number, arg) } }
                    .filterNotNull()
                    .joinToString(",")
                if (joined.isNotEmpty()) {
                    addRedaction(joined, "<phone number(s)>")

                    return joined
                }
            }
            "caller_name" -> {
                val joined = metadata.calls.asSequence()
                    .map { it.callerName?.trim() }
                    .filter { n -> !n.isNullOrEmpty() }
                    .joinToString(",")
                if (joined.isNotEmpty()) {
                    addRedaction(joined, "<caller name(s)>")

                    return joined
                }
            }
            "contact_name" -> {
                val joined = metadata.calls.asSequence()
                    .map { it.contactName?.trim() }
                    .filter { n -> !n.isNullOrEmpty() }
                    .joinToString(",")
                if (joined.isNotEmpty()) {
                    addRedaction(joined, "<contact name(s)>")

                    return joined
                }
            }
            "call_log_name" -> {
                val cachedName = metadata.callLogName?.trim()
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

    private fun generate(template: Template, metadata: CallMetadata): OutputPath {
        val newPathString = template.evaluate { name, arg ->
            val result = evaluateVars(name, arg, metadata)?.trim()

            // Directories are allowed in the template, but not in a variable's value unless it's
            // part of the timestamp because that's fully user controlled.
            when (name) {
                DATE_VAR -> result
                else -> result?.replace('/', '_')
            }
        }
        val newPath = splitPath(newPathString)

        return OutputPath(newPath, redactor.redact(newPath))
    }

    fun generate(metadata: CallMetadata): OutputPath {
        val path = try {
            generate(filenameTemplate, metadata)
        } catch (e: Exception) {
            if (filenameTemplate === Preferences.DEFAULT_FILENAME_TEMPLATE) {
                throw e
            } else {
                Log.w(TAG, "Failed to evaluate custom template: $filenameTemplate", e)
                generate(Preferences.DEFAULT_FILENAME_TEMPLATE, metadata)
            }
        }

        Log.i(TAG, "Generated filename: $path")

        return path
    }

    private fun parseTimestamp(input: String, startPos: Int): Temporal? {
        val pos = ParsePosition(startPos)
        val parsed = synchronized(this) {
            formatter.parse(input, pos)
        }

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
                    "sim_slot" -> {
                        if (varRef.arg !in arrayOf(null, "always")) {
                            errors.add(ValidationError(
                                ValidationErrorType.INVALID_ARGUMENT, varRef))
                        }
                    }
                    "direction", "caller_name", "contact_name", "call_log_name" -> {
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