package link.cure.recorder.api

import link.cure.recorder.models.APIResponse
import link.cure.recorder.models.CallLogEventsBody
import okhttp3.MultipartBody
import retrofit2.http.*


interface MessagingAPIInterface {
    @POST("phone_call_log_events")
    suspend fun postCallLogs(
        @Header("Authorization") token: String,
        @Body body: CallLogEventsBody
    ): APIResponse?

    @Multipart
    @POST("upload_phone_call_log_recording")
    suspend fun postRecordingFile(
        @Header("Authorization") token: String,
        @Part
        file: MultipartBody.Part,
        @Part
        creationTimestamp: MultipartBody.Part
    ): APIResponse?
}