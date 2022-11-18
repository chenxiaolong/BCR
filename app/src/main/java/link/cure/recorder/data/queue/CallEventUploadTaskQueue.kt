package link.cure.recorder.data.queue

import android.content.Context
import android.util.Log
import com.google.gson.GsonBuilder
import com.squareup.tape.FileObjectQueue
import com.squareup.tape.FileObjectQueue.Converter
import com.squareup.tape.ObjectQueue
import link.cure.recorder.data.models.CallLogEvent
import java.io.File
import java.io.IOException

class CallEventUploadTaskQueue private constructor(
    private var delegate: FileObjectQueue<CallLogEvent?>
) : ObjectQueue<CallLogEvent?> {

    companion object {
        private const val TAG = "CallEventUploadTaskQueue"
        private const val FILE_NAME = "call_event_upload_task_queue"

        private var instance: CallEventUploadTaskQueue? = null

        fun getInstance(context: Context): CallEventUploadTaskQueue =
            instance ?: create(context)

        private fun create(context: Context): CallEventUploadTaskQueue {
            val gsonConverter: Converter<CallLogEvent> =
                GsonConverter(GsonBuilder().create(), CallLogEvent::class.java)
            val queueFile =
                File(context.getExternalFilesDir(null)!!, FILE_NAME)
            val delegate: FileObjectQueue<CallLogEvent?>
            try {
                delegate = FileObjectQueue(queueFile, gsonConverter)
            } catch (exception: IOException) {
                Log.e(TAG, "Queue creation failed!", exception)
                throw exception
            }
            return CallEventUploadTaskQueue(delegate)
        }

    }

    override fun size(): Int {
        Log.d(TAG,"size")
        return delegate.size()
    }

    override fun add(entry: CallLogEvent?) {
        Log.d(TAG,"add")
        delegate.add(entry!!)
    }

    override fun peek(): CallLogEvent? {
        Log.d(TAG,"peek")
        return delegate.peek()
    }

    override fun remove() {
        Log.d(TAG,"remove")
        delegate.remove()
    }

    override fun setListener(listener: ObjectQueue.Listener<CallLogEvent?>?) {
        delegate.setListener(listener)
    }
}