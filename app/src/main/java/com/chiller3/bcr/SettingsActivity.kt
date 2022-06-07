package com.chiller3.bcr

import android.content.ContentResolver
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.chiller3.bcr.format.Formats
import com.chiller3.bcr.format.NoParamInfo
import com.chiller3.bcr.format.RangedParamInfo
import com.chiller3.bcr.format.SampleRates

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)
        if (savedInstanceState == null) {
            supportFragmentManager
                    .beginTransaction()
                    .replace(R.id.settings, SettingsFragment())
                    .commit()
        }

        setSupportActionBar(findViewById(R.id.toolbar))

        setTitle(R.string.app_name_full)
    }

    class SettingsFragment : PreferenceFragmentCompat(), Preference.OnPreferenceChangeListener,
        Preference.OnPreferenceClickListener, LongClickablePreference.OnPreferenceLongClickListener,
        SharedPreferences.OnSharedPreferenceChangeListener {
        private lateinit var prefCallRecording: SwitchPreferenceCompat
        private lateinit var prefOutputDir: LongClickablePreference
        private lateinit var prefOutputFormat: Preference
        private lateinit var prefInhibitBatteryOpt: SwitchPreferenceCompat
        private lateinit var prefVersion: LongClickablePreference

        private val requestPermissionRequired =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
                if (granted.all { it.value }) {
                    prefCallRecording.isChecked = true
                } else {
                    startActivity(Permissions.getAppInfoIntent(requireContext()))
                }
            }
        private val requestSafOutputDir =
            registerForActivityResult(OpenPersistentDocumentTree()) { uri ->
                Preferences.setOutputDir(requireContext(), uri)
                refreshOutputDir()
            }
        private val requestInhibitBatteryOpt =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                refreshInhibitBatteryOptState()
            }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey)

            val context = requireContext()

            // If the desired state is enabled, set to disabled if runtime permissions have been
            // denied. The user will have to grant permissions again to re-enable the features.

            prefCallRecording = findPreference(Preferences.PREF_CALL_RECORDING)!!
            if (prefCallRecording.isChecked && !Permissions.haveRequired(context)) {
                prefCallRecording.isChecked = false
            }
            prefCallRecording.onPreferenceChangeListener = this

            prefOutputDir = findPreference(Preferences.PREF_OUTPUT_DIR)!!
            prefOutputDir.onPreferenceClickListener = this
            prefOutputDir.onPreferenceLongClickListener = this
            refreshOutputDir()

            prefOutputFormat = findPreference(Preferences.PREF_OUTPUT_FORMAT)!!
            prefOutputFormat.onPreferenceClickListener = this
            refreshOutputFormat()

            prefInhibitBatteryOpt = findPreference(Preferences.PREF_INHIBIT_BATT_OPT)!!
            prefInhibitBatteryOpt.onPreferenceChangeListener = this

            prefVersion = findPreference(Preferences.PREF_VERSION)!!
            prefVersion.onPreferenceClickListener = this
            prefVersion.onPreferenceLongClickListener = this
            refreshVersion()
        }

        override fun onResume() {
            super.onResume()

            preferenceScreen.sharedPreferences!!.registerOnSharedPreferenceChangeListener(this)

            // Changing the battery state does not cause a reload of the activity
            refreshInhibitBatteryOptState()
        }

        override fun onPause() {
            super.onPause()

            preferenceScreen.sharedPreferences!!.unregisterOnSharedPreferenceChangeListener(this)
        }

        private fun refreshOutputDir() {
            val context = requireContext()
            val outputDirUri = Preferences.getOutputDir(context)
            val outputDirFormatted = when {
                outputDirUri.scheme == ContentResolver.SCHEME_FILE -> outputDirUri.path
                outputDirUri.scheme == ContentResolver.SCHEME_CONTENT
                        && outputDirUri.authority == "com.android.externalstorage.documents" -> {
                    // DocumentsContract.findDocumentPath() may sometimes crash with a permission
                    // error when passed a children document URI after an app upgrade, even though
                    // the app still has valid persisted URI permissions. Instead, just parse the
                    // URI manually. The format of SAF URIs hasn't changed across the versions of
                    // Android that BCR supports.
                    val segments = outputDirUri.pathSegments
                    val treeIndex = segments.indexOf("tree")

                    if (treeIndex >= 0 && treeIndex < segments.size - 1) {
                        segments[treeIndex + 1]
                    } else {
                        outputDirUri.toString()
                    }
                }
                else -> outputDirUri.toString()
            }

            val summary = getString(R.string.pref_output_dir_desc)
            prefOutputDir.summary = "${summary}\n\n${outputDirFormatted}"
        }

        private fun refreshOutputFormat() {
            val context = requireContext()
            val (format, formatParamSaved) = Formats.fromPreferences(context)
            val formatParam = formatParamSaved ?: format.paramInfo.default
            val summary = getString(R.string.pref_output_format_desc)
            val prefix = when (val info = format.paramInfo) {
                is RangedParamInfo -> "${info.format(formatParam)}, "
                NoParamInfo -> ""
            }
            val sampleRate = SampleRates.format(SampleRates.fromPreferences(context))

            prefOutputFormat.summary = "${summary}\n\n${format.name} (${prefix}${sampleRate})"
        }

        private fun refreshVersion() {
            val suffix = if (!BuildConfig.DEBUG && Preferences.isDebugMode(requireContext())) {
                "+debugmode"
            } else {
                ""
            }
            prefVersion.summary = "${BuildConfig.VERSION_NAME} (${BuildConfig.BUILD_TYPE}${suffix})"
        }

        private fun refreshInhibitBatteryOptState() {
            val inhibiting = Permissions.isInhibitingBatteryOpt(requireContext())
            prefInhibitBatteryOpt.isChecked = inhibiting
            prefInhibitBatteryOpt.isEnabled = !inhibiting
        }

        override fun onPreferenceChange(preference: Preference, newValue: Any?): Boolean {
            // No need to validate runtime permissions when disabling a feature
            if (newValue == false) {
                return true
            }

            val context = requireContext()

            when (preference) {
                prefCallRecording -> if (Permissions.haveRequired(context)) {
                    return true
                } else {
                    requestPermissionRequired.launch(Permissions.REQUIRED)
                }
                // This is only reachable if battery optimization is not already inhibited
                prefInhibitBatteryOpt -> requestInhibitBatteryOpt.launch(
                    Permissions.getInhibitBatteryOptIntent(requireContext()))
            }

            return false
        }

        override fun onPreferenceClick(preference: Preference): Boolean {
            when (preference) {
                prefOutputDir -> {
                    requestSafOutputDir.launch(null)
                    return true
                }
                prefOutputFormat -> {
                    FormatBottomSheetFragment().show(
                        childFragmentManager, FormatBottomSheetFragment.TAG)
                    return true
                }
                prefVersion -> {
                    val uri = Uri.parse(BuildConfig.PROJECT_URL_AT_COMMIT)
                    startActivity(Intent(Intent.ACTION_VIEW, uri))
                    return true
                }
            }

            return false
        }

        override fun onPreferenceLongClick(preference: Preference): Boolean {
            when (preference) {
                prefOutputDir -> {
                    Preferences.setOutputDir(requireContext(), null)
                    refreshOutputDir()
                    return true
                }
                prefVersion -> {
                    val context = requireContext()
                    Preferences.setDebugMode(context, !Preferences.isDebugMode(context))
                    refreshVersion()
                    return true
                }
            }

            return false
        }

        override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
            when {
                // Update the switch state if it was toggled outside of the preference (eg. from the
                // quick settings toggle)
                key == prefCallRecording.key -> {
                    val current = prefCallRecording.isChecked
                    val expected = sharedPreferences.getBoolean(key, current)
                    if (current != expected) {
                        prefCallRecording.isChecked = expected
                    }
                }
                // Update the output format state when it's changed by the bottom sheet
                Preferences.isFormatKey(key) || key == Preferences.PREF_SAMPLE_RATE -> {
                    refreshOutputFormat()
                }
            }
        }
    }
}