/*
 * SPDX-FileCopyrightText: 2026 Andrew Gunnerson
 * SPDX-License-Identifier: GPL-3.0-only
 */

@file:OptIn(ExperimentalUnsignedTypes::class)

package com.chiller3.bcr.settings

import android.os.Parcelable
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import com.chiller3.bcr.R
import com.chiller3.bcr.format.AudioSource
import com.chiller3.bcr.format.Format
import com.chiller3.bcr.format.RangedParamInfo
import com.chiller3.bcr.format.RangedParamType
import com.chiller3.bcr.format.RangedSampleRateInfo
import com.chiller3.bcr.ui.Preference
import com.chiller3.bcr.ui.PreferenceColumn
import com.chiller3.bcr.ui.betterSegmentedShapes
import kotlinx.parcelize.Parcelize

@Parcelize
sealed interface FormatChoiceAction : Parcelable {
    @Parcelize
    data object SelectFormat : FormatChoiceAction

    @Parcelize
    data class SelectParam(val formatIndex: Int) : FormatChoiceAction {
        constructor(format: Format) : this(Format.all.indexOf(format))
    }

    @Parcelize
    data class SelectSampleRate(val formatIndex: Int) : FormatChoiceAction {
        constructor(format: Format) : this(Format.all.indexOf(format))
    }

    @Parcelize
    data class SelectAudioSource(val formatIndex: Int) : FormatChoiceAction {
        constructor(format: Format) : this(Format.all.indexOf(format))
    }
}

@Parcelize
sealed interface FormatChoiceResult : Parcelable {
    val format: Format

    @Parcelize
    data class SelectedFormat(val formatIndex: Int) : FormatChoiceResult {
        override val format: Format
            get() = Format.all[formatIndex]
    }

    @Parcelize
    data class SelectedParam(val formatIndex: Int, val param: UInt?) : FormatChoiceResult {
        override val format: Format
            get() = Format.all[formatIndex]
    }

    @Parcelize
    data class SelectedSampleRate(
        val formatIndex: Int,
        val sampleRate: UInt?,
    ) : FormatChoiceResult {
        override val format: Format
            get() = Format.all[formatIndex]
    }

    @Parcelize
    data class SelectedAudioSource(
        val formatIndex: Int,
        val audioSource: AudioSource,
    ) : FormatChoiceResult {
        override val format: Format
            get() = Format.all[formatIndex]
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FormatChoiceDialog(
    action: FormatChoiceAction,
    onSelected: (FormatChoiceResult) -> Unit,
    onDismissed: () -> Unit,
) {
    val choices = actionChoices(action)

    AlertDialog(
        title = { Text(text = actionTitle(action)) },
        text = {
            PreferenceColumn(fillScreen = false) {
                itemsIndexed(choices) { index, (result, text) ->
                    Preference(
                        onClick = { onSelected(result) },
                        shapes = betterSegmentedShapes(index = index, count = choices.size),
                        title = { Text(text = text) },
                    )
                }
            }
        },
        onDismissRequest = { onDismissed() },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = { onDismissed() }) {
                Text(text = stringResource(android.R.string.cancel))
            }
        },
    )
}

@Composable
private fun actionTitle(action: FormatChoiceAction) = when (action) {
    FormatChoiceAction.SelectFormat -> stringResource(R.string.pref_output_format_name)
    is FormatChoiceAction.SelectParam -> {
        val paramInfo = Format.all[action.formatIndex].paramInfo

        if (paramInfo is RangedParamInfo) {
            when (paramInfo.type) {
                RangedParamType.CompressionLevel ->
                    stringResource(R.string.pref_format_param_desc_compression_level)
                RangedParamType.Bitrate -> stringResource(R.string.pref_format_param_desc_bitrate)
            }
        } else {
            throw IllegalArgumentException("Cannot select param for $paramInfo")
        }
    }
    is FormatChoiceAction.SelectSampleRate -> stringResource(R.string.pref_sample_rate_name)
    is FormatChoiceAction.SelectAudioSource -> stringResource(R.string.pref_audio_source_name)
}

@Composable
private fun actionChoices(action: FormatChoiceAction) =
    mutableListOf<Pair<FormatChoiceResult, String>>().apply {
        val resources = LocalResources.current

        when (val action = action) {
            FormatChoiceAction.SelectFormat -> {
                for ((i, format) in Format.all.withIndex()) {
                    add(FormatChoiceResult.SelectedFormat(i) to format.name)
                }
            }
            is FormatChoiceAction.SelectParam -> {
                val format = Format.all[action.formatIndex]

                for (preset in format.paramInfo.presets) {
                    add(FormatChoiceResult.SelectedParam(action.formatIndex, preset)
                            to format.paramInfo.format(resources, preset))
                }

                if (format.paramInfo is RangedParamInfo) {
                    add(FormatChoiceResult.SelectedParam(action.formatIndex, null)
                            to stringResource(R.string.custom_param_value))
                }
            }
            is FormatChoiceAction.SelectSampleRate -> {
                val format = Format.all[action.formatIndex]

                for (preset in format.sampleRateInfo.presets) {
                    add(FormatChoiceResult.SelectedSampleRate(action.formatIndex, preset)
                            to format.sampleRateInfo.format(resources, preset))
                }

                if (format.sampleRateInfo is RangedSampleRateInfo) {
                    add(FormatChoiceResult.SelectedSampleRate(action.formatIndex, null)
                            to stringResource(R.string.custom_param_value))
                }
            }
            is FormatChoiceAction.SelectAudioSource -> {
                val format = Format.all[action.formatIndex]

                for (audioSource in AudioSource.entries) {
                    if (!audioSource.isStereo || format.supportsStereo) {
                        add(FormatChoiceResult.SelectedAudioSource(action.formatIndex, audioSource)
                                to stringResource(audioSource.nameResId))
                    }
                }
            }
        }
    }
