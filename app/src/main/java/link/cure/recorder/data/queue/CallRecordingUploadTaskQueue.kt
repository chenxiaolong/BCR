package link.cure.recorder.data.queue

import android.content.Context
import android.util.Log
import com.google.gson.GsonBuilder
import com.squareup.tape.FileObjectQueue
import com.squareup.tape.FileObjectQueue.Converter
import com.squareup.tape.ObjectQueue
import link.cure.recorder.data.models.CallLogRecording
import java.io.File
import java.io.IOException

class CallRecordingUploadTaskQueue private constructor(
    private var delegate: FileObjectQueue<CallLogRecording?>
) : ObjectQueue<CallLogRecording?> {

    companion object {
        private const val TAG = "CallRecordingUploadTaskQueue"
        private const val FILE_NAME = "call_recording_upload_task_queue"

        private var instance: CallRecordingUploadTaskQueue? = null

        fun getInstance(context: Context): CallRecordingUploadTaskQueue =
            instance ?: create(context)

        private fun create(context: Context): CallRecordingUploadTaskQueue {
            val gsonConverter: Converter<CallLogRecording> =
                GsonConverter(GsonBuilder().create(), CallLogRecording::class.java)
            val queueFile =
                File(context.getExternalFilesDir(null)!!, FILE_NAME)
            val delegate: FileObjectQueue<CallLogRecording?>
            try {
                delegate = FileObjectQueue(queueFile, gsonConverter)
            } catch (exception: IOException) {
                Log.e(TAG, "Queue creation failed!", exception)
                throw exception
            }
            return CallRecordingUploadTaskQueue(delegate)
        }

    }

    override fun size(): Int {
        Log.d(TAG,"size")
        return delegate.size()
    }

    override fun add(entry: CallLogRecording?) {
        Log.d(TAG,"add")
        delegate.add(entry!!)
    }

    override fun peek(): CallLogRecording? {
        Log.d(TAG,"peek")
        return delegate.peek()
    }

    override fun remove() {
        Log.d(TAG,"remove")
        delegate.remove()
    }

    override fun setListener(listener: ObjectQueue.Listener<CallLogRecording?>?) {
        delegate.setListener(listener)
    }
}