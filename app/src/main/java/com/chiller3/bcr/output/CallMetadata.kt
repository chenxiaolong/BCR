package com.chiller3.bcr.output

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

enum class CallDirection {
    IN,
    OUT,
    CONFERENCE,
    ;

    override fun toString(): String = when (this) {
        IN -> "in"
        OUT -> "out"
        CONFERENCE -> "conference"
    }
}

data class CallPartyDetails(
    val phoneNumber: PhoneNumber?,
    val callerName: String?,
    val contactName: String?,
) {
    fun toJson(context: Context) = JSONObject().apply {
        put("phone_number", phoneNumber?.toString() ?: JSONObject.NULL)
        put("phone_number_formatted",
            phoneNumber?.format(context, PhoneNumber.Format.COUNTRY_SPECIFIC) ?: JSONObject.NULL)
        put("caller_name", callerName ?: JSONObject.NULL)
        put("contact_name", contactName ?: JSONObject.NULL)
    }
}

data class CallMetadata(
    val timestamp: ZonedDateTime,
    val direction: CallDirection?,
    val simCount: Int?,
    val simSlot: Int?,
    val callLogName: String?,
    val calls: List<CallPartyDetails>,
) {
    fun toJson(context: Context) = JSONObject().apply {
        put("timestamp_unix_ms", timestamp.toInstant().toEpochMilli())
        put("timestamp", DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(timestamp))
        put("direction", direction?.toString() ?: JSONObject.NULL)
        put("sim_slot", simSlot ?: JSONObject.NULL)
        put("call_log_name", callLogName ?: JSONObject.NULL)
        put("calls", JSONArray(calls.map { it.toJson(context) }))
    }
}