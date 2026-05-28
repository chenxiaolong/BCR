/*
 * SPDX-FileCopyrightText: 2022-2026 Andrew Gunnerson
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.chiller3.bcr.settings

import android.content.res.Configuration
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.chiller3.bcr.Preferences
import com.chiller3.bcr.R
import com.chiller3.bcr.format.AudioSource
import com.chiller3.bcr.format.Format
import com.chiller3.bcr.format.NoParamInfo
import com.chiller3.bcr.format.RangedParamInfo
import com.chiller3.bcr.format.RangedParamType
import com.chiller3.bcr.ui.AppScreen
import com.chiller3.bcr.ui.BetterSegmentedShapes
import com.chiller3.bcr.ui.Preference
import com.chiller3.bcr.ui.PreferenceColumn
import com.chiller3.bcr.ui.theme.AppTheme

@Composable
fun OutputFormatScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { Preferences(context) }

    var reloadPrefs by remember { mutableIntStateOf(0) }
    val savedFormat = remember(reloadPrefs) { Format.fromPreferences(prefs) }

    AppScreen(
        title = { Text(text = stringResource(R.string.pref_output_format_name)) },
        onBack = onBack,
        onReset = {
            prefs.resetAllFormats()
            reloadPrefs++
        },
    ) { params ->
        OutputFormatContent(
            format = savedFormat.format,
            formatParam = savedFormat.param ?: savedFormat.format.paramInfo.default,
            sampleRate = savedFormat.sampleRate ?: savedFormat.format.sampleRateInfo.default,
            audioSource = savedFormat.audioSource,
            onFormatChange = { newFormat ->
                prefs.format = newFormat
                reloadPrefs++
            },
            onFormatReset = {
                prefs.format = null
                reloadPrefs++
            },
            onFormatParamChange = { newParam ->
                prefs.setFormatParam(savedFormat.format, newParam)
                reloadPrefs++
            },
            onFormatParamReset = {
                prefs.setFormatParam(savedFormat.format, null)
                reloadPrefs++
            },
            onSampleRateChange = { newSampleRate ->
                prefs.setFormatSampleRate(savedFormat.format, newSampleRate)
                reloadPrefs++
            },
            onSampleRateReset = {
                prefs.setFormatSampleRate(savedFormat.format, null)
                reloadPrefs++
            },
            onAudioSourceChange = { newAudioSource ->
                prefs.audioSource = newAudioSource
                reloadPrefs++
            },
            onAudioSourceReset = {
                prefs.audioSource = null
                reloadPrefs++
            },
            contentPadding = params.contentPadding,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun OutputFormatContent(
    format: Format,
    formatParam: UInt,
    sampleRate: UInt,
    audioSource: AudioSource,
    onFormatChange: (Format) -> Unit,
    onFormatReset: () -> Unit,
    onFormatParamChange: (UInt) -> Unit,
    onFormatParamReset: () -> Unit,
    onSampleRateChange: (UInt) -> Unit,
    onSampleRateReset: () -> Unit,
    onAudioSourceChange: (AudioSource) -> Unit,
    onAudioSourceReset: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val formatParamTitle = formatParamTitle(format)

    var showFormatChoiceDialog by rememberSaveable { mutableStateOf<FormatChoiceAction?>(null) }
    var showFormatParamDialog by rememberSaveable { mutableStateOf(false) }
    var showSampleRateDialog by rememberSaveable { mutableStateOf(false) }

    PreferenceColumn(contentPadding = contentPadding) {
        item(key = "output_format") {
            Preference(
                onClick = { showFormatChoiceDialog = FormatChoiceAction.SelectFormat },
                onLongClick = onFormatReset,
                shapes = BetterSegmentedShapes.top(),
                title = { Text(text = stringResource(R.string.pref_output_format_name)) },
                summary = { Text(text = format.name) },
                modifier = Modifier.animateItem(),
            )
        }

        formatParamTitle?.let { title ->
            item(key = "format_param") {
                Preference(
                    onClick = { showFormatChoiceDialog = FormatChoiceAction.SelectParam(format) },
                    onLongClick = onFormatParamReset,
                    shapes = BetterSegmentedShapes.middle(),
                    title = { Text(text = title) },
                    summary = { Text(text = formatParamSummary(format, formatParam)) },
                    modifier = Modifier.animateItem(),
                )
            }
        }

        item(key = "sample_rate") {
            Preference(
                onClick = { showFormatChoiceDialog = FormatChoiceAction.SelectSampleRate(format) },
                onLongClick = onSampleRateReset,
                shapes = BetterSegmentedShapes.middle(),
                title = { Text(text = stringResource(R.string.pref_sample_rate_name)) },
                summary = { Text(text = sampleRateSummary(format, sampleRate)) },
                modifier = Modifier.animateItem(),
            )
        }

        item(key = "audio_source") {
            Preference(
                onClick = { showFormatChoiceDialog = FormatChoiceAction.SelectAudioSource(format) },
                onLongClick = onAudioSourceReset,
                shapes = BetterSegmentedShapes.bottom(),
                title = { Text(text = stringResource(R.string.pref_audio_source_name)) },
                summary = { Text(text = audioSourceSummary(audioSource)) },
                modifier = Modifier.animateItem(),
            )
        }
    }

    showFormatChoiceDialog?.let { action ->
        FormatChoiceDialog(
            action = action,
            onSelect = { result ->
                when (result) {
                    is FormatChoiceResult.SelectedFormat -> { onFormatChange(result.format) }
                    is FormatChoiceResult.SelectedParam -> {
                        if (result.param != null) {
                            onFormatParamChange(result.param)
                        } else {
                            showFormatParamDialog = true
                        }
                    }
                    is FormatChoiceResult.SelectedSampleRate -> {
                        if (result.sampleRate != null) {
                            onSampleRateChange(result.sampleRate)
                        } else {
                            showSampleRateDialog = true
                        }
                    }
                    is FormatChoiceResult.SelectedAudioSource -> {
                        onAudioSourceChange(result.audioSource)
                    }
                }

                @Suppress("AssignedValueIsNeverRead")
                showFormatChoiceDialog = null
            },
            onDismiss = {
                @Suppress("AssignedValueIsNeverRead")
                showFormatChoiceDialog = null
            },
        )
    }

    if (showFormatParamDialog) {
        FormatParamDialog(
            format = format,
            onSelect = { newValue ->
                onFormatParamChange(newValue)
                @Suppress("AssignedValueIsNeverRead")
                showFormatParamDialog = false
            },
            onDismiss = {
                @Suppress("AssignedValueIsNeverRead")
                showFormatParamDialog = false
            }
        )
    }

    if (showSampleRateDialog) {
        FormatSampleRateDialog(
            format = format,
            onSelect = { newValue ->
                onSampleRateChange(newValue)
                @Suppress("AssignedValueIsNeverRead")
                showSampleRateDialog = false
            },
            onDismiss = {
                @Suppress("AssignedValueIsNeverRead")
                showSampleRateDialog = false
            }
        )
    }
}

@Composable
private fun formatParamTitle(format: Format) = when (val info = format.paramInfo) {
    is RangedParamInfo -> {
        when (info.type) {
            RangedParamType.CompressionLevel ->
                stringResource(R.string.pref_format_param_desc_compression_level)
            RangedParamType.Bitrate -> stringResource(R.string.pref_format_param_desc_bitrate)
        }
    }
    NoParamInfo -> null
}

@Composable
private fun formatParamSummary(format: Format, param: UInt) =
    format.paramInfo.format(LocalResources.current, param)

@Composable
private fun sampleRateSummary(format: Format, sampleRate: UInt) =
    format.sampleRateInfo.format(LocalResources.current, sampleRate)

@Composable
private fun audioSourceSummary(audioSource: AudioSource) = buildString {
    append(stringResource(audioSource.nameResId))

    if (audioSource.isStereo) {
        append("\n\n")
        append(stringResource(R.string.audio_source_stereo_warning))
    }
}

@Preview(
    name = "Light Mode",
    showBackground = true,
)
@Preview(
    name = "Dark Mode",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    showBackground = true,
)
@Composable
private fun PreviewOutputFormatScreen() {
    val format = Format.all.first()

    AppTheme {
        AppScreen(
            title = { Text(text = stringResource(R.string.pref_output_format_name)) },
            onBack = {},
            onReset = {},
        ) { params ->
            OutputFormatContent(
                format = format,
                formatParam = format.paramInfo.default,
                sampleRate = format.sampleRateInfo.default,
                audioSource = AudioSource.VOICE_UPLINK_DOWNLINK,
                onFormatChange = {},
                onFormatReset = {},
                onFormatParamChange = {},
                onFormatParamReset = {},
                onSampleRateChange = {},
                onSampleRateReset = {},
                onAudioSourceChange = {},
                onAudioSourceReset = {},
                contentPadding = params.contentPadding,
            )
        }
    }
}
