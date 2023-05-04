@file:OptIn(ExperimentalUnsignedTypes::class)

package com.chiller3.bcr.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.setFragmentResultListener
import com.chiller3.bcr.Preferences
import com.chiller3.bcr.R
import com.chiller3.bcr.databinding.BottomSheetChipBinding
import com.chiller3.bcr.databinding.OutputFormatBottomSheetBinding
import com.chiller3.bcr.dialog.FormatParamDialogFragment
import com.chiller3.bcr.format.*
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.ChipGroup

class OutputFormatBottomSheetFragment : BottomSheetDialogFragment(),
    ChipGroup.OnCheckedStateChangeListener, View.OnClickListener {
    private lateinit var binding: OutputFormatBottomSheetBinding
    private lateinit var prefs: Preferences

    private val chipIdToFormat = HashMap<Int, Format>()
    private val formatToChipId = HashMap<Format, Int>()

    private val chipIdToParam = HashMap<Int, UInt?>()
    private val paramToChipId = HashMap<UInt?, Int>()

    private val chipIdToSampleRate = HashMap<Int, SampleRate>()
    private val sampleRateToChipId = HashMap<SampleRate, Int>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = OutputFormatBottomSheetBinding.inflate(inflater, container, false)

        prefs = Preferences(requireContext())

        binding.reset.setOnClickListener(this)

        for (format in Format.all) {
            if (!format.supported) {
                continue
            }

            addFormatChip(inflater, format)
        }

        binding.nameGroup.setOnCheckedStateChangeListener(this)

        binding.paramGroup.setOnCheckedStateChangeListener(this)

        for (sampleRate in SampleRate.all) {
            addSampleRateChip(inflater, sampleRate)
        }

        binding.sampleRateGroup.setOnCheckedStateChangeListener(this)

        setFragmentResultListener(FormatParamDialogFragment.TAG) { _, _ ->
            refreshParam()
        }

        refreshFormat()
        refreshParam()
        refreshSampleRate()

        return binding.root
    }

    private fun addChip(inflater: LayoutInflater, parent: ViewGroup): BottomSheetChipBinding {
        val chipBinding = BottomSheetChipBinding.inflate(inflater, parent, false)
        val id = View.generateViewId()
        chipBinding.root.id = id
        chipBinding.root.layoutDirection = View.LAYOUT_DIRECTION_LOCALE
        parent.addView(chipBinding.root)
        return chipBinding
    }

    private fun addFormatChip(inflater: LayoutInflater, format: Format) {
        val chipBinding = addChip(inflater, binding.nameGroup)
        chipBinding.root.text = format.name
        chipIdToFormat[chipBinding.root.id] = format
        formatToChipId[format] = chipBinding.root.id
    }

    private fun addParamChip(inflater: LayoutInflater, paramInfo: FormatParamInfo, value: UInt?,
                             canClose: Boolean) {
        val chipBinding = addChip(inflater, binding.paramGroup)
        if (canClose) {
            chipBinding.root.isCloseIconVisible = true
            chipBinding.root.setOnCloseIconClickListener(::onChipClosed)
        }
        if (value != null) {
            chipBinding.root.text = paramInfo.format(requireContext(), value)
        } else {
            chipBinding.root.setText(R.string.output_format_bottom_sheet_custom_param)
        }
        chipIdToParam[chipBinding.root.id] = value
        paramToChipId[value] = chipBinding.root.id
    }

    private fun addSampleRateChip(inflater: LayoutInflater, sampleRate: SampleRate) {
        val chipBinding = addChip(inflater, binding.sampleRateGroup)
        chipBinding.root.text = sampleRate.toString()
        chipIdToSampleRate[chipBinding.root.id] = sampleRate
        sampleRateToChipId[sampleRate] = chipBinding.root.id
    }

    /**
     * Update UI based on currently selected format in the preferences.
     *
     * Calls [refreshParam] via [onCheckedChanged].
     */
    private fun refreshFormat() {
        val (format, _) = Format.fromPreferences(prefs)
        binding.nameGroup.check(formatToChipId[format]!!)
    }

    private fun refreshParam() {
        val (format, param) = Format.fromPreferences(prefs)
        val selectedParam = param ?: format.paramInfo.default

        chipIdToParam.clear()
        paramToChipId.clear()
        binding.paramGroup.removeAllViews()

        when (val info = format.paramInfo) {
            is RangedParamInfo -> {
                binding.paramLayout.isVisible = true

                binding.paramTitle.setText(when (info.type) {
                    RangedParamType.CompressionLevel -> R.string.output_format_bottom_sheet_compression_level
                    RangedParamType.Bitrate -> R.string.output_format_bottom_sheet_bitrate
                })

                for (preset in format.paramInfo.presets) {
                    addParamChip(layoutInflater, format.paramInfo, preset, false)
                }

                if (selectedParam !in format.paramInfo.presets) {
                    // TODO: Cancellable
                    addParamChip(layoutInflater, format.paramInfo, selectedParam, true)
                } else {
                    // TODO: New custom
                    addParamChip(layoutInflater, format.paramInfo, null, false)
                }

                binding.paramGroup.check(paramToChipId[selectedParam]!!)
            }
            NoParamInfo -> {
                binding.paramLayout.isVisible = false
            }
        }
    }

    private fun refreshSampleRate() {
        val sampleRate = SampleRate.fromPreferences(prefs)
        binding.sampleRateGroup.check(sampleRateToChipId[sampleRate]!!)
    }

    private fun onChipClosed(chip: View) {
        if (chip.id in chipIdToParam) {
            val format = chipIdToFormat[binding.nameGroup.checkedChipId]!!
            prefs.setFormatParam(format, null)
            refreshParam()
        }
    }

    override fun onCheckedChanged(group: ChipGroup, checkedIds: MutableList<Int>) {
        when (group) {
            binding.nameGroup -> {
                prefs.format = chipIdToFormat[checkedIds.first()]!!
                refreshParam()
            }
            binding.paramGroup -> {
                val format = chipIdToFormat[binding.nameGroup.checkedChipId]!!
                val param = chipIdToParam[checkedIds.first()]
                if (param != null) {
                    prefs.setFormatParam(format, param)
                } else {
                    FormatParamDialogFragment().show(
                        parentFragmentManager.beginTransaction(), FormatParamDialogFragment.TAG)
                }
            }
            binding.sampleRateGroup -> {
                prefs.sampleRate = chipIdToSampleRate[checkedIds.first()]!!
            }
        }
    }

    override fun onClick(v: View?) {
        when (v) {
            binding.reset -> {
                prefs.resetAllFormats()
                prefs.sampleRate = null
                refreshFormat()
                // Need to explicitly refresh the parameter when the default format is already chosen
                refreshParam()
                refreshSampleRate()
            }
        }
    }

    companion object {
        val TAG: String = OutputFormatBottomSheetFragment::class.java.simpleName
    }
}
