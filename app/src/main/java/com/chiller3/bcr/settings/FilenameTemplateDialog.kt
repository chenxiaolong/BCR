/*
 * SPDX-FileCopyrightText: 2023-2026 Andrew Gunnerson
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.chiller3.bcr.settings

import android.text.Annotation
import android.text.SpannedString
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.chiller3.bcr.BuildConfig
import com.chiller3.bcr.R
import com.chiller3.bcr.output.OutputFilenameGenerator
import com.chiller3.bcr.template.Template
import com.chiller3.bcr.template.TemplateSyntaxColors
import com.chiller3.bcr.template.templateSyntaxColors

@Composable
fun FilenameTemplateDialog(
    template: Template,
    onSelect: (Template) -> Unit,
    onDismiss: () -> Unit,
    onReset: () -> Unit,
) {
    val syntaxColors = templateSyntaxColors()
    val initialText = remember { template.toString() }
    val input = rememberTextFieldState(initialText = initialText)
    val template = tryParseInput(input.text.toString(), syntaxColors)

    AlertDialog(
        title = { Text(text = stringResource(R.string.filename_template_dialog_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(state = rememberScrollState())) {
                Text(text = buildMessage(syntaxColors))

                OutlinedTextField(
                    state = input,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    isError = template is TemplateParse.Error,
                    supportingText = {
                        if (template is TemplateParse.Error) {
                            Text(text = template.message)
                        }
                    },
                    outputTransformation = OutputTransformation {
                        // This can't use an AnnotatedString directly.
                        // https://issuetracker.google.com/issues/389978748
                        val annotated = syntaxColors.annotated(originalText.toString())

                        for (style in annotated.spanStyles) {
                            addStyle(style.item, style.start, style.end)
                        }
                        for (style in annotated.paragraphStyles) {
                            addStyle(style.item, style.start, style.end)
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                    ),
                )

                if ('/' in input.text) {
                    Text(
                        text = buildSubdirectoryWarningMessage(syntaxColors),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { onSelect((template as TemplateParse.Value).template) },
                enabled = template is TemplateParse.Value,
            ) {
                Text(text = stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(android.R.string.cancel))
            }

            TextButton(onClick = onReset) {
                Text(text = stringResource(R.string.filename_template_dialog_action_reset_to_default))
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
    )
}

@Composable
private fun buildMessage(syntaxColors: TemplateSyntaxColors): AnnotatedString {
    val resources = LocalResources.current

    val origMessage = resources.getText(R.string.filename_template_dialog_message) as SpannedString
    val message = StringBuilder(origMessage)
    val origAnnotations = origMessage.getSpans(0, origMessage.length, Annotation::class.java)
    val newAnnotations = mutableListOf<AnnotatedString.Range<AnnotatedString.Annotation>>()
    var lengthOffset = 0

    for (annotation in origAnnotations) {
        val start = origMessage.getSpanStart(annotation) + lengthOffset
        val end = origMessage.getSpanEnd(annotation) + lengthOffset

        @Suppress("CascadeIf")
        if (annotation.key == "type" && annotation.value == "supported_vars") {
            val origLength = message.length
            val separator = ", "
            var nextOffset = start

            for ((i, v) in OutputFilenameGenerator.KNOWN_VARS.withIndex()) {
                val text = Template.VariableRef(v, null).toTemplate()

                if (i == 0) {
                    message.replace(start, end, text)
                    nextOffset += text.length
                } else {
                    message.insert(nextOffset, separator)
                    nextOffset += separator.length

                    message.insert(nextOffset, text)
                    nextOffset += text.length
                }

                newAnnotations.addAll(
                    syntaxColors.annotate(
                        message,
                        nextOffset - text.length,
                        nextOffset,
                    )
                )
            }

            lengthOffset += message.length - origLength
        } else if (annotation.key == "type" && annotation.value == "template") {
            newAnnotations.addAll(syntaxColors.annotate(message, start, end))
        } else if (annotation.key == "type" && annotation.value == "template_docs") {
            newAnnotations.add(
                AnnotatedString.Range(
                    LinkAnnotation.Url(BuildConfig.PROJECT_URL_AT_COMMIT + "#filename-template"),
                    start,
                    end,
                )
            )
        } else {
            throw IllegalStateException("Invalid annotation: $annotation")
        }
    }

    return AnnotatedString(message.toString(), newAnnotations)
}

@Composable
private fun buildSubdirectoryWarningMessage(syntaxColors: TemplateSyntaxColors): AnnotatedString {
    val resources = LocalResources.current

    val origMessage = resources.getText(R.string.filename_template_dialog_warning_subdirectories)
            as SpannedString
    val message = StringBuilder(origMessage)
    val origAnnotations = origMessage.getSpans(0, origMessage.length, Annotation::class.java)
    val newAnnotations = mutableListOf<AnnotatedString.Range<AnnotatedString.Annotation>>()

    for (annotation in origAnnotations) {
        val start = origMessage.getSpanStart(annotation)
        val end = origMessage.getSpanEnd(annotation)

        if (annotation.key == "type" && annotation.value == "template") {
            newAnnotations.addAll(syntaxColors.annotate(message, start, end))
        } else {
            throw IllegalStateException("Invalid annotation: $annotation")
        }
    }

    return AnnotatedString(message.toString(), newAnnotations)
}

@Composable
private fun buildErrorMessageWithTemplate(
    @StringRes stringResId: Int,
    template: String,
    syntaxColors: TemplateSyntaxColors,
): AnnotatedString {
    val resources = LocalResources.current

    val origMessage = resources.getText(stringResId) as SpannedString
    val message = StringBuilder(origMessage)
    val origAnnotations = origMessage.getSpans(0, origMessage.length, Annotation::class.java)
    val newAnnotations = mutableListOf<AnnotatedString.Range<AnnotatedString.Annotation>>()
    var lengthOffset = 0

    for (annotation in origAnnotations) {
        val start = origMessage.getSpanStart(annotation) + lengthOffset
        val end = origMessage.getSpanEnd(annotation) + lengthOffset

        if (annotation.key == "type" && annotation.value == "template") {
            val origLength = message.length
            message.replace(start, end, template)

            val newEnd = start + template.length

            newAnnotations.addAll(syntaxColors.annotate(message, start, newEnd))

            lengthOffset = message.length - origLength
        } else {
            throw IllegalStateException("Invalid annotation: $annotation")
        }
    }

    return AnnotatedString(message.toString(), newAnnotations)
}

private sealed interface TemplateParse {
    data class Value(val template: Template) : TemplateParse

    data class Error(val message: AnnotatedString) : TemplateParse
}

@Composable
private fun tryParseInput(input: String, syntaxColors: TemplateSyntaxColors): TemplateParse {
    if (input.isEmpty()) {
        return TemplateParse.Error(
            AnnotatedString(stringResource(R.string.filename_template_dialog_error_empty)),
        )
    }

    val template = try {
        Template(input)
    } catch (_: Exception) {
        return TemplateParse.Error(
            AnnotatedString(stringResource(R.string.filename_template_dialog_error_invalid_syntax)),
        )
    }

    val errors = OutputFilenameGenerator.validate(template)
    if (errors.isEmpty()) {
        return TemplateParse.Value(template)
    }

    // Only show first error due to space constraints.
    val error = errors.first()
    val errorResId = when (error.type) {
        OutputFilenameGenerator.ValidationErrorType.UNKNOWN_VARIABLE ->
            R.string.filename_template_dialog_error_unknown_variable
        OutputFilenameGenerator.ValidationErrorType.HAS_ARGUMENT ->
            R.string.filename_template_dialog_error_has_argument
        OutputFilenameGenerator.ValidationErrorType.INVALID_ARGUMENT ->
            R.string.filename_template_dialog_error_invalid_argument
    }

    return TemplateParse.Error(
        buildErrorMessageWithTemplate(errorResId, error.varRef.toTemplate(), syntaxColors),
    )
}
