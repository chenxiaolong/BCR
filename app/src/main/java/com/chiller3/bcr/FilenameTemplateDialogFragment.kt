package com.chiller3.bcr

import android.app.Dialog
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.Annotation
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannedString
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.TypefaceSpan
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import com.chiller3.bcr.databinding.DialogFilenameTemplateBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class FilenameTemplateDialogFragment : DialogFragment() {
    companion object {
        val TAG: String = FilenameTemplateDialogFragment::class.java.simpleName

        const val RESULT_SUCCESS = "success"
    }

    private lateinit var prefs: Preferences
    private lateinit var highlighter: TemplateSyntaxHighlighter
    private lateinit var binding: DialogFilenameTemplateBinding
    private var template: Template? = null
    private var success: Boolean = false

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        prefs = Preferences(context)
        highlighter = TemplateSyntaxHighlighter(context)
        template = prefs.filenameTemplate ?: Preferences.DEFAULT_FILENAME_TEMPLATE

        binding = DialogFilenameTemplateBinding.inflate(layoutInflater)

        val origMessage = getText(R.string.filename_template_dialog_message) as SpannedString
        val annotations = origMessage.getSpans(0, origMessage.length, Annotation::class.java)
        val message = SpannableString(origMessage)

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

        binding.message.movementMethod = LinkMovementMethod.getInstance()
        binding.message.text = message

        // Make this non-multiline text box look like one
        binding.text.setHorizontallyScrolling(false)
        binding.text.maxLines = Int.MAX_VALUE
        binding.text.addTextChangedListener {
            highlighter.highlight(it!!)

            if (it.isEmpty()) {
                template = null
                binding.textLayout.error = getString(R.string.filename_template_dialog_error_empty)
            } else {
                try {
                    template = Template(it.toString()).apply {
                        evaluate { name, _ ->
                            if (name in OutputFilenameGenerator.KNOWN_VARS) {
                                ""
                            } else {
                                null
                            }
                        }
                    }
                    binding.textLayout.error = null
                    // Don't keep the layout space for the error message reserved
                    binding.textLayout.isErrorEnabled = false
                } catch (e: Template.MissingVariableException) {
                    template = null
                    binding.textLayout.error =
                        getString(R.string.filename_template_dialog_error_unknown_var, e.name)
                } catch (e: Exception) {
                    template = null
                    binding.textLayout.error =
                        getString(R.string.filename_template_dialog_error_invalid)
                }
            }

            refreshOkButtonEnabledState()
        }
        binding.text.setText(template.toString())

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

    private fun refreshOkButtonEnabledState() {
        (dialog as AlertDialog?)?.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled =
            template != null
    }
}