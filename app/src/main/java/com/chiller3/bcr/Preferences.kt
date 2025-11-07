/*
 * SPDX-FileCopyrightText: 2022-2024 Andrew Gunnerson
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.chiller3.bcr

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.UserManager
import android.provider.DocumentsContract
import android.util.Log
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.preference.PreferenceManager
import com.chiller3.bcr.extension.DOCUMENTSUI_AUTHORITY
import com.chiller3.bcr.extension.safTreeToDocument
import com.chiller3.bcr.format.Format
import com.chiller3.bcr.output.Retention
import com.chiller3.bcr.rule.LegacyRecordRule
import com.chiller3.bcr.rule.RecordRule
import com.chiller3.bcr.template.Template
import kotlinx.serialization.json.Json
import java.io.File

class Preferences(initialContext: Context) {
    companion object {
        private val TAG = Preferences::class.java.simpleName

        const val CATEGORY_DEBUG = "debug"

        const val PREF_CALL_RECORDING = "call_recording"
        const val PREF_RECORD_RULES = "record_rules"
        const val PREF_OUTPUT_DIR = "output_dir"
        const val PREF_FILENAME_TEMPLATE = "filename_template"
        const val PREF_OUTPUT_FORMAT = "output_format"
        const val PREF_MIN_DURATION = "min_duration"
        const val PREF_INHIBIT_BATT_OPT = "inhibit_batt_opt"
        const val PREF_HIDE_LAUNCHER_ICON = "hide_launcher_icon"
        private const val PREF_WRITE_METADATA = "write_metadata"
        private const val PREF_RECORD_TELECOM_APPS = "record_telecom_apps"
        private const val PREF_RECORD_DIALING_STATE = "record_dialing_state"
        const val PREF_VERSION = "version"
        private const val PREF_FORCE_DIRECT_BOOT = "force_direct_boot"
        const val PREF_MIGRATE_DIRECT_BOOT = "migrate_direct_boot"
        const val PREF_SAVE_LOGS = "save_logs"

        const val PREF_ADD_NEW_RULE = "add_new_rule"

        // Not associated with a UI preference
        private const val PREF_DEBUG_MODE = "debug_mode"
        private const val PREF_LEGACY_RECORD_RULE_PREFIX = "record_rule_"
        private const val PREF_FORMAT_NAME = "codec_name"
        private const val PREF_FORMAT_PARAM_PREFIX = "codec_param_"
        private const val PREF_FORMAT_SAMPLE_RATE_PREFIX = "codec_sample_rate_"
        const val PREF_OUTPUT_RETENTION = "output_retention"
        private const val PREF_NEXT_NOTIFICATION_ID = "next_notification_id"
        private const val PREF_ALREADY_MIGRATED = "already_migrated"

        // Defaults
        val DEFAULT_FILENAME_TEMPLATE = Template(
            "{date}" +
                    "[_{direction}|]" +
                    "[_sim{sim_slot}|]" +
                    "[_{phone_number}|]" +
                    "[_[{contact_name}|{caller_name}|{call_log_name}]|]"
        )
        val DEFAULT_RECORD_RULES = listOf(
            RecordRule(
                callNumber = RecordRule.CallNumber.Any,
                callType = RecordRule.CallType.ANY,
                simSlot = RecordRule.SimSlot.Any,
                action = RecordRule.Action.SAVE,
            ),
        )

        private val JSON_FORMAT = Json { ignoreUnknownKeys = true }

        fun isFormatKey(key: String): Boolean =
            key == PREF_FORMAT_NAME
                    || key.startsWith(PREF_FORMAT_PARAM_PREFIX)
                    || key.startsWith(PREF_FORMAT_SAMPLE_RATE_PREFIX)

        fun migrateToDeviceProtectedStorage(context: Context) {
            if (context.isDeviceProtectedStorage) {
                Log.w(TAG, "Cannot migrate preferences in BFU state")
                return
            }

            val deviceContext = context.createDeviceProtectedStorageContext()
            var devicePrefs = PreferenceManager.getDefaultSharedPreferences(deviceContext)

            if (devicePrefs.getBoolean(PREF_ALREADY_MIGRATED, false)) {
                Log.i(TAG, "Already migrated preferences to device protected storage")
                return
            }

            Log.i(TAG, "Migrating preferences to device protected storage")

            // getDefaultSharedPreferencesName() is not public, but realistically, Android can't
            // ever change the default shared preferences name without breaking nearly every app.
            val sharedPreferencesName = context.packageName + "_preferences"

            // This returns true if the shared preferences didn't exist.
            if (!deviceContext.moveSharedPreferencesFrom(context, sharedPreferencesName)) {
                Log.e(TAG, "Failed to migrate preferences to device protected storage")
            }

            devicePrefs = PreferenceManager.getDefaultSharedPreferences(deviceContext)
            devicePrefs.edit { putBoolean(PREF_ALREADY_MIGRATED, true) }
        }
    }

    private val context = if (initialContext.isDeviceProtectedStorage) {
        initialContext
    } else {
        initialContext.createDeviceProtectedStorageContext()
    }
    private val userManager = context.getSystemService(UserManager::class.java)
    internal val prefs = PreferenceManager.getDefaultSharedPreferences(context)

    /**
     * Get a unsigned integer preference value.
     *
     * @return Will never be [sentinel]
     */
    private fun getOptionalUint(key: String, sentinel: UInt): UInt? {
        // Use a sentinel value because doing contains + getInt results in TOCTOU issues
        val value = prefs.getInt(key, sentinel.toInt())

        return if (value == sentinel.toInt()) {
            null
        } else {
            value.toUInt()
        }
    }

    /**
     * Set an unsigned integer preference to [value].
     *
     * @param value Must not be [sentinel]
     *
     * @throws IllegalArgumentException if [value] is [sentinel]
     */
    private fun setOptionalUint(key: String, sentinel: UInt, value: UInt?) {
        if (value == sentinel) {
            throw IllegalArgumentException("$key value cannot be $sentinel")
        }

        prefs.edit {
            if (value == null) {
                remove(key)
            } else {
                putInt(key, value.toInt())
            }
        }
    }

    /** Whether to show debug preferences and enable creation of debug logs for all calls. */
    var isDebugMode: Boolean
        get() = BuildConfig.FORCE_DEBUG_MODE || prefs.getBoolean(PREF_DEBUG_MODE, false)
        set(enabled) = prefs.edit { putBoolean(PREF_DEBUG_MODE, enabled) }

    /** Whether to output to direct boot directories even if the device has been unlocked once. */
    private var forceDirectBoot: Boolean
        get() = prefs.getBoolean(PREF_FORCE_DIRECT_BOOT, false)
        set(enabled) = prefs.edit { putBoolean(PREF_FORCE_DIRECT_BOOT, enabled) }

    /** Whether we're running in direct boot mode. */
    private val isDirectBoot: Boolean
        get() = !userManager.isUserUnlocked || forceDirectBoot

    /** Default output directory in the BFU state. */
    val directBootInProgressDir: File = File(context.filesDir, "in_progress")

    /** Target output directory in the BFU state. */
    val directBootCompletedDir: File = File(context.filesDir, "completed")

    /**
     * Get the default output directory. The directory should always be writable and is suitable for
     * use as a fallback. In the BFU state, this returns the internal app directory backed by
     * device-protected storage, which is not accessible by the user.
     */
    val defaultOutputDir: File = if (isDirectBoot) {
        directBootInProgressDir
    } else {
        context.getExternalFilesDir(null)!!
    }

    /**
     * The user-specified output directory.
     *
     * The URI, it not null, refers to a write-persisted URI provided by SAF. When a new URI is set,
     * persisted URI permissions for the old URI will be revoked and persisted write permissions
     * for the new URI will be requested. If the old and new URI are the same, nothing is done. If
     * persisting permissions for the new URI fails, the saved output directory is not changed.
     *
     * In the BFU state, this always returns null to ensure that nothing tries to move files into
     * credential-protected storage.
     */
    var outputDir: Uri?
        get() = if (isDirectBoot) {
            // We initially record to the in-progress directory and then atomically move them to the
            // completed directory afterwards. This ensures that the recorder thread won't ever race
            // with the direct boot migration service. Any completed recordings are guaranteed to be
            // in the completed directory and will be moved by the service. Any recordings that are
            // in-progress after the user unlocks for the first time will be moved to the user's
            // output directory by the recorder thread since isUserUnlocked will be true by that
            // point.
            Uri.fromFile(directBootCompletedDir)
        } else {
            prefs.getString(PREF_OUTPUT_DIR, null)?.toUri()
        }
        set(uri) {
            if (isDirectBoot) {
                throw IllegalStateException("Changing output directory while in direct boot")
            }

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
        get() = getOptionalUint(PREF_OUTPUT_RETENTION, UInt.MAX_VALUE)?.let {
            Retention.fromRawPreferenceValue(it)
        }
        set(retention) = setOptionalUint(PREF_OUTPUT_RETENTION, UInt.MAX_VALUE,
            retention?.toRawPreferenceValue())

    /**
     * Whether call recording is enabled.
     */
    var isCallRecordingEnabled: Boolean
        get() = prefs.getBoolean(PREF_CALL_RECORDING, false)
        set(enabled) = prefs.edit { putBoolean(PREF_CALL_RECORDING, enabled) }

    /** No longer used, except for migration to modern rules. */
    private var legacyRecordRules: List<LegacyRecordRule>?
        get() {
            val rules = mutableListOf<LegacyRecordRule>()
            while (true) {
                val prefix = "${PREF_LEGACY_RECORD_RULE_PREFIX}${rules.size}_"
                val rule = LegacyRecordRule.fromRawPreferences(prefs, prefix) ?: break
                rules.add(rule)
            }
            return rules.ifEmpty { null }
        }
        set(rules) = prefs.edit {
            val keys = prefs.all.keys.filter { it.startsWith(PREF_LEGACY_RECORD_RULE_PREFIX) }
            for (key in keys) {
                remove(key)
            }

            if (rules != null) {
                throw IllegalArgumentException("Setting legacy rules is not supported")
            }
        }

    /** List of rules to determine which action to take for a specific call. */
    var recordRules: List<RecordRule>?
        get() = prefs.getString(PREF_RECORD_RULES, null)?.let { JSON_FORMAT.decodeFromString(it) }
        set(rules) = prefs.edit {
            if (rules == null) {
                remove(PREF_RECORD_RULES)
            } else {
                putString(PREF_RECORD_RULES, JSON_FORMAT.encodeToString(rules))
            }
        }

    /**
     * The saved output format.
     *
     * Use [getFormatParam]/[setFormatParam] to get/set the format-specific parameter. Use
     * [getFormatSampleRate]/[setFormatSampleRate] to get/set the format-specific sample rate.
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
        getOptionalUint(PREF_FORMAT_PARAM_PREFIX + format.name, UInt.MAX_VALUE)

    /**
     * Set the format-specific parameter for [format].
     *
     * @param param Must not be [UInt.MAX_VALUE]
     *
     * @throws IllegalArgumentException if [param] is [UInt.MAX_VALUE]
     */
    fun setFormatParam(format: Format, param: UInt?) =
        setOptionalUint(PREF_FORMAT_PARAM_PREFIX + format.name, UInt.MAX_VALUE, param)

    /**
     * Get the format-specific sample rate for [format].
     */
    fun getFormatSampleRate(format: Format): UInt? =
        getOptionalUint(PREF_FORMAT_SAMPLE_RATE_PREFIX + format.name, 0U)

    /**
     * Set the format-specific sample rate for [format].
     *
     * @param rate Must not be 0
     *
     * @throws IllegalArgumentException if [rate] is 0
     */
    fun setFormatSampleRate(format: Format, rate: UInt?) =
        setOptionalUint(PREF_FORMAT_SAMPLE_RATE_PREFIX + format.name, 0U, rate)

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
     * Minimum recording duration for it to be kept if the record rules would have allowed it to be
     * kept in the first place.
     */
    var minDuration: Int
        get() = prefs.getInt(PREF_MIN_DURATION, 0)
        set(seconds) = prefs.edit { putInt(PREF_MIN_DURATION, seconds) }

    /**
     * Whether to write call metadata file.
     */
    var writeMetadata: Boolean
        get() = prefs.getBoolean(PREF_WRITE_METADATA, false)
        set(enabled) = prefs.edit { putBoolean(PREF_WRITE_METADATA, enabled) }

    /**
     * Whether to record calls from telecom-integrated apps.
     */
    var recordTelecomApps: Boolean
        get() = prefs.getBoolean(PREF_RECORD_TELECOM_APPS, false)
        set(enabled) = prefs.edit { putBoolean(PREF_RECORD_TELECOM_APPS, enabled) }

    /**
     * Whether to start recording as soon as a call enters the DIALING state.
     */
    var recordDialingState: Boolean
        get() = prefs.getBoolean(PREF_RECORD_DIALING_STATE, false)
        set(enabled) = prefs.edit { putBoolean(PREF_RECORD_DIALING_STATE, enabled) }

    /**
     * Whether to hide the launcher icon.
     */
    var hideLauncherIcon: Boolean
        get() = prefs.getBoolean(PREF_HIDE_LAUNCHER_ICON, false)
        set(enabled) = prefs.edit { putBoolean(PREF_HIDE_LAUNCHER_ICON, enabled) }

    /**
     * Get a unique notification ID that increments on every call.
     */
    val nextNotificationId: Int
        get() = synchronized(context.applicationContext) {
            val nextId = prefs.getInt(PREF_NEXT_NOTIFICATION_ID, 0)
            prefs.edit { putInt(PREF_NEXT_NOTIFICATION_ID, nextId + 1) }
            nextId
        }

    /**
     * Migrate legacy rules to modern rules.
     *
     * This migration will be removed in version 1.80.
     */
    fun migrateLegacyRules() {
        val legacyRules = legacyRecordRules
        if (legacyRules != null) {
            recordRules = LegacyRecordRule.convertToModernRules(legacyRules)
            legacyRecordRules = null
        }
    }
}
