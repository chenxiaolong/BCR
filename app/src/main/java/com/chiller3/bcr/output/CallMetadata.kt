/*
 * SPDX-FileCopyrightText: 2023-2025 Andrew Gunnerson
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.chiller3.bcr.output

import android.content.Context
import com.chiller3.bcr.format.FormatParamInfo
import com.chiller3.bcr.format.NoParamInfo
import com.chiller3.bcr.format.RangedParamInfo
import com.chiller3.bcr.format.RangedParamType
import com.google.i18n.phonenumbers.PhoneNumberUtil
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@Serializable
enum class CallDirection {
    @SerialName("in")
    IN,
    @SerialName("out")
    OUT,
    @SerialName("conference")
    CONFERENCE,
}

data class CallPartyDetails(
    val phoneNumber: PhoneNumber?,
    val callerName: String?,
    val contactName: String?,
)

@Serializable
data class CallPartyDetailsJson(
    @SerialName("phone_number")
    val phoneNumber: String?,
    @SerialName("phone_number_formatted")
    val phoneNumberFormatted: String?,
    @SerialName("caller_name")
    val callerName: String?,
    @SerialName("contact_name")
    val contactName: String?,
) {
    constructor(context: Context, details: CallPartyDetails) : this(
        phoneNumber = details.phoneNumber?.toString(),
        phoneNumberFormatted = details.phoneNumber
            ?.format(context, PhoneNumberUtil.PhoneNumberFormat.NATIONAL),
        callerName = details.callerName,
        contactName = details.contactName,
    )
}

data class CallMetadata(
    val timestamp: ZonedDateTime,
    val direction: CallDirection?,
    val simCount: Int?,
    val simSlot: Int?,
    val callLogName: String?,
    val calls: List<CallPartyDetails>,
)

@Serializable
data class CallMetadataJson(
    @SerialName("timestamp_unix_ms")
    val timestampUnixMs: Long,
    val timestamp: String,
    val direction: CallDirection?,
    @SerialName("sim_slot")
    val simSlot: Int?,
    @SerialName("call_log_name")
    val callLogName: String?,
    val calls: List<CallPartyDetailsJson>,
    val output: OutputJson,
) {
    constructor(context: Context, metadata: CallMetadata, output: OutputJson) : this(
        timestampUnixMs = metadata.timestamp.toInstant().toEpochMilli(),
        timestamp = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(metadata.timestamp),
        direction = metadata.direction,
        simSlot = metadata.simSlot,
        callLogName = metadata.callLogName,
        calls = metadata.calls.map { CallPartyDetailsJson(context, it) },
        output = output,
    )
}

@Serializable
enum class ParameterType {
    @SerialName("none")
    NONE,
    @SerialName("compression_level")
    COMPRESSION_LEVEL,
    @SerialName("bitrate")
    BITRATE,
    ;

    companion object {
        fun fromParamInfo(info: FormatParamInfo): ParameterType = when (info) {
            NoParamInfo -> NONE
            is RangedParamInfo -> when (info.type) {
                RangedParamType.CompressionLevel -> COMPRESSION_LEVEL
                RangedParamType.Bitrate -> BITRATE
            }
        }
    }
}

@Serializable
data class FormatJson(
    val type: String,
    @SerialName("mime_type_container")
    val mimeTypeContainer: String,
    @SerialName("mime_type_audio")
    val mimeTypeAudio: String,
    @SerialName("parameter_type")
    val parameterType: ParameterType,
    val parameter: UInt,
)

@Serializable
data class RecordingJson(
    @SerialName("frames_total")
    val framesTotal: Long,
    @SerialName("frames_encoded")
    val framesEncoded: Long,
    @SerialName("sample_rate")
    val sampleRate: Int,
    @SerialName("channel_count")
    val channelCount: Int,
    @SerialName("duration_secs_wall")
    val durationSecsWall: Double,
    @SerialName("duration_secs_total")
    val durationSecsTotal: Double,
    @SerialName("duration_secs_encoded")
    val durationSecsEncoded: Double,
    @SerialName("buffer_frames")
    val bufferFrames: Long,
    @SerialName("buffer_overruns")
    val bufferOverruns: Int,
    @SerialName("was_ever_paused")
    val wasEverPaused: Boolean,
    @SerialName("was_ever_holding")
    val wasEverHolding: Boolean,
)

@Serializable
data class OutputJson(
    val format: FormatJson,
    val recording: RecordingJson?,
)
