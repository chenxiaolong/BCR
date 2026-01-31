/*
 * SPDX-FileCopyrightText: 2022-2026 Andrew Gunnerson
 * SPDX-License-Identifier: GPL-3.0-only
 */

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
import com.chiller3.bcr.dialog.FormatSampleRateDialogFragment
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

    private val chipIdToSampleRate = HashMap<Int, UInt?>()
    private val sampleRateToChipId = HashMap<UInt?, Int>()

    private val chipIdToChannels = HashMap<Int, Boolean>()
    private val channelsToChipId = HashMap<Boolean, Int>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = OutputFormatBottomSheetBinding.inflate(inflater, container, false)

        prefs = Preferences(requireContext())

        binding.reset.setOnClickListener(this)

        for (format in Format.all) {
            addFormatChip(inflater, format)
        }

        binding.nameGroup.setOnCheckedStateChangeListener(this)

        binding.paramGroup.setOnCheckedStateChangeListener(this)

        binding.sampleRateGroup.setOnCheckedStateChangeListener(this)

        binding.channelsGroup.setOnCheckedStateChangeListener(this)

        setFragmentResultListener(FormatParamDialogFragment.TAG) { _, _ ->
            refreshParam()
        }

        setFragmentResultListener(FormatSampleRateDialogFragment.TAG) { _, _ ->
            refreshSampleRate()
        }

        refreshFormat()
        refreshParam()
        refreshSampleRate()
        refreshChannels()
        refreshStereoWarning()

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

    private fun addSampleRateChip(inflater: LayoutInflater, sampleRateInfo: SampleRateInfo,
                                  rate: UInt?, canClose: Boolean) {
        val chipBinding = addChip(inflater, binding.sampleRateGroup)
        if (canClose) {
            chipBinding.root.isCloseIconVisible = true
            chipBinding.root.setOnCloseIconClickListener(::onChipClosed)
        }
        if (rate != null) {
            chipBinding.root.text = sampleRateInfo.format(requireContext(), rate)
        } else {
            chipBinding.root.setText(R.string.output_format_bottom_sheet_custom_param)
        }
        chipIdToSampleRate[chipBinding.root.id] = rate
        sampleRateToChipId[rate] = chipBinding.root.id
    }

    private fun addChannelsChip(inflater: LayoutInflater, stereo: Boolean) {
        val chipBinding = addChip(inflater, binding.channelsGroup)

        if (stereo) {
            chipBinding.root.setText(R.string.channels_stereo)
        } else {
            chipBinding.root.setText(R.string.channels_mono)
        }

        chipIdToChannels[chipBinding.root.id] = stereo
        channelsToChipId[stereo] = chipBinding.root.id
    }

    /**
     * Update UI based on currently selected format in the preferences.
     *
     * Updates dependent views via [onCheckedChanged].
     */
    private fun refreshFormat() {
        val savedFormat = Format.fromPreferences(prefs)
        binding.nameGroup.check(formatToChipId[savedFormat.format]!!)
    }

    private fun refreshParam() {
        val savedFormat = Format.fromPreferences(prefs)
        val selectedParam = savedFormat.param ?: savedFormat.format.paramInfo.default

        chipIdToParam.clear()
        paramToChipId.clear()
        binding.paramGroup.removeAllViews()

        when (val info = savedFormat.format.paramInfo) {
            is RangedParamInfo -> {
                binding.paramLayout.isVisible = true

                binding.paramTitle.setText(when (info.type) {
                    RangedParamType.CompressionLevel -> R.string.output_format_bottom_sheet_compression_level
                    RangedParamType.Bitrate -> R.string.output_format_bottom_sheet_bitrate
                })

                for (preset in savedFormat.format.paramInfo.presets) {
                    addParamChip(layoutInflater, savedFormat.format.paramInfo, preset, false)
                }

                if (selectedParam !in savedFormat.format.paramInfo.presets) {
                    addParamChip(layoutInflater, savedFormat.format.paramInfo, selectedParam, true)
                } else {
                    addParamChip(layoutInflater, savedFormat.format.paramInfo, null, false)
                }

                binding.paramGroup.check(paramToChipId[selectedParam]!!)
            }
            NoParamInfo -> {
                binding.paramLayout.isVisible = false
            }
        }
    }

    private fun refreshSampleRate() {
        val savedFormat = Format.fromPreferences(prefs)
        val selectedSampleRate = savedFormat.sampleRate ?: savedFormat.format.sampleRateInfo.default

        chipIdToSampleRate.clear()
        sampleRateToChipId.clear()
        binding.sampleRateGroup.removeAllViews()

        for (preset in savedFormat.format.sampleRateInfo.presets) {
            addSampleRateChip(layoutInflater, savedFormat.format.sampleRateInfo, preset, false)
        }

        if (savedFormat.format.sampleRateInfo is RangedSampleRateInfo) {
            if (selectedSampleRate !in savedFormat.format.sampleRateInfo.presets) {
                addSampleRateChip(layoutInflater, savedFormat.format.sampleRateInfo, selectedSampleRate, true)
            } else {
                addSampleRateChip(layoutInflater, savedFormat.format.sampleRateInfo, null, false)
            }
        }

        binding.sampleRateGroup.check(sampleRateToChipId[selectedSampleRate]!!)
    }

    private fun refreshChannels() {
        val savedFormat = Format.fromPreferences(prefs)

        chipIdToChannels.clear()
        channelsToChipId.clear()
        binding.channelsGroup.removeAllViews()

        addChannelsChip(layoutInflater, false)

        if (savedFormat.format.supportsStereo) {
            addChannelsChip(layoutInflater, true)
        }

        binding.channelsGroup.check(channelsToChipId[savedFormat.stereo]!!)
    }

    private fun refreshStereoWarning() {
        val savedFormat = Format.fromPreferences(prefs)

        binding.stereoWarning.isVisible = savedFormat.stereo
    }

    private fun onChipClosed(chip: View) {
        if (chip.id in chipIdToParam) {
            val format = chipIdToFormat[binding.nameGroup.checkedChipId]!!
            prefs.setFormatParam(format, null)
            refreshParam()
        } else if (chip.id in chipIdToSampleRate) {
            val format = chipIdToFormat[binding.nameGroup.checkedChipId]!!
            prefs.setFormatSampleRate(format, null)
            refreshSampleRate()
        }
    }

    override fun onCheckedChanged(group: ChipGroup, checkedIds: MutableList<Int>) {
        when (group) {
            binding.nameGroup -> {
                prefs.format = chipIdToFormat[checkedIds.first()]!!
                refreshParam()
                refreshSampleRate()
                refreshChannels()
                refreshStereoWarning()
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
                val format = chipIdToFormat[binding.nameGroup.checkedChipId]!!
                val sampleRate = chipIdToSampleRate[checkedIds.first()]
                if (sampleRate != null) {
                    prefs.setFormatSampleRate(format, sampleRate)
                } else {
                    FormatSampleRateDialogFragment().show(
                        parentFragmentManager.beginTransaction(), FormatSampleRateDialogFragment.TAG)
                }
            }
            binding.channelsGroup -> {
                prefs.stereo = chipIdToChannels[binding.channelsGroup.checkedChipId]!!
                refreshStereoWarning()
            }
        }
    }

    override fun onClick(v: View?) {
        when (v) {
            binding.reset -> {
                prefs.resetAllFormats()
                refreshFormat()
                // Need to explicitly refresh the parameter when the default format is already chosen
                refreshParam()
                refreshSampleRate()
                refreshChannels()
                refreshStereoWarning()
            }
        }
    }

    companion object {
        val TAG: String = OutputFormatBottomSheetFragment::class.java.simpleName
    }
}
