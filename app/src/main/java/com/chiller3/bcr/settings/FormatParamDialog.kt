/*
 * SPDX-FileCopyrightText: 2023-2026 Andrew Gunnerson
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.core.text.isDigitsOnly
import com.chiller3.bcr.R
import com.chiller3.bcr.format.Format
import com.chiller3.bcr.format.RangedParamInfo
import com.chiller3.bcr.format.RangedParamType

@Composable
fun FormatParamDialog(
    format: Format,
    onSelect: (UInt) -> Unit,
    onDismiss: () -> Unit,
) {
    val resources = LocalResources.current

    val paramInfo = format.paramInfo
    if (paramInfo !is RangedParamInfo) {
        throw IllegalStateException("Selected format is not configurable")
    }

    val multiplier = remember(paramInfo) {
        when (paramInfo.type) {
            RangedParamType.CompressionLevel -> 1U
            RangedParamType.Bitrate -> {
                if (paramInfo.range.first % 1_000U == 0U && paramInfo.range.last % 1_000U == 0U) {
                    1000U
                } else {
                    1U
                }
            }
        }
    }

    var input by rememberSaveable { mutableStateOf("") }
    val value = tryParseInput(paramInfo, multiplier, input)
    val settings = formatParamTextFieldSettings(paramInfo, multiplier)

    AlertDialog(
        title = { Text(text = stringResource(R.string.format_param_dialog_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(state = rememberScrollState())) {
                val message = stringResource(
                    R.string.format_param_dialog_message,
                    paramInfo.format(resources, paramInfo.range.first),
                    paramInfo.format(resources, paramInfo.range.last),
                )
                Text(text = message)

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
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { onSelect(value!!) },
                enabled = value != null,
            ) {
                Text(text = stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
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
private fun tryParseInput(paramInfo: RangedParamInfo, multiplier: UInt, input: String): UInt? {
    if (input.isNotEmpty()) {
        try {
            val newValue = input.toUInt().times(multiplier.toULong())
            if (newValue in paramInfo.range) {
                return newValue.toUInt()
            }
        } catch (_: NumberFormatException) {
            // Ignore
        }
    }

    return null
}

private data class FormatParamTextFieldSettings(
    val prefix: String?,
    val suffix: String?,
    val textAlign: TextAlign,
)

@Composable
private fun formatParamTextFieldSettings(
    paramInfo: RangedParamInfo,
    multiplier: UInt,
): FormatParamTextFieldSettings {
    // Try to detect if the displayed format is a prefix or suffix since it's not the same in every
    // language (eg. "Level 8" vs "8级")
    val translated = when (paramInfo.type) {
        RangedParamType.CompressionLevel ->
            stringResource(R.string.format_param_compression_level, "\u0000")
        RangedParamType.Bitrate -> if (multiplier == 1_000U) {
            stringResource(R.string.format_param_bitrate_kbps, "\u0000")
        } else {
            stringResource(R.string.format_param_bitrate_bps, "\u0000")
        }
    }
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

    return FormatParamTextFieldSettings(prefix, suffix, textAlign)
}
