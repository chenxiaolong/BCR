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
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.then
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.core.text.isDigitsOnly
import com.chiller3.bcr.R

@Composable
fun MinDurationDialog(
    minDuration: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val input = rememberTextFieldState(initialText = minDuration.toString())
    val minDuration = tryParseInput(input.text.toString())
    val supportingText = minDuration?.let {
        pluralStringResource(R.plurals.min_duration_dialog_seconds, it, it)
    }

    AlertDialog(
        title = { Text(text = stringResource(R.string.min_duration_dialog_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(state = rememberScrollState())) {
                Text(text = stringResource(R.string.min_duration_dialog_message))

                OutlinedTextField(
                    state = input,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    supportingText = { supportingText?.let { Text(text = it) } },
                    inputTransformation = InputTransformation.then {
                        if (!asCharSequence().isDigitsOnly()) {
                            revertAllChanges()
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    lineLimits = TextFieldLineLimits.SingleLine,
                )
            }
        },
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { onSelect(minDuration!!) },
                enabled = minDuration != null,
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

private fun tryParseInput(input: String): Int? {
    if (input.isEmpty()) {
        return 0
    } else {
        try {
            val seconds = input.toInt()
            if (seconds >= 0) {
                return seconds
            }
        } catch (_: NumberFormatException) {
            // Ignore.
        }
    }

    return null
}
