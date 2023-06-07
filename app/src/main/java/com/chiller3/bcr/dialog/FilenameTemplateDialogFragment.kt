package com.chiller3.bcr.dialog

import android.app.Dialog
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.Annotation
import android.text.InputType
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.SpannedString
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.TypefaceSpan
import android.view.View
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import com.chiller3.bcr.BuildConfig
import com.chiller3.bcr.Preferences
import com.chiller3.bcr.R
import com.chiller3.bcr.databinding.DialogTextInputBinding
import com.chiller3.bcr.output.OutputFilenameGenerator
import com.chiller3.bcr.template.Template
import com.chiller3.bcr.template.TemplateSyntaxHighlighter
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class FilenameTemplateDialogFragment : DialogFragment() {
    companion object {
        val TAG: String = FilenameTemplateDialogFragment::class.java.simpleName

        const val RESULT_SUCCESS = "success"
    }

    private lateinit var prefs: Preferences
    private lateinit var highlighter: TemplateSyntaxHighlighter
    private lateinit var binding: DialogTextInputBinding
    private var template: Template? = null
    private var success: Boolean = false

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        prefs = Preferences(context)
        highlighter = TemplateSyntaxHighlighter(context)
        template = prefs.filenameTemplate ?: Preferences.DEFAULT_FILENAME_TEMPLATE

        binding = DialogTextInputBinding.inflate(layoutInflater)

        binding.message.movementMethod = LinkMovementMethod.getInstance()
        binding.message.text = buildMessage()

        binding.bottomMessage.text = buildSubdirectoryWarningMessage()

        binding.text.inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        // Make this non-multiline text box look like one
        binding.text.setHorizontallyScrolling(false)
        binding.text.maxLines = Int.MAX_VALUE
        binding.text.addTextChangedListener {
            highlighter.highlight(it!!)

            binding.bottomMessage.isVisible = '/' in it

            if (it.isEmpty()) {
                template = null
                binding.textLayout.error = getString(R.string.filename_template_dialog_error_empty)
            } else {
                try {
                    val newTemplate = Template(it.toString())
                    val errors = OutputFilenameGenerator.validate(newTemplate)
                    if (errors.isEmpty()) {
                        template = newTemplate

                        binding.textLayout.error = null
                        // Don't keep the layout space for the error message reserved
                        binding.textLayout.isErrorEnabled = false
                    } else {
                        template = null

                        // Only show first error due to space constraints
                        val error = errors.first()

                        val errorResId = when (error.type) {
                            OutputFilenameGenerator.ValidationErrorType.UNKNOWN_VARIABLE -> {
                                R.string.filename_template_dialog_error_unknown_variable
                            }
                            OutputFilenameGenerator.ValidationErrorType.HAS_ARGUMENT -> {
                                R.string.filename_template_dialog_error_has_argument
                            }
                            OutputFilenameGenerator.ValidationErrorType.INVALID_ARGUMENT -> {
                                R.string.filename_template_dialog_error_invalid_argument
                            }
                        }
                        binding.textLayout.error = buildErrorMessageWithTemplate(
                            errorResId, error.varRef.toTemplate())
                    }
                } catch (e: Exception) {
                    template = null
                    binding.textLayout.error =
                        getString(R.string.filename_template_dialog_error_invalid_syntax)
                }
            }

            refreshOkButtonEnabledState()
        }
        if (savedInstanceState == null) {
            binding.text.setText(template!!.toString())
        }

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.filename_template_dialog_title)
            .setView(binding.root)
            .setPositiveButton(R.string.dialog_action_ok) { _, _ ->
                prefs.filenameTemplate = template!!
                success = true
            }
            .setNegativeButton(R.string.dialog_action_cancel, null)
            .setNeutralButton(R.string.filename_template_dialog_action_reset_to_default) { _, _ ->
                prefs.filenameTemplate = null
                success = true
            }
            .create()
            .apply {
                setCanceledOnTouchOutside(false)
            }
    }

    override fun onStart() {
        super.onStart()
        refreshOkButtonEnabledState()
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)

        setFragmentResult(tag!!, bundleOf(RESULT_SUCCESS to success))
    }

    private fun buildMessage(): SpannableStringBuilder {
        val origMessage = getText(R.string.filename_template_dialog_message) as SpannedString
        val message = SpannableStringBuilder(origMessage)
        val annotations = message.getSpans(0, origMessage.length, Annotation::class.java)

        for (annotation in annotations) {
            val start = message.getSpanStart(annotation)
            val end = message.getSpanEnd(annotation)

            if (annotation.key == "type" && annotation.value == "supported_vars") {
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

                    highlighter.highlight(message, start, nextOffset)
                }
            } else if (annotation.key == "type" && annotation.value == "template") {
                message.setSpan(
                    TypefaceSpan(Typeface.MONOSPACE),
                    start,
                    end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                )

                highlighter.highlight(message, start, end)
            } else if (annotation.key == "type" && annotation.value == "template_docs") {
                message.setSpan(
                    object : ClickableSpan() {
                        override fun onClick(widget: View) {
                            val uri = Uri.parse(BuildConfig.PROJECT_URL_AT_COMMIT +
                                    "#filename-template")
                            startActivity(Intent(Intent.ACTION_VIEW, uri))
                        }
                    },
                    start,
                    end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            } else {
                throw IllegalStateException("Invalid annotation: $annotation")
            }
        }

        return message
    }

    private fun buildSubdirectoryWarningMessage(): SpannableStringBuilder {
        val origMessage = getText(R.string.filename_template_dialog_warning_subdirectories) as SpannedString
        val message = SpannableStringBuilder(origMessage)
        val annotations = message.getSpans(0, origMessage.length, Annotation::class.java)

        for (annotation in annotations) {
            val start = message.getSpanStart(annotation)
            val end = message.getSpanEnd(annotation)

            if (annotation.key == "type" && annotation.value == "template") {
                message.setSpan(
                    TypefaceSpan(Typeface.MONOSPACE),
                    start,
                    end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                )

                highlighter.highlight(message, start, end)
            } else {
                throw IllegalStateException("Invalid annotation: $annotation")
            }
        }

        return message
    }

    private fun buildErrorMessageWithTemplate(
        @StringRes stringResId: Int,
        template: String,
    ): SpannableStringBuilder {
        val origMessage = getText(stringResId) as SpannedString
        val message = SpannableStringBuilder(origMessage)
        val annotations = message.getSpans(0, origMessage.length, Annotation::class.java)

        for (annotation in annotations) {
            val start = message.getSpanStart(annotation)
            val end = message.getSpanEnd(annotation)

            if (annotation.key == "type" && annotation.value == "template") {
                message.replace(start, end, template)

                val newEnd = start + template.length

                message.setSpan(
                    TypefaceSpan(Typeface.MONOSPACE),
                    start,
                    newEnd,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                )

                highlighter.highlight(message, start, newEnd)
            } else {
                throw IllegalStateException("Invalid annotation: $annotation")
            }
        }

        return message
    }

    private fun refreshOkButtonEnabledState() {
        (dialog as AlertDialog?)?.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled =
            template != null
    }
}