package com.chiller3.bcr.codec

import android.content.Context
import com.chiller3.bcr.Preferences

object Codecs {
    val all: Array<Codec> = arrayOf(FlacCodec, OpusCodec, AacCodec)
    val default: Codec = all.first()

    /** Find output codec by name. */
    fun getByName(name: String): Codec? = all.find { it.name == name }

    /**
     * Get the saved codec from the preferences or fall back to the default.
     *
     * The parameter, if set, is clamped to the codec's allowed parameter range.
     */
    fun fromPreferences(context: Context): Pair<Codec, UInt?> {
        val savedCodecName = Preferences.getCodecName(context)
        val codec = if (savedCodecName != null) {
            getByName(savedCodecName) ?: default
        } else {
            default
        }

        // Clamp to the codec's allowed parameter range in case the range is shrunk
        val param = Preferences.getCodecParam(context, codec.name)?.coerceIn(codec.paramRange)

        return Pair(codec, param)
    }

    /** Save the selected codec and its parameter to the preferences. */
    fun saveToPreferences(context: Context, codec: Codec, param: UInt?) {
        Preferences.setCodecName(context, codec.name)
        Preferences.setCodecParam(context, codec.name, param)
    }
}