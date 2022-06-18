@file:Suppress("OPT_IN_IS_NOT_ENABLED")
@file:OptIn(ExperimentalUnsignedTypes::class)

package com.chiller3.bcr

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.chiller3.bcr.databinding.OutputDirectoryBottomSheetBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.slider.Slider

class OutputDirectoryBottomSheetFragment : BottomSheetDialogFragment(), Slider.OnChangeListener {
    private var _binding: OutputDirectoryBottomSheetBinding? = null
    private val binding
        get() = _binding!!

    private lateinit var prefs: Preferences

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

        binding.selectNewDir.setOnClickListener {
            requestSafOutputDir.launch(null)
        }

        binding.retentionSlider.valueFrom = 0f
        binding.retentionSlider.valueTo = (Retention.all.size - 1).toFloat()
        binding.retentionSlider.stepSize = 1f
        binding.retentionSlider.setLabelFormatter {
            Retention.format(context, Retention.all[it.toInt()])
        }
        binding.retentionSlider.addOnChangeListener(this)

        binding.reset.setOnClickListener {
            prefs.outputDir = null
            refreshOutputDir()
            prefs.outputRetention = null
            refreshOutputRetention()
        }

        refreshOutputDir()
        refreshOutputRetention()

        return binding.root
    }

    private fun refreshOutputDir() {
        val outputDirUri = prefs.outputDirOrDefault
        binding.outputDir.text = outputDirUri.formattedString
    }

    private fun refreshOutputRetention() {
        val days = Retention.fromPreferences(prefs)
        binding.retentionSlider.value = Retention.all.indexOf(days).toFloat()
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