package com.chiller3.bcr

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat

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
        private lateinit var prefInhibitBatteryOpt: SwitchPreferenceCompat
        private lateinit var prefVersion: Preference

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

            prefInhibitBatteryOpt = findPreference(Preferences.PREF_INHIBIT_BATT_OPT)!!
            prefInhibitBatteryOpt.onPreferenceChangeListener = this

            prefVersion = findPreference(Preferences.PREF_VERSION)!!
            prefVersion.onPreferenceClickListener = this
            prefVersion.summary = BuildConfig.VERSION_NAME
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
            val outputDir = Preferences.getOutputDir(requireContext())
            val summary = getString(R.string.pref_output_dir_desc)
            prefOutputDir.summary = "${summary}\n\n${outputDir}"
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
                prefVersion -> {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PROJECT_URL)))
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
            }

            return false
        }

        override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
            // Update the switch state if it was toggled outside of the preference (eg. from the
            // quick settings toggle)
            when (key) {
                prefCallRecording.key -> {
                    val current = prefCallRecording.isChecked
                    val expected = sharedPreferences.getBoolean(key, current)
                    if (current != expected) {
                        prefCallRecording.isChecked = expected
                    }
                }
            }
        }

        companion object {
            private const val PROJECT_URL = "https://github.com/chenxiaolong/BCR"
        }
    }
}