package link.cure.recorder.data.queue

import com.google.gson.Gson
import com.squareup.tape.FileObjectQueue
import java.io.*

/**
 * Use GSON to serialize classes to a bytes.
 *
 *
 * Note: This will only work when concrete classes are specified for `T`. If you want to specify an interface for
 * `T` then you need to also include the concrete class name in the serialized byte array so that you can
 * deserialize to the appropriate type.
 */
class GsonConverter<T>(private val gson: Gson, private val type: Class<T>) :
    FileObjectQueue.Converter<T> {
    override fun from(bytes: ByteArray): T {
        val reader: Reader = InputStreamReader(ByteArrayInputStream(bytes))
        return gson.fromJson(reader, type)
    }

    @Throws(IOException::class)
    override fun toStream(`object`: T, bytes: OutputStream) {
        val writer: Writer = OutputStreamWriter(bytes)
        gson.toJson(`object`, writer)
        writer.close()
    }
}