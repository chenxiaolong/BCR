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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.core.text.isDigitsOnly
import com.chiller3.bcr.R
import com.chiller3.bcr.output.DaysRetention
import com.chiller3.bcr.output.NoRetention
import com.chiller3.bcr.output.Retention

@Composable
fun FileRetentionDialog(
    retention: Retention,
    onSelect: (Retention) -> Unit,
    onDismiss: () -> Unit,
) {
    val resources = LocalResources.current

    var input by rememberSaveable(retention) {
        mutableStateOf(
            when (retention) {
                is DaysRetention -> retention.days.toString()
                NoRetention -> ""
            }
        )
    }
    val retention = tryParseInput(input)
    val supportingText = remember(retention) {
        when (retention) {
            is RetentionParse.Value -> retention.retention.toFormattedString(resources)
            is RetentionParse.Error -> retention.message
        }
    }

    AlertDialog(
        title = { Text(text = stringResource(R.string.file_retention_dialog_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(state = rememberScrollState())) {
                Text(text = stringResource(R.string.file_retention_dialog_message))

                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    value = input,
                    onValueChange = { if (it.isDigitsOnly()) input = it },
                    isError = retention is RetentionParse.Error,
                    supportingText = { Text(text = supportingText) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        },
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { onSelect((retention as RetentionParse.Value).retention) },
                enabled = retention is RetentionParse.Value,
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

private sealed interface RetentionParse {
    data class Value(val retention: Retention) : RetentionParse

    data class Error(val message: String) : RetentionParse
}

@Composable
private fun tryParseInput(input: String): RetentionParse =
    if (input.isEmpty()) {
        RetentionParse.Value(NoRetention)
    } else {
        try {
            val days = input.toUInt()
            if (days == 0U) {
                RetentionParse.Value(NoRetention)
            } else {
                RetentionParse.Value(DaysRetention(days))
            }
        } catch (_: NumberFormatException) {
            RetentionParse.Error(stringResource(R.string.file_retention_error_too_large))
        }
    }
