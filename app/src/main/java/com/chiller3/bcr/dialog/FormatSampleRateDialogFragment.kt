package com.chiller3.bcr.dialog

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.text.InputType
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.BulletSpan
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import com.chiller3.bcr.Preferences
import com.chiller3.bcr.R
import com.chiller3.bcr.databinding.DialogTextInputBinding
import com.chiller3.bcr.format.Format
import com.chiller3.bcr.format.RangedSampleRateInfo
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class FormatSampleRateDialogFragment : DialogFragment() {
    companion object {
        val TAG: String = FormatSampleRateDialogFragment::class.java.simpleName

        const val RESULT_SUCCESS = "success"
    }

    private lateinit var prefs: Preferences
    private lateinit var format: Format
    private lateinit var binding: DialogTextInputBinding
    private var value: UInt? = null
    private var success: Boolean = false

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        prefs = Preferences(context)
        format = Format.fromPreferences(prefs).first

        val sampleRateInfo = format.sampleRateInfo
        if (sampleRateInfo !is RangedSampleRateInfo) {
            throw IllegalStateException("Selected format is not configurable")
        }

        binding = DialogTextInputBinding.inflate(layoutInflater)

        binding.message.text = SpannableStringBuilder().apply {
            append(getString(R.string.format_sample_rate_dialog_message_desc))

            // BulletSpan operates on unscaled pixels for some reason.
            val density = resources.displayMetrics.density
            val gapPx = (density * 4).toInt()
            val radiusPx = (density * 2).toInt()

            for (range in sampleRateInfo.ranges) {
                append('\n')

                val start = length

                append(getString(
                    R.string.format_sample_rate_dialog_message_range,
                    sampleRateInfo.format(context, range.first),
                    sampleRateInfo.format(context, range.last),
                ))

                val end = length

                setSpan(
                    BulletSpan(gapPx, binding.message.currentTextColor, radiusPx),
                    start,
                    end,
                    Spannable.SPAN_INCLUSIVE_EXCLUSIVE,
                )
            }
        }

        // Try to detect if the displayed format is a prefix or suffix since it may not be the same
        // in every language.
        val translated = getString(R.string.format_sample_rate, "\u0000")
        val placeholder = translated.indexOf('\u0000')
        val hasPrefix = placeholder > 0
        val hasSuffix = placeholder < translated.length - 1
        if (hasPrefix) {
            binding.textLayout.prefixText = translated.substring(0, placeholder).trimEnd()
        }
        if (hasSuffix) {
            binding.textLayout.suffixText = translated.substring(placeholder + 1).trimStart()
        }
        if (hasPrefix && hasSuffix) {
            binding.text.textAlignment = View.TEXT_ALIGNMENT_CENTER
        } else if (hasSuffix) {
            binding.text.textAlignment = View.TEXT_ALIGNMENT_TEXT_END
        }

        binding.text.inputType = InputType.TYPE_CLASS_NUMBER
        binding.text.addTextChangedListener {
            value = null

            if (it!!.isNotEmpty()) {
                try {
                    value = it.toString().toUInt().apply {
                        sampleRateInfo.validate(this)
                    }
                } catch (e: NumberFormatException) {
                    // Ignore
                } catch (e: IllegalArgumentException) {
                    // Ignore
                }
            }

            refreshOkButtonEnabledState()
        }

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.format_sample_rate_dialog_title)
            .setView(binding.root)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                prefs.setFormatSampleRate(format, value!!)
                success = true
            }
            .setNegativeButton(android.R.string.cancel, null)
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
        (dialog as AlertDialog?)?.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = value != null
    }
}
