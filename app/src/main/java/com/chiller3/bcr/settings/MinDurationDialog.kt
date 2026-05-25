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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
    onSelected: (Int) -> Unit,
    onDismissed: () -> Unit,
) {
    var input by rememberSaveable(minDuration) { mutableStateOf(minDuration.toString()) }
    val minDuration = tryParseInput(input)
    val supportingText = minDuration?.let {
        pluralStringResource(R.plurals.min_duration_dialog_seconds, it, it)
    }

    AlertDialog(
        title = { Text(text = stringResource(R.string.min_duration_dialog_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(state = rememberScrollState())) {
                Text(text = stringResource(R.string.min_duration_dialog_message))

                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    value = input,
                    onValueChange = { if (it.isDigitsOnly()) input = it },
                    supportingText = { supportingText?.let { Text(text = it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        },
        onDismissRequest = { onDismissed() },
        confirmButton = {
            TextButton(
                onClick = { onSelected(minDuration!!) },
                enabled = minDuration != null,
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
