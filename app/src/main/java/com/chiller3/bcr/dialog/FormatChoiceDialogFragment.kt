/*
 * SPDX-FileCopyrightText: 2026 Andrew Gunnerson
 * SPDX-License-Identifier: GPL-3.0-only
 */

@file:OptIn(ExperimentalUnsignedTypes::class)

package com.chiller3.bcr.dialog

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.os.Parcelable
import androidx.core.os.BundleCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import com.chiller3.bcr.R
import com.chiller3.bcr.format.AudioSource
import com.chiller3.bcr.format.Format
import com.chiller3.bcr.format.RangedParamInfo
import com.chiller3.bcr.format.RangedParamType
import com.chiller3.bcr.format.RangedSampleRateInfo
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.parcelize.Parcelize

open class FormatChoiceDialogFragment : DialogFragment() {
    companion object {
        val TAG: String = FormatChoiceDialogFragment::class.java.simpleName

        private const val ARG_ACTION = "action"
        const val RESULT_RESULT = "result"

        fun newInstance(action: Action) =
            FormatChoiceDialogFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_ACTION, action)
                }
            }
    }

    @Parcelize
    sealed interface Action : Parcelable {
        @Parcelize
        data object SelectFormat : Action

        @Parcelize
        data class SelectParam(val formatIndex: Int) : Action {
            constructor(format: Format) : this(Format.all.indexOf(format))
        }

        @Parcelize
        data class SelectSampleRate(val formatIndex: Int) : Action {
            constructor(format: Format) : this(Format.all.indexOf(format))
        }

        @Parcelize
        data class SelectAudioSource(val formatIndex: Int) : Action {
            constructor(format: Format) : this(Format.all.indexOf(format))
        }
    }

    @Parcelize
    sealed interface Result : Parcelable {
        val format: Format

        @Parcelize
        data class SelectedFormat(val formatIndex: Int) : Result {
            override val format: Format
                get() = Format.all[formatIndex]
        }

        @Parcelize
        data class SelectedParam(val formatIndex: Int, val param: UInt?) : Result {
            override val format: Format
                get() = Format.all[formatIndex]
        }

        @Parcelize
        data class SelectedSampleRate(val formatIndex: Int, val sampleRate: UInt?) : Result {
            override val format: Format
                get() = Format.all[formatIndex]
        }

        @Parcelize
        data class SelectedAudioSource(val formatIndex: Int, val audioSource: AudioSource) : Result {
            override val format: Format
                get() = Format.all[formatIndex]
        }
    }

    private val action by lazy {
        BundleCompat.getParcelable(requireArguments(), ARG_ACTION, Action::class.java)!!
    }
    private val items by lazy {
        mutableListOf<Pair<Result, String>>().apply {
            val context = requireContext()

            when (val action = action) {
                Action.SelectFormat -> {
                    for ((i, format) in Format.all.withIndex()) {
                        add(Result.SelectedFormat(i) to format.name)
                    }
                }
                is Action.SelectParam -> {
                    val format = Format.all[action.formatIndex]

                    for (preset in format.paramInfo.presets) {
                        add(Result.SelectedParam(action.formatIndex, preset)
                                to format.paramInfo.format(context, preset))
                    }

                    if (format.paramInfo is RangedParamInfo) {
                        add(Result.SelectedParam(action.formatIndex, null)
                                to getString(R.string.custom_param_value))
                    }
                }
                is Action.SelectSampleRate -> {
                    val format = Format.all[action.formatIndex]

                    for (preset in format.sampleRateInfo.presets) {
                        add(Result.SelectedSampleRate(action.formatIndex, preset)
                                to format.sampleRateInfo.format(context, preset))
                    }

                    if (format.sampleRateInfo is RangedSampleRateInfo) {
                        add(Result.SelectedSampleRate(action.formatIndex, null)
                                to getString(R.string.custom_param_value))
                    }
                }
                is Action.SelectAudioSource -> {
                    val format = Format.all[action.formatIndex]

                    for (audioSource in AudioSource.entries) {
                        if (!audioSource.isStereo || format.supportsStereo) {
                            add(Result.SelectedAudioSource(action.formatIndex, audioSource)
                                    to getString(audioSource.nameResId))
                        }
                    }
                }
            }
        }
    }

    private var result: Result? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val title = when (val action = action) {
            Action.SelectFormat -> R.string.pref_output_format_name
            is Action.SelectParam -> {
                val paramInfo = Format.all[action.formatIndex].paramInfo

                if (paramInfo is RangedParamInfo) {
                    when (paramInfo.type) {
                        RangedParamType.CompressionLevel ->
                            R.string.pref_format_param_desc_compression_level
                        RangedParamType.Bitrate -> R.string.pref_format_param_desc_bitrate
                    }
                } else {
                    throw IllegalArgumentException("Cannot select param for $paramInfo")
                }
            }
            is Action.SelectSampleRate -> R.string.pref_sample_rate_name
            is Action.SelectAudioSource -> R.string.pref_audio_source_name
        }

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setItems(items.map { it.second }.toTypedArray()) { _, i ->
                result = items[i].first
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)

        setFragmentResult(tag!!, Bundle().apply { putParcelable(RESULT_RESULT, result) })
    }
}
