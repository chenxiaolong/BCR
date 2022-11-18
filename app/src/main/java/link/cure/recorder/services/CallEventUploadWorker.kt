package link.cure.recorder.services

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.sentry.Sentry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import link.cure.recorder.data.api.MessagingAPIInterface
import link.cure.recorder.data.api.RetrofitBuilder
import link.cure.recorder.data.models.APIResponse
import link.cure.recorder.data.models.CallLogEvent
import link.cure.recorder.data.models.CallLogEventsBody
import link.cure.recorder.data.queue.CallEventUploadTaskQueue
import link.cure.recorder.utils.Preferences

class CallEventUploadWorker(context: Context, workerParameters: WorkerParameters) :
    CoroutineWorker(context, workerParameters) {

    companion object {
        private const val TAG = "CallEventUploadWorker"
        private val messagingAPIInterface: MessagingAPIInterface =
            RetrofitBuilder.messagingAPIInterface
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "doWork")
        val callEventUploadTaskQueue: CallEventUploadTaskQueue =
            CallEventUploadTaskQueue.getInstance(context = applicationContext)
        val prefs = Preferences(context = applicationContext)
        val token: String = "Token " + prefs.userToken
        var count = callEventUploadTaskQueue.size()
        while (count > 0) {
            val event: CallLogEvent? = callEventUploadTaskQueue.peek()
            Log.d(TAG, "Event be like :- $event")
            count--
            callEventUploadTaskQueue.remove()
            if (event == null) {
                continue
            }
            try {
                Log.d(TAG, "Syncing call log")
                val response: APIResponse? =
                    messagingAPIInterface.postCallLogs(
                        token = token,
                        body = CallLogEventsBody(
                            logs = arrayListOf(
                                event
                            )
                        )
                    )
                Log.d(TAG, response.toString())
            } catch (e: Exception) {
                Log.e(TAG,"Got an Exception while syncing call events!",e)
                if (event.attempt < 5) {
                    event.attempt = event.attempt + 1
                    callEventUploadTaskQueue.add(event)
                } else {
                    Sentry.captureException(e) {
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