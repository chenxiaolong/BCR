/*
 * SPDX-FileCopyrightText: 2023-2026 Andrew Gunnerson
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.chiller3.bcr.template

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import com.google.android.material.color.MaterialColors

data class TemplateSyntaxColors(
    val variableRefName: Color,
    val variableRefArg: Color,
    val fallbackChars: Color,
    val dirSeparator: Color,
) {
    companion object {
        // Android's regex implementation requires the "redundant" escapes.
        @Suppress("RegExpRedundantEscape")
        private val REGEX_VARIABLE_REF = "\\{[\\p{Alpha}_][\\p{Alpha}\\d_]*(:[^\\}]*)?\\}".toRegex()

        internal val BASE_VARIABLE_REF_NAME = Color(0xff008000)
        internal val BASE_VARIABLE_REF_ARG = Color(0xff0000ff)
        internal val BASE_FALLBACK_CHARS = Color(0xffff00ff)
        internal val BASE_DIR_SEPARATOR = Color(0xffff0000)

        private fun bias(color: Color, towards: Color, isDarkTheme: Boolean): Color {
            val harmonized = MaterialColors.harmonize(color.toArgb(), towards.toArgb())
            return Color(MaterialColors.getColorRoles(harmonized, !isDarkTheme).accent)
        }
    }

    constructor(towards: Color, isDarkTheme: Boolean) : this(
        variableRefName = bias(BASE_VARIABLE_REF_NAME, towards, isDarkTheme),
        variableRefArg = bias(BASE_VARIABLE_REF_ARG, towards, isDarkTheme),
        fallbackChars = bias(BASE_FALLBACK_CHARS, towards, isDarkTheme),
        dirSeparator = bias(BASE_DIR_SEPARATOR, towards, isDarkTheme),
    )

    fun annotate(
        data: CharSequence,
        templateStart: Int = 0,
        templateEnd: Int = data.length,
    ): List<AnnotatedString.Range<AnnotatedString.Annotation>> {
        val template = if (templateStart == 0 && templateEnd == data.length) {
            data
        } else {
            data.subSequence(templateStart, templateEnd)
        }
        val annotations = mutableListOf<AnnotatedString.Range<AnnotatedString.Annotation>>()

        // This is intentionally not based on the AST because Template cannot parse incomplete
        // half-written templates and we don't want syntax highlighting to be removed as the user is
        // typing.
        for ((i, c) in template.withIndex()) {
            if (c == '[' || c == ']' || c == '|') {
                annotations.add(
                    AnnotatedString.Range(
                        SpanStyle(color = fallbackChars),
                        templateStart + i,
                        templateStart + i + 1,
                    )
                )
            }
        }

        for (m in REGEX_VARIABLE_REF.findAll(template)) {
            annotations.add(
                AnnotatedString.Range(
                    SpanStyle(color = variableRefName),
                    templateStart + m.range.first,
                    templateStart + m.range.last + 1,
                )
            )

            val arg = m.groups[1]
            if (arg != null) {
                annotations.add(
                    AnnotatedString.Range(
                        SpanStyle(color = variableRefArg),
                        templateStart + arg.range.first,
                        templateStart + arg.range.last + 1,
                    )
                )
            }
        }

        // Color directory separators last to ensure their visual prominence.
        for ((i, c) in template.withIndex()) {
            if (c == '/') {
                annotations.add(
                    AnnotatedString.Range(
                        SpanStyle(color = dirSeparator),
                        templateStart + i,
                        templateStart + i + 1,
                    )
                )
            }
        }

        return annotations
    }

    fun annotated(input: String) = AnnotatedString(input, annotate(input))
}

@Composable
fun templateSyntaxColors(
    biasTowards: Color = MaterialTheme.colorScheme.primary,
    isDarkTheme: Boolean = isSystemInDarkTheme(),
) = remember(biasTowards, isDarkTheme) {
    TemplateSyntaxColors(biasTowards, isDarkTheme)
}
