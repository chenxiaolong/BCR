/*
 * SPDX-FileCopyrightText: 2024-2026 Andrew Gunnerson
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.chiller3.bcr.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.core.text.isDigitsOnly
import com.chiller3.bcr.R
import com.chiller3.bcr.format.Format
import com.chiller3.bcr.format.RangedSampleRateInfo
import com.chiller3.bcr.format.SampleRateInfo

@Composable
fun FormatSampleRateDialog(
    format: Format,
    onSelected: (UInt) -> Unit,
    onDismissed: () -> Unit,
) {
    val sampleRateInfo = format.sampleRateInfo
    if (sampleRateInfo !is RangedSampleRateInfo) {
        throw IllegalStateException("Selected format is not configurable")
    }

    var input by rememberSaveable { mutableStateOf("") }
    val value = tryParseInput(sampleRateInfo, input)
    val settings = formatSampleRateTextFieldSettings()

    AlertDialog(
        title = { Text(text = stringResource(R.string.format_sample_rate_dialog_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(state = rememberScrollState())) {
                Text(text = sampleRateMessage(sampleRateInfo))

                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    value = input,
                    onValueChange = { if (it.isDigitsOnly()) input = it },
                    textStyle = LocalTextStyle.current.copy(textAlign = settings.textAlign),
                    prefix = {
                        if (settings.prefix != null) {
                            Text(text = settings.prefix)
                        }
                    },
                    suffix = {
                        if (settings.suffix != null) {
                            Text(text = settings.suffix)
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        },
        onDismissRequest = { onDismissed() },
        confirmButton = {
            TextButton(
                onClick = { onSelected(value!!) },
                enabled = value != null,
            ) {
                Text(text = stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = { onDismissed() }) {
                Text(text = stringResource(android.R.string.cancel))
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
    )
}

@Composable
private fun sampleRateMessage(sampleRateInfo: RangedSampleRateInfo) = buildAnnotatedString {
    val resources = LocalResources.current

    append(stringResource(R.string.format_sample_rate_dialog_message_desc))

    withBulletList {
        for (range in sampleRateInfo.ranges) {
            withBulletListItem {
                append(resources.getString(
                    R.string.format_sample_rate_dialog_message_range,
                    sampleRateInfo.format(resources, range.first),
                    sampleRateInfo.format(resources, range.last),
                ))
            }
        }
    }
}

@Composable
private fun tryParseInput(sampleRateInfo: SampleRateInfo, input: String): UInt? {
    if (input.isNotEmpty()) {
        try {
            return input.toUInt().apply {
                sampleRateInfo.validate(this)
            }
        } catch (_: NumberFormatException) {
            // Ignore
        } catch (_: IllegalArgumentException) {
            // Ignore
        }
    }

    return null
}

private data class FormatSampleRateTextFieldSettings(
    val prefix: String?,
    val suffix: String?,
    val textAlign: TextAlign,
)

@Composable
private fun formatSampleRateTextFieldSettings(): FormatSampleRateTextFieldSettings {
    // Try to detect if the displayed format is a prefix or suffix since it may not be the same in
    // every language.
    val translated = stringResource(R.string.format_sample_rate, "\u0000")
    val placeholder = translated.indexOf('\u0000')
    val hasPrefix = placeholder > 0
    val hasSuffix = placeholder < translated.length - 1
    var prefix: String? = null
    var suffix: String? = null
    var textAlign = TextAlign.Unspecified

    if (hasPrefix) {
        prefix = translated.take(placeholder).trimEnd()
    }
    if (hasSuffix) {
        suffix = translated.substring(placeholder + 1).trimStart()
    }
    if (hasPrefix && hasSuffix) {
        textAlign = TextAlign.Center
    } else if (hasSuffix) {
        textAlign = TextAlign.End
    }

    return FormatSampleRateTextFieldSettings(prefix, suffix, textAlign)
}
