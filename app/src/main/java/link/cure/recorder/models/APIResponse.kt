package link.cure.recorder.models

import com.google.gson.annotations.SerializedName

data class APIResponse(
    @SerializedName("success")
    val success: Boolean,
    @SerializedName("message")
    val message: String,
    @SerializedName("body")
    val body: HashMap<String, Any>?,
)