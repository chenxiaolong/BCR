package com.chiller3.bcr.template

import android.content.Context
import android.content.res.Configuration
import android.text.Spannable
import android.text.style.ForegroundColorSpan
import androidx.annotation.ColorRes
import androidx.core.text.getSpans
import com.chiller3.bcr.R
import com.google.android.material.color.ColorRoles
import com.google.android.material.color.MaterialColors

class TemplateSyntaxHighlighter(context: Context) {
    private val colorVariableRefName = getHarmonizedColor(
        context, R.color.template_highlighting_variable_ref_name)
    private val colorVariableRefArg = getHarmonizedColor(
        context, R.color.template_highlighting_variable_ref_arg)
    private val colorFallbackChars = getHarmonizedColor(
        context, R.color.template_highlighting_fallback_chars)
    private val colorDirectorySeparator = getHarmonizedColor(
        context, R.color.template_highlighting_directory_separator)

    fun highlight(
        spannable: Spannable,
        templateStart: Int = 0,
        templateEnd: Int = spannable.length,
    ) {
        val template = if (templateStart == 0 && templateEnd == spannable.length) {
            spannable
        } else {
            spannable.subSequence(templateStart, templateEnd)
        }

        // Forcibly recolor every time. We don't rely on Android's span extensions, which aren't
        // sophisticated enough to keep the syntax highlighting correct.
        for (span in spannable.getSpans<ForegroundColorSpan>()) {
            val start = spannable.getSpanStart(span)
            val end = spannable.getSpanEnd(span)
            if (start >= templateStart && end <= templateEnd) {
                spannable.removeSpan(span)
            }
        }

        // This is intentionally not based on the AST because Template cannot parse incomplete
        // half-written templates and we don't want syntax highlighting to be removed as the user is
        // typing.
        for ((i, c) in template.withIndex()) {
            if (c == '[' || c == ']' || c == '|') {
                spannable.setSpan(
                    ForegroundColorSpan(colorFallbackChars.accent),
                    templateStart + i,
                    templateStart + i + 1,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
        }

        for (m in REGEX_VARIABLE_REF.findAll(template)) {
            spannable.setSpan(
                ForegroundColorSpan(colorVariableRefName.accent),
                templateStart + m.range.first,
                templateStart + m.range.last + 1,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
            )

            val arg = m.groups[1]
            if (arg != null) {
                spannable.setSpan(
                    ForegroundColorSpan(colorVariableRefArg.accent),
                    templateStart + arg.range.first,
                    templateStart + arg.range.last + 1,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
        }

        // Color directory separators last to ensure their visual prominence.
        for ((i, c) in template.withIndex()) {
            if (c == '/') {
                spannable.setSpan(
                    ForegroundColorSpan(colorDirectorySeparator.accent),
                    templateStart + i,
                    templateStart + i + 1,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
        }
    }

    companion object {
        // Android's regex implementation requires the "redundant" escapes
        @Suppress("RegExpRedundantEscape")
        private val REGEX_VARIABLE_REF = "\\{[\\p{Alpha}_][\\p{Alpha}\\d_]*(:[^\\}]*)?\\}".toRegex()

        private fun getHarmonizedColor(context: Context, @ColorRes colorResId: Int): ColorRoles {
            val isLight = context.resources.configuration.uiMode and
                    Configuration.UI_MODE_NIGHT_MASK != Configuration.UI_MODE_NIGHT_YES
            val color = context.getColor(colorResId)

            val blended = MaterialColors.harmonizeWithPrimary(context, color)
            return MaterialColors.getColorRoles(blended, isLight)
        }
    }
}