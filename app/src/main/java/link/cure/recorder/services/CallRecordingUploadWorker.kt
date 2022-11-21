package link.cure.recorder.services

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.sentry.Sentry
import link.cure.recorder.data.api.MessagingAPIInterface
import link.cure.recorder.data.api.RetrofitBuilder
import link.cure.recorder.data.models.APIResponse
import link.cure.recorder.data.queue.CallRecordingUploadTaskQueue
import link.cure.recorder.utils.Preferences
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import java.io.File

class CallRecordingUploadWorker(context: Context, workerParameters: WorkerParameters) :
    CoroutineWorker(context, workerParameters) {
    companion object {
        private const val TAG = "CallRecordingUploadWorker"
        private val messagingAPIInterface: MessagingAPIInterface =
            RetrofitBuilder.messagingAPIInterface
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "doWork")
        val callRecordingUploadTaskQueue =
            CallRecordingUploadTaskQueue.getInstance(applicationContext)
        val prefs = Preferences(context = applicationContext)
        val token: String = "Token " + prefs.userToken
        var count = callRecordingUploadTaskQueue.size()
        while (count > 0) {
            val event = callRecordingUploadTaskQueue.peek()
            Log.d(TAG, "Event be like :- $event")
            count--
            callRecordingUploadTaskQueue.remove()
            if (event == null) {
                continue
            }
            try {
                Log.d(TAG, "making Request")
                val recording = File(Uri.parse(event.fileURI).path!!)
                val filePart = MultipartBody.Part.createFormData(
                    "recording_file",
                    recording.name,
                    RequestBody.create(MediaType.parse("audio/ogg"), recording)
                )
                val creationTimestamp = MultipartBody.Part.createFormData(
                    "creation_timestamp",
                    RecorderThread.timestampFromFilename(recording.name) ?: "unknown"
                )
                val direction = MultipartBody.Part.createFormData(
                    "direction",
                    RecorderThread.directionFromFilename(recording.name) ?: "unknown"
                )
                val phone = MultipartBody.Part.createFormData(
                    "user_phone",
                    RecorderThread.phoneFromFilename(recording.name) ?: "unknown"
                )
                Log.d(
                    TAG,
                    "${RecorderThread.timestampFromFilename(recording.name)} " +
                            "${RecorderThread.directionFromFilename(recording.name)} " +
                            "${RecorderThread.phoneFromFilename(recording.name)}"
                )

                val response: APIResponse? =
                    messagingAPIInterface.postRecordingFile(
                        token = token,
                        file = filePart,
                        creationTimestamp = creationTimestamp,
                        direction = direction,
                        phone = phone
                    )

                Log.d(TAG, response.toString())
            } catch (exception: Exception) {
                if (event.attempt < 5) {
                    event.attempt = event.attempt + 1
                    callRecordingUploadTaskQueue.add(event)
                } else {
                    Sentry.captureException(exception) {
                        it.setContexts("event", event.toString())
                        it.setContexts("count", count)
                    }
                }
            }
        }
        Log.d(TAG, "returning")
        return Result.success()
    }

}