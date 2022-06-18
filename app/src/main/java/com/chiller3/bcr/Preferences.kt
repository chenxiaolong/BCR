package com.chiller3.bcr

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import java.io.File

class Preferences(private val context: Context) {
    companion object {
        const val PREF_CALL_RECORDING = "call_recording"
        const val PREF_OUTPUT_DIR = "output_dir"
        const val PREF_OUTPUT_FORMAT = "output_format"
        const val PREF_INHIBIT_BATT_OPT = "inhibit_batt_opt"
        const val PREF_VERSION = "version"

        // Not associated with a UI preference
        private const val PREF_DEBUG_MODE = "debug_mode"
        private const val PREF_FORMAT_NAME = "codec_name"
        private const val PREF_FORMAT_PARAM_PREFIX = "codec_param_"
        const val PREF_OUTPUT_RETENTION = "output_retention"
        const val PREF_SAMPLE_RATE = "sample_rate"

        fun isFormatKey(key: String): Boolean =
            key == PREF_FORMAT_NAME || key.startsWith(PREF_FORMAT_PARAM_PREFIX)
    }

    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)

    /**
     * Get a unsigned integer preference value.
     *
     * @return Will never be [UInt.MAX_VALUE]
     */
    private fun getOptionalUint(key: String): UInt? {
        // Use a sentinel value because doing contains + getInt results in TOCTOU issues
        val value = prefs.getInt(key, -1)

        return if (value == -1) {
            null
        } else {
            value.toUInt()
        }
    }

    /**
     * Set an unsigned integer preference to [value].
     *
     * @param value Must not be [UInt.MAX_VALUE]
     *
     * @throws IllegalArgumentException if [value] is [UInt.MAX_VALUE]
     */
    private fun setOptionalUint(key: String, value: UInt?) {
        // -1 (when casted to int) is used as a sentinel value
        if (value == UInt.MAX_VALUE) {
            throw IllegalArgumentException("$key value cannot be ${UInt.MAX_VALUE}")
        }

        prefs.edit {
            if (value == null) {
                remove(key)
            } else {
                putInt(key, value.toInt())
            }
        }
    }

    var isDebugMode: Boolean
        get() = prefs.getBoolean(PREF_DEBUG_MODE, false)
        set(enabled) = prefs.edit { putBoolean(PREF_DEBUG_MODE, enabled) }

    /**
     * Get the default output directory. The directory should always be writable and is suitable for
     * use as a fallback.
     */
    val defaultOutputDir: File = context.getExternalFilesDir(null)!!

    /**
     * The user-specified output directory.
     *
     * The URI, it not null, refers to a write-persisted URI provided by SAF. When a new URI is set,
     * persisted URI permissions for the old URI will be revoked and persisted write permissions
     * for the new URI will be requested. If the old and new URI are the same, nothing is done. If
     * persisting permissions for the new URI fails, the saved output directory is not changed.
     */
    var outputDir: Uri?
        get() = prefs.getString(PREF_OUTPUT_DIR, null)?.let { Uri.parse(it) }
        set(uri) {
            val oldUri = outputDir
            if (oldUri == uri) {
                // URI is the same as before or both are null
                return
            }

            prefs.edit {
                if (uri != null) {
                    // Persist permissions for the new URI first
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                    putString(PREF_OUTPUT_DIR, uri.toString())
                } else {
                    remove(PREF_OUTPUT_DIR)
                }
            }

            // Release persisted permissions on the old directory only after the new URI is set to
            // guarantee atomicity
            if (oldUri != null) {
                context.contentResolver.releasePersistableUriPermission(
                    oldUri,
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
        }

    /**
     * Get the user-specified output directory or the default if none was set. This method does not
     * perform any filesystem operations to check if the user-specified directory is still valid.
     */
    val outputDirOrDefault: Uri
        get() = outputDir ?: Uri.fromFile(defaultOutputDir)

    /**
     * The saved file retention (in days).
     *
     * Must not be [UInt.MAX_VALUE].
     */
    var outputRetention: UInt?
        get() = getOptionalUint(PREF_OUTPUT_RETENTION)
        set(days) = setOptionalUint(PREF_OUTPUT_RETENTION, days)

    /**
     * Whether call recording is enabled.
     */
    var isCallRecordingEnabled: Boolean
        get() = prefs.getBoolean(PREF_CALL_RECORDING, false)
        set(enabled) = prefs.edit { putBoolean(PREF_CALL_RECORDING, enabled) }

    /**
     * The saved output format.
     *
     * Use [getFormatParam]/[setFormatParam] to get/set the format-specific parameter.
     */
    var formatName: String?
        get() = prefs.getString(PREF_FORMAT_NAME, null)
        set(name) = prefs.edit {
            if (name == null) {
                remove(PREF_FORMAT_NAME)
            } else {
                putString(PREF_FORMAT_NAME, name)
            }
        }

    /**
     * Get the format-specific parameter for format [name].
     */
    fun getFormatParam(name: String): UInt? = getOptionalUint(PREF_FORMAT_PARAM_PREFIX + name)

    /**
     * Set the format-specific parameter for format [name].
     *
     * @param param Must not be [UInt.MAX_VALUE]
     *
     * @throws IllegalArgumentException if [param] is [UInt.MAX_VALUE]
     */
    fun setFormatParam(name: String, param: UInt?) =
        setOptionalUint(PREF_FORMAT_PARAM_PREFIX + name, param)

    /**
     * Remove the default format preference and the parameters for all formats.
     */
    fun resetAllFormats() {
        val keys = prefs.all.keys.filter(::isFormatKey)
        prefs.edit {
            for (key in keys) {
                remove(key)
            }
        }
    }

    /**
     * The recording and output sample rate.
     *
     * Must not be [UInt.MAX_VALUE].
     */
    var sampleRate: UInt?
        get() = getOptionalUint(PREF_SAMPLE_RATE)
        set(sampleRate) = setOptionalUint(PREF_SAMPLE_RATE, sampleRate)
}