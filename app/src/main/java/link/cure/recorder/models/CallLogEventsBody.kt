package link.cure.recorder.models

import com.google.gson.annotations.SerializedName

data class CallLogEventsBody (
    @SerializedName("phone_call_log_events")
    val logs: List<CallLogEvent>,
)
