package com.chiller3.bcr.settings

import android.os.Bundle
import android.text.SpannableString
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.setFragmentResultListener
import com.chiller3.bcr.Preferences
import com.chiller3.bcr.R
import com.chiller3.bcr.databinding.OutputDirectoryBottomSheetBinding
import com.chiller3.bcr.dialog.FileRetentionDialogFragment
import com.chiller3.bcr.dialog.FilenameTemplateDialogFragment
import com.chiller3.bcr.extension.formattedString
import com.chiller3.bcr.output.OutputFilenameGenerator
import com.chiller3.bcr.output.Retention
import com.chiller3.bcr.template.Template
import com.chiller3.bcr.template.TemplateSyntaxHighlighter
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class OutputDirectoryBottomSheetFragment : BottomSheetDialogFragment() {
    private lateinit var binding: OutputDirectoryBottomSheetBinding
    private lateinit var prefs: Preferences
    private lateinit var highlighter: TemplateSyntaxHighlighter

    private val requestSafOutputDir =
        registerForActivityResult(OpenPersistentDocumentTree()) { uri ->
            prefs.outputDir = uri
            refreshOutputDir()
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = OutputDirectoryBottomSheetBinding.inflate(inflater, container, false)

        val context = requireContext()

        prefs = Preferences(context)
        highlighter = TemplateSyntaxHighlighter(context)

        binding.selectNewDir.setOnClickListener {
            requestSafOutputDir.launch(null)
        }
        binding.selectNewDir.setOnLongClickListener {
            prefs.outputDir = null
            refreshOutputDir()
            true
        }

        binding.editTemplate.setOnClickListener {
            FilenameTemplateDialogFragment().show(
                parentFragmentManager.beginTransaction(), FilenameTemplateDialogFragment.TAG)
        }
        binding.editTemplate.setOnLongClickListener {
            prefs.filenameTemplate = null
            refreshFilenameTemplate()
            true
        }

        binding.editRetention.setOnClickListener {
            FileRetentionDialogFragment().show(
                parentFragmentManager.beginTransaction(), FileRetentionDialogFragment.TAG)
        }
        binding.editRetention.setOnLongClickListener {
            prefs.outputRetention = null
            refreshOutputRetention()
            true
        }

        binding.reset.setOnClickListener {
            prefs.outputDir = null
            refreshOutputDir()
            prefs.filenameTemplate = null
            refreshFilenameTemplate()
            prefs.outputRetention = null
            refreshOutputRetention()
        }

        setFragmentResultListener(FilenameTemplateDialogFragment.TAG) { _, _ ->
            refreshFilenameTemplate()
            refreshOutputRetention()
        }
        setFragmentResultListener(FileRetentionDialogFragment.TAG) { _, _ ->
            refreshOutputRetention()
        }

        refreshFilenameTemplate()
        refreshOutputDir()
        refreshOutputRetention()

        return binding.root
    }

    private fun refreshOutputDir() {
        val outputDirUri = prefs.outputDirOrDefault
        binding.outputDir.text = outputDirUri.formattedString
    }

    private fun refreshFilenameTemplate() {
        val template = prefs.filenameTemplate ?: Preferences.DEFAULT_FILENAME_TEMPLATE
        val highlightedTemplate = SpannableString(template.toString())
        highlighter.highlight(highlightedTemplate)

        binding.filenameTemplate.text = highlightedTemplate
    }

    private fun refreshOutputRetention() {
        // Disable retention options if the template makes it impossible for the feature to work
        val template = prefs.filenameTemplate ?: Preferences.DEFAULT_FILENAME_TEMPLATE
        val locations = template.findVariableRef(OutputFilenameGenerator.DATE_VAR)
        val retentionUsable = locations != null &&
                locations.second != setOf(Template.VariableRefLocation.Arbitrary)

        binding.retention.isEnabled = retentionUsable
        binding.editRetention.isEnabled = retentionUsable

        if (retentionUsable) {
            val retention = Retention.fromPreferences(prefs)
            binding.retention.text = retention.toFormattedString(requireContext())
        } else {
            binding.retention.setText(R.string.retention_unusable)
        }
    }

    companion object {
        val TAG: String = OutputDirectoryBottomSheetFragment::class.java.simpleName
    }
}
