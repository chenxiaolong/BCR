package com.chiller3.bcr

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.util.Log
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.chiller3.bcr.extension.DOCUMENTSUI_AUTHORITY
import com.chiller3.bcr.extension.safTreeToDocument
import com.chiller3.bcr.format.Format
import com.chiller3.bcr.format.SampleRate
import com.chiller3.bcr.output.Retention
import com.chiller3.bcr.rule.RecordRule
import com.chiller3.bcr.template.Template
import java.io.File

class Preferences(private val context: Context) {
    companion object {
        private val TAG = Preferences::class.java.simpleName

        const val CATEGORY_RULES = "rules"

        const val PREF_CALL_RECORDING = "call_recording"
        const val PREF_RECORD_RULES = "record_rules"
        const val PREF_OUTPUT_DIR = "output_dir"
        const val PREF_FILENAME_TEMPLATE = "filename_template"
        const val PREF_OUTPUT_FORMAT = "output_format"
        const val PREF_INHIBIT_BATT_OPT = "inhibit_batt_opt"
        const val PREF_WRITE_METADATA = "write_metadata"
        const val PREF_VERSION = "version"

        const val PREF_ADD_RULE = "add_rule"
        const val PREF_RULE_PREFIX = "rule_"

        // Not associated with a UI preference
        private const val PREF_DEBUG_MODE = "debug_mode"
        private const val PREF_RECORD_RULE_PREFIX = "record_rule_"
        private const val PREF_FORMAT_NAME = "codec_name"
        private const val PREF_FORMAT_PARAM_PREFIX = "codec_param_"
        const val PREF_OUTPUT_RETENTION = "output_retention"
        const val PREF_SAMPLE_RATE = "sample_rate"
        private const val PREF_NEXT_NOTIFICATION_ID = "next_notification_id"

        // Defaults
        val DEFAULT_FILENAME_TEMPLATE = Template(
            "{date}" +
                    "[_{direction}|]" +
                    "[_sim{sim_slot}|]" +
                    "[_{phone_number}|]" +
                    "[_[{contact_name}|{caller_name}|{call_log_name}]|]"
        )
        val DEFAULT_RECORD_RULES = listOf(
            RecordRule.UnknownCalls(true),
            RecordRule.AllCalls(true),
        )

        fun isFormatKey(key: String): Boolean =
            key == PREF_FORMAT_NAME || key.startsWith(PREF_FORMAT_PARAM_PREFIX)
    }

