package com.chiller3.bcr.codec

import android.content.Context
import com.chiller3.bcr.Preferences

object Codecs {
    val all: Array<Codec> = arrayOf(OpusCodec, AacCodec, FlacCodec)
    val default: Codec = all.first { it.supported }

    /** Find output codec by name. */
    fun getByName(name: String): Codec? = all.find { it.name == name }

    /**
     * Get the saved codec from the preferences or fall back to the default.
     *
     * The parameter, if set, is clamped to the codec's allowed parameter range.
     */
    fun fromPreferences(context: Context): Pair<Codec, UInt?> {
        val savedCodecName = Preferences.getCodecName(context)

        // Use the saved codec if it is valid and supported on the current device. Otherwise, fall
        // back to the default.
        val codec = savedCodecName
            ?.let { getByName(it) }
            ?.let { if (it.supported) { it } else { null } }
            ?: default

        // Clamp to the codec's allowed parameter range in case the range is shrunk
        val param = Preferences.getCodecParam(context, codec.name)?.coerceIn(codec.paramRange)

        return Pair(codec, param)
    }
}