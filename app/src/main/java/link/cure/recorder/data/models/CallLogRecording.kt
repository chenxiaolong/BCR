package link.cure.recorder.data.models

import com.google.gson.annotations.SerializedName

data class CallLogRecording(
    @SerializedName("file_uri")
    val fileURI: String,
    @SerializedName("attempt")
    var attempt: Int
)
