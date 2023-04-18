package com.chiller3.bcr

import android.os.Bundle
import android.text.SpannableString
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.setFragmentResultListener
import com.chiller3.bcr.databinding.OutputDirectoryBottomSheetBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.slider.Slider

class OutputDirectoryBottomSheetFragment : BottomSheetDialogFragment(), Slider.OnChangeListener {
    private var _binding: OutputDirectoryBottomSheetBinding? = null
    private val binding
        get() = _binding!!

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
        _binding = OutputDirectoryBottomSheetBinding.inflate(inflater, container, false)

        val context = requireContext()

        prefs = Preferences(context)
        highlighter = TemplateSyntaxHighlighter(context)

        binding.selectNewDir.setOnClickListener {
            requestSafOutputDir.launch(null)
        }

        binding.editTemplate.setOnClickListener {
            FilenameTemplateDialogFragment().show(
                parentFragmentManager.beginTransaction(), FilenameTemplateDialogFragment.TAG)
        }

        binding.retentionSlider.valueFrom = 0f
        binding.retentionSlider.valueTo = (Retention.all.size - 1).toFloat()
        binding.retentionSlider.stepSize = 1f
        binding.retentionSlider.setLabelFormatter {
            Retention.all[it.toInt()].toFormattedString(context)
        }
        binding.retentionSlider.addOnChangeListener(this)

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
        val days = Retention.fromPreferences(prefs)
        binding.retentionSlider.value = Retention.all.indexOf(days).toFloat()

        // Disable retention options if the template makes it impossible for the feature to work
        val template = prefs.filenameTemplate ?: Preferences.DEFAULT_FILENAME_TEMPLATE
        val locations = template.findVariableRef(OutputFilenameGenerator.DATE_VAR)
        binding.retentionSlider.isEnabled = locations != null &&
                locations.second != setOf(Template.VariableRefLocation.Arbitrary)
    }

    override fun onValueChange(slider: Slider, value: Float, fromUser: Boolean) {
        when (slider) {
            binding.retentionSlider -> {
                prefs.outputRetention = Retention.all[value.toInt()]
            }
        }
    }

    companion object {
        val TAG: String = OutputDirectoryBottomSheetFragment::class.java.simpleName
    }
}
