/*
 * SPDX-FileCopyrightText: 2022-2026 Andrew Gunnerson
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.chiller3.bcr.settings

import android.os.Bundle
import android.text.SpannableString
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.core.view.MenuProvider
import androidx.fragment.app.setFragmentResultListener
import androidx.lifecycle.Lifecycle
import androidx.preference.Preference
import com.chiller3.bcr.PreferenceBaseFragment
import com.chiller3.bcr.Preferences
import com.chiller3.bcr.R
import com.chiller3.bcr.dialog.FileRetentionDialogFragment
import com.chiller3.bcr.dialog.FilenameTemplateDialogFragment
import com.chiller3.bcr.extension.formattedString
import com.chiller3.bcr.output.OutputFilenameGenerator
import com.chiller3.bcr.output.Retention
import com.chiller3.bcr.template.Template
import com.chiller3.bcr.template.TemplateSyntaxHighlighter
import com.chiller3.bcr.view.LongClickablePreference
import com.chiller3.bcr.view.OnPreferenceLongClickListener

class OutputDirectoryFragment : PreferenceBaseFragment(), Preference.OnPreferenceClickListener,
    OnPreferenceLongClickListener {
    override val requestTag: String = OutputDirectoryFragment::class.java.simpleName

    private lateinit var highlighter: TemplateSyntaxHighlighter

    private lateinit var prefs: Preferences
    private lateinit var prefOutputDir: LongClickablePreference
    private lateinit var prefFilenameTemplate: LongClickablePreference
    private lateinit var prefOutputRetention: LongClickablePreference

    private val requestSafOutputDir =
        registerForActivityResult(OpenPersistentDocumentTree()) { uri ->
            if (uri != null) {
                prefs.outputDir = uri
                refreshOutputDir()
            }
        }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val context = requireContext()

        highlighter = TemplateSyntaxHighlighter(context)

        // Not strictly necessary since all preferences here are non-persistent.
        preferenceManager.setStorageDeviceProtected()
        setPreferencesFromResource(R.xml.preferences_output_directory, rootKey)

        prefs = Preferences(context)

        prefOutputDir = findPreference(Preferences.PREF_OUTPUT_DIR)!!
        prefOutputDir.onPreferenceClickListener = this
        prefOutputDir.onPreferenceLongClickListener = this

        prefFilenameTemplate = findPreference(Preferences.PREF_FILENAME_TEMPLATE)!!
        prefFilenameTemplate.onPreferenceClickListener = this
        prefFilenameTemplate.onPreferenceLongClickListener = this

        prefOutputRetention = findPreference(Preferences.PREF_OUTPUT_RETENTION)!!
        prefOutputRetention.onPreferenceClickListener = this
        prefOutputRetention.onPreferenceLongClickListener = this

        refreshOutputDir()
        refreshFilenameTemplate()
        refreshOutputRetention()

        setFragmentResultListener(FilenameTemplateDialogFragment.TAG) { _, _ ->
            refreshFilenameTemplate()
            refreshOutputRetention()
        }
        setFragmentResultListener(FileRetentionDialogFragment.TAG) { _, _ ->
            refreshOutputRetention()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.reset, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem) = when (menuItem.itemId) {
                R.id.reset -> {
                    prefs.outputDir = null
                    refreshOutputDir()
                    prefs.filenameTemplate = null
                    refreshFilenameTemplate()
                    prefs.outputRetention = null
                    refreshOutputRetention()
                    true
                }
                else -> false
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun refreshOutputDir() {
        val outputDirUri = prefs.outputDirOrDefault
        prefOutputDir.summary = outputDirUri.formattedString
    }

    private fun refreshFilenameTemplate() {
        val template = prefs.filenameTemplate ?: Preferences.DEFAULT_FILENAME_TEMPLATE
        val highlightedTemplate = SpannableString(template.toString())
        highlighter.highlight(highlightedTemplate)

        prefFilenameTemplate.summary = highlightedTemplate
    }

    private fun refreshOutputRetention() {
        // Disable retention options if the template makes it impossible for the feature to work
        val template = prefs.filenameTemplate ?: Preferences.DEFAULT_FILENAME_TEMPLATE
        val locations = template.findVariableRef(OutputFilenameGenerator.DATE_VAR, true)
        val retentionUsable = locations != null &&
                locations.second != setOf(Template.VariableRefLocation.Arbitrary)

        prefOutputRetention.isEnabled = retentionUsable

        if (retentionUsable) {
            val retention = Retention.fromPreferences(prefs)
            prefOutputRetention.summary = retention.toFormattedString(requireContext())
        } else {
            prefOutputRetention.summary = getString(R.string.retention_unusable)
        }
    }

    override fun onPreferenceClick(preference: Preference): Boolean {
        when (preference) {
            prefOutputDir -> {
                requestSafOutputDir.launch(null)
                return true
            }
            prefFilenameTemplate -> {
                FilenameTemplateDialogFragment().show(
                    parentFragmentManager.beginTransaction(), FilenameTemplateDialogFragment.TAG)
                return true
            }
            prefOutputRetention -> {
                FileRetentionDialogFragment().show(
                    parentFragmentManager.beginTransaction(), FileRetentionDialogFragment.TAG)
                return true
            }
        }

        return false
    }

    override fun onPreferenceLongClick(preference: Preference): Boolean {
        when (preference) {
            prefOutputDir -> {
                prefs.outputDir = null
                refreshOutputDir()
                return true
            }
            prefFilenameTemplate -> {
                prefs.filenameTemplate = null
                refreshFilenameTemplate()
                refreshOutputRetention()
                return true
            }
            prefOutputRetention -> {
                prefs.outputRetention = null
                refreshOutputRetention()
                return true
            }
        }

        return false
    }
}