    internal val prefs = PreferenceManager.getDefaultSharedPreferences(context)

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
        get() = BuildConfig.FORCE_DEBUG_MODE || prefs.getBoolean(PREF_DEBUG_MODE, false)
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
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                                or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                    putString(PREF_OUTPUT_DIR, uri.toString())
                } else {
                    remove(PREF_OUTPUT_DIR)
                }
            }

            // Release persisted permissions on the old directory only after the new URI is set to
            // guarantee atomicity
            if (oldUri != null) {
                // It's not documented, but this can throw an exception when trying to release a
                // previously persisted URI that's associated with an app that's no longer installed
                try {
                    context.contentResolver.releasePersistableUriPermission(
                        oldUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                                or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Error when releasing persisted URI permission for: $oldUri", e)
                }
            }

            // Clear all alert notifications. Having them disappear is a better user experience than
            // having the open/share actions use a no-longer-valid URI.
            Notifications(context).dismissAll()
        }

    /**
     * Get the user-specified output directory or the default if none was set. This method does not
     * perform any filesystem operations to check if the user-specified directory is still valid.
     */
    val outputDirOrDefault: Uri
        get() = outputDir ?: Uri.fromFile(defaultOutputDir)

    /**
     * Build an [Intent] for opening DocumentsUI to the user-specified output directory or the
     * default if none was set.
     */
    val outputDirOrDefaultIntent: Intent
        get() = Intent(Intent.ACTION_VIEW).apply {
            val uri = outputDir?.safTreeToDocument() ?: run {
                // Opening a file:// URI will fail with FileUriExposedException. We need to hackily
                // build our own URI for the directory. Luckily, the implementation details have
                // never changed...
                val externalDir = Environment.getExternalStorageDirectory()
                val relPath = defaultOutputDir.relativeTo(externalDir)

                DocumentsContract.buildDocumentUri(DOCUMENTSUI_AUTHORITY, "primary:$relPath")
            }
            setDataAndType(uri, "vnd.android.document/directory")
        }

    /** The user-specified filename template. */
    var filenameTemplate: Template?
        get() = prefs.getString(PREF_FILENAME_TEMPLATE, null)?.let { Template(it) }
        set(template) = prefs.edit {
            if (template == null) {
                remove(PREF_FILENAME_TEMPLATE)
            } else {
                putString(PREF_FILENAME_TEMPLATE, template.toString())
            }
        }

    /**
     * The saved file retention (in days).
     *
     * Must not be [UInt.MAX_VALUE].
     */
    var outputRetention: Retention?
        get() = getOptionalUint(PREF_OUTPUT_RETENTION)?.let { Retention.fromRawPreferenceValue(it) }
        set(retention) = setOptionalUint(PREF_OUTPUT_RETENTION, retention?.toRawPreferenceValue())

    /**
     * Whether call recording is enabled.
     */
    var isCallRecordingEnabled: Boolean
        get() = prefs.getBoolean(PREF_CALL_RECORDING, false)
        set(enabled) = prefs.edit { putBoolean(PREF_CALL_RECORDING, enabled) }

    /** List of rules to determine whether to automatically record. */
    var recordRules: List<RecordRule>?
        get() {
            val rules = mutableListOf<RecordRule>()
            while (true) {
                val prefix = "${PREF_RECORD_RULE_PREFIX}${rules.size}_"
                val rule = RecordRule.fromRawPreferences(prefs, prefix) ?: break
                rules.add(rule)
            }
            return rules.ifEmpty { null }
        }
        set(rules) = prefs.edit {
            val keys = prefs.all.keys.filter { it.startsWith(PREF_RECORD_RULE_PREFIX) }
            for (key in keys) {
                remove(key)
            }

            if (rules != null) {
                for ((i, rule) in rules.withIndex()) {
                    rule.toRawPreferences(this, "${PREF_RECORD_RULE_PREFIX}${i}_")
                }
            }
        }

    /**
     * The saved output format.
     *
     * Use [getFormatParam]/[setFormatParam] to get/set the format-specific parameter.
     */
    var format: Format?
        get() = prefs.getString(PREF_FORMAT_NAME, null)?.let { Format.getByName(it) }
        set(format) = prefs.edit {
            if (format == null) {
                remove(PREF_FORMAT_NAME)
            } else {
                putString(PREF_FORMAT_NAME, format.name)
            }
        }

    /**
     * Get the format-specific parameter for [format].
     */
    fun getFormatParam(format: Format): UInt? =
        getOptionalUint(PREF_FORMAT_PARAM_PREFIX + format.name)

    /**
     * Set the format-specific parameter for [format].
     *
     * @param param Must not be [UInt.MAX_VALUE]
     *
     * @throws IllegalArgumentException if [param] is [UInt.MAX_VALUE]
     */
    fun setFormatParam(format: Format, param: UInt?) =
        setOptionalUint(PREF_FORMAT_PARAM_PREFIX + format.name, param)

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
    var sampleRate: SampleRate?
        get() = getOptionalUint(PREF_SAMPLE_RATE)?.let { SampleRate(it) }
        set(sampleRate) = setOptionalUint(PREF_SAMPLE_RATE, sampleRate?.value)

    /**
     * Whether to write call metadata file.
     */
    var writeMetadata: Boolean
        get() = prefs.getBoolean(PREF_WRITE_METADATA, false)
        set(enabled) = prefs.edit { putBoolean(PREF_WRITE_METADATA, enabled) }

    /**
     * Get a unique notification ID that increments on every call.
     */
    val nextNotificationId: Int
        get() = synchronized(context.applicationContext) {
            val nextId = prefs.getInt(PREF_NEXT_NOTIFICATION_ID, 0)
            prefs.edit { putInt(PREF_NEXT_NOTIFICATION_ID, nextId + 1) }
            nextId
        }
}
