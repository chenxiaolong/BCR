package com.chiller3.bcr

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.preference.PreferenceManager
import java.io.File

object Preferences {
    const val PREF_CALL_RECORDING = "call_recording"
    const val PREF_OUTPUT_DIR = "output_dir"
    const val PREF_OUTPUT_FORMAT = "output_format"
    const val PREF_INHIBIT_BATT_OPT = "inhibit_batt_opt"
    const val PREF_VERSION = "version"

    // Not associated with a UI preference
    private const val PREF_FORMAT_NAME = "codec_name"
    private const val PREF_FORMAT_PARAM_PREFIX = "codec_param_"

    /**
     * Get the default output directory. The directory should always be writable and is suitable for
     * use as a fallback.
     */
    fun getDefaultOutputDir(context: Context): File =
        context.getExternalFilesDir(null)!!

    /**
     * Get the user-specified output directory. The returned URI refers to a write-persisted URI
     * provided by SAF.
     */
    fun getSavedOutputDir(context: Context): Uri? {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val outputDir = prefs.getString(PREF_OUTPUT_DIR, null)
        if (outputDir != null) {
            return Uri.parse(outputDir)
        }
        return null
    }

    /**
     * Get the user-specified output directory or the default if none was set. This method does not
     * perform any filesystem operations to check if the user-specified directory is still valid.
     */
    fun getOutputDir(context: Context): Uri {
        return getSavedOutputDir(context) ?: Uri.fromFile(getDefaultOutputDir(context))
    }

    /**
     * Set a new user-specified output directory. [uri] must refer to a document tree provided by
     * SAF or null if the existing saved directory should be cleared. Persisted URI permissions for
     * the old directory, if set, will be revoked and persisted write permissions for the new URI
     * will be requested. If the old URI and the new URI is the same, nothing is done. If persisting
     * permissions for the new URI fails, the saved directory is not changed.
     */
    fun setOutputDir(context: Context, uri: Uri?) {
        val oldUri = getSavedOutputDir(context)
        if (oldUri == uri) {
            // URI is the same as before or both are null
            return
        }

        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val editor = prefs.edit()

        if (uri != null) {
            // Persist permissions for the new URI first
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            editor.putString(PREF_OUTPUT_DIR, uri.toString())
        } else {
            editor.remove(PREF_OUTPUT_DIR)
        }

        editor.apply()

        // Release persisted permissions on the old directory only after the new URI is set to
        // guarantee atomicity
        if (oldUri != null) {
            context.contentResolver.releasePersistableUriPermission(
                oldUri,
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
    }

    fun isCallRecordingEnabled(context: Context): Boolean {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        return prefs.getBoolean(PREF_CALL_RECORDING, false)
    }

    fun setCallRecordingEnabled(context: Context, enabled: Boolean) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val editor = prefs.edit()
        editor.putBoolean(PREF_CALL_RECORDING, enabled)
        editor.apply()
    }

    fun isFormatKey(key: String): Boolean =
        key == PREF_FORMAT_NAME || key.startsWith(PREF_FORMAT_PARAM_PREFIX)

    /**
     * Get the saved output format.
     *
     * Use [getFormatParam] to get the format-specific parameter.
     */
    fun getFormatName(context: Context): String? {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        return prefs.getString(PREF_FORMAT_NAME, null)
    }

    /**
     * Save the selected output format.
     *
     * Use [setFormatParam] to set the format-specific parameter.
     */
    fun setFormatName(context: Context, name: String?) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val editor = prefs.edit()

        if (name == null) {
            editor.remove(PREF_FORMAT_NAME)
        } else {
            editor.putString(PREF_FORMAT_NAME, name)
        }

        editor.apply()
    }

    /**
     * Get the format-specific parameter for format [name].
     */
    fun getFormatParam(context: Context, name: String): UInt? {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val key = PREF_FORMAT_PARAM_PREFIX + name
        // Use a sentinel value because doing contains + getInt results in TOCTOU issues
        val value = prefs.getInt(key, -1)

        return if (value == -1) {
            null
        } else {
            value.toUInt()
        }
    }

    /**
     * Set the format-specific parameter for format [name].
     *
     * @param param Must not be [UInt.MAX_VALUE]
     *
     * @throws IllegalArgumentException if [param] is [UInt.MAX_VALUE]
     */
    fun setFormatParam(context: Context, name: String, param: UInt?) {
        // -1 (when casted to int) is used as a sentinel value
        if (param == UInt.MAX_VALUE) {
            throw IllegalArgumentException("Parameter cannot be ${UInt.MAX_VALUE}")
        }

        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val editor = prefs.edit()
        val key = PREF_FORMAT_PARAM_PREFIX + name

        if (param == null) {
            editor.remove(key)
        } else {
            editor.putInt(key, param.toInt())
        }

        editor.apply()
    }

    /**
     * Remove the default format preference and the parameters for all formats.
     */
    fun resetAllFormats(context: Context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val keys = prefs.all.keys.filter(::isFormatKey)
        val editor = prefs.edit()

        for (key in keys) {
            editor.remove(key)
        }

        editor.apply()
    }
}