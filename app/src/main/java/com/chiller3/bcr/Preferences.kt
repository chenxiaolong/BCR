package com.chiller3.bcr

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.preference.PreferenceManager
import java.io.File

object Preferences {
    const val PREF_CALL_RECORDING = "call_recording"
    const val PREF_OUTPUT_DIR = "output_dir"
    const val PREF_INHIBIT_BATT_OPT = "inhibit_batt_opt"
    const val PREF_VERSION = "version"

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
}