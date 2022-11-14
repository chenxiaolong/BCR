package link.cure.recorder.models

import com.google.gson.annotations.SerializedName

data class CallLogEvent(
    @SerializedName("user_phone")
    val phone: String,
    @SerializedName("direction")
    val direction: String,
    @SerializedName("creation_timestamp")
    val creationTimestamp: Long,
    @SerializedName("timestamp")
    val timestamp: Long,
    @SerializedName("type")
    val type: String,
)