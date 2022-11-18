package link.cure.recorder.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import link.cure.recorder.databinding.OutputDirectoryBottomSheetBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.slider.Slider
import link.cure.recorder.utils.OpenPersistentDocumentTree
import link.cure.recorder.utils.Preferences
import link.cure.recorder.utils.Retention
import link.cure.recorder.utils.formattedString

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
            Retention.all[it.toInt()].toFormattedString(context)
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