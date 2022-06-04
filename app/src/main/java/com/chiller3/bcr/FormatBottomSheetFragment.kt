@file:Suppress("OPT_IN_IS_NOT_ENABLED")
@file:OptIn(ExperimentalUnsignedTypes::class)

package com.chiller3.bcr

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import com.chiller3.bcr.databinding.FormatBottomSheetBinding
import com.chiller3.bcr.databinding.FormatBottomSheetChipBinding
import com.chiller3.bcr.format.*
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.ChipGroup
import com.google.android.material.slider.Slider

class FormatBottomSheetFragment : BottomSheetDialogFragment(),
    ChipGroup.OnCheckedStateChangeListener, Slider.OnChangeListener, View.OnClickListener {
    private var _binding: FormatBottomSheetBinding? = null
    private val binding
        get() = _binding!!

    private val chipIdToFormat = HashMap<Int, Format>()
    private val formatToChipId = HashMap<Format, Int>()
    private lateinit var formatParamInfo: FormatParamInfo

    private val chipIdToSampleRate = HashMap<Int, UInt?>()
    private val sampleRateToChipId = HashMap<UInt?, Int>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FormatBottomSheetBinding.inflate(inflater, container, false)

        binding.paramSlider.setLabelFormatter {
            formatParamInfo.format(it.toUInt())
        }
        binding.paramSlider.addOnChangeListener(this)

        binding.reset.setOnClickListener(this)

        for (format in Formats.all) {
            if (!format.supported) {
                continue
            }

            addFormatChip(inflater, format)
        }

        binding.nameGroup.setOnCheckedStateChangeListener(this)

        for (sampleRate in SampleRates.all) {
            addSampleRateChip(inflater, sampleRate)
        }

        binding.sampleRateGroup.setOnCheckedStateChangeListener(this)

        refreshFormat()
        refreshSampleRate()

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun addFormatChip(inflater: LayoutInflater, format: Format) {
        val chipBinding = FormatBottomSheetChipBinding.inflate(
            inflater, binding.nameGroup, false)
        val id = View.generateViewId()
        chipBinding.root.id = id
        chipBinding.root.text = format.name
        chipBinding.root.layoutDirection = View.LAYOUT_DIRECTION_LOCALE
        binding.nameGroup.addView(chipBinding.root)
        chipIdToFormat[id] = format
        formatToChipId[format] = id
    }

    private fun addSampleRateChip(inflater: LayoutInflater, sampleRate: UInt) {
        val chipBinding = FormatBottomSheetChipBinding.inflate(
            inflater, binding.sampleRateGroup, false)
        val id = View.generateViewId()
        chipBinding.root.id = id
        chipBinding.root.text = SampleRates.format(sampleRate)
        chipBinding.root.layoutDirection = View.LAYOUT_DIRECTION_LOCALE
        binding.sampleRateGroup.addView(chipBinding.root)
        chipIdToSampleRate[id] = sampleRate
        sampleRateToChipId[sampleRate] = id
    }

    /**
     * Update UI based on currently selected format in the preferences.
     *
     * Calls [refreshParam] via [onCheckedChanged].
     */
    private fun refreshFormat() {
        val (format, _) = Formats.fromPreferences(requireContext())
        binding.nameGroup.check(formatToChipId[format]!!)
    }

    private fun refreshSampleRate() {
        val sampleRate = SampleRates.fromPreferences(requireContext())
        binding.sampleRateGroup.check(sampleRateToChipId[sampleRate]!!)
    }

    /**
     * Update parameter title and slider to match format parameter specifications.
     */
    private fun refreshParam() {
        val (format, param) = Formats.fromPreferences(requireContext())
        formatParamInfo = format.paramInfo

        when (val info = format.paramInfo) {
            is RangedParamInfo -> {
                binding.paramGroup.isVisible = true

                binding.paramTitle.setText(when (info.type) {
                    RangedParamType.CompressionLevel -> R.string.bottom_sheet_compression_level
                    RangedParamType.Bitrate -> R.string.bottom_sheet_bitrate
                })

                binding.paramSlider.valueFrom = info.range.first.toFloat()
                binding.paramSlider.valueTo = info.range.last.toFloat()
                binding.paramSlider.stepSize = info.stepSize.toFloat()
                binding.paramSlider.value = (param ?: info.default).toFloat()
            }
            NoParamInfo -> {
                binding.paramGroup.isVisible = false

                // Needed due to a bug in the material3 library where the slider label does not disappear
                // when the slider visibility is set to View.GONE
                // https://github.com/material-components/material-components-android/issues/2726
                val ensureLabelsRemoved = binding.paramSlider.javaClass.superclass
                    .getDeclaredMethod("ensureLabelsRemoved")
                ensureLabelsRemoved.isAccessible = true
                ensureLabelsRemoved.invoke(binding.paramSlider)
            }
        }
    }

    override fun onCheckedChanged(group: ChipGroup, checkedIds: MutableList<Int>) {
        val context = requireContext()

        when (group) {
            binding.nameGroup -> {
                Preferences.setFormatName(context, chipIdToFormat[checkedIds.first()]!!.name)
                refreshParam()
            }
            binding.sampleRateGroup -> {
                Preferences.setSampleRate(context, chipIdToSampleRate[checkedIds.first()])
            }
        }
    }

    override fun onValueChange(slider: Slider, value: Float, fromUser: Boolean) {
        when (slider) {
            binding.paramSlider -> {
                val format = chipIdToFormat[binding.nameGroup.checkedChipId]!!
                Preferences.setFormatParam(requireContext(), format.name, value.toUInt())
            }
        }
    }

    override fun onClick(v: View?) {
        when (v) {
            binding.reset -> {
                val context = requireContext()
                Preferences.resetAllFormats(context)
                Preferences.setSampleRate(context, null)
                refreshFormat()
                // Need to explicitly refresh the parameter when the default format is already chosen
                refreshParam()
                refreshSampleRate()
            }
        }
    }

    companion object {
        val TAG: String = FormatBottomSheetFragment::class.java.simpleName
    }
}