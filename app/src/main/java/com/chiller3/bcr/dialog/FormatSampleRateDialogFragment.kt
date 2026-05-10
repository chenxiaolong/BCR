/*
 * SPDX-FileCopyrightText: 2024-2026 Andrew Gunnerson
 * SPDX-License-Identifier: GPL-3.0-only
 */

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
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import com.chiller3.bcr.R
import com.chiller3.bcr.databinding.DialogTextInputBinding
import com.chiller3.bcr.format.Format
import com.chiller3.bcr.format.RangedSampleRateInfo
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class FormatSampleRateDialogFragment : DialogFragment() {
    companion object {
        val TAG: String = FormatSampleRateDialogFragment::class.java.simpleName

        private const val ARG_FORMAT_INDEX = "format_index"
        private const val RESULT_FORMAT_INDEX = ARG_FORMAT_INDEX
        private const val RESULT_VALUE = "value"

        fun newInstance(format: Format) =
            FormatSampleRateDialogFragment().apply {
                arguments = Bundle().apply {
                    putInt(ARG_FORMAT_INDEX, Format.all.indexOf(format))
                }
            }

        fun getResult(bundle: Bundle): Pair<Format, UInt>? {
            if (!bundle.containsKey(RESULT_VALUE)) {
                return null
            }

            val formatIndex = bundle.getInt(RESULT_FORMAT_INDEX)
            val value = bundle.getInt(RESULT_VALUE).toUInt()

            return Format.all[formatIndex] to value
        }
    }

    private lateinit var binding: DialogTextInputBinding
    private var value: UInt? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        val format = Format.all[requireArguments().getInt(ARG_FORMAT_INDEX)]

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
            binding.textLayout.prefixText = translated.take(placeholder).trimEnd()
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
                } catch (_: NumberFormatException) {
                    // Ignore
                } catch (_: IllegalArgumentException) {
                    // Ignore
                }
            }

            refreshOkButtonEnabledState()
        }

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.format_sample_rate_dialog_title)
            .setView(binding.root)
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                value = null
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

        setFragmentResult(tag!!, Bundle().apply {
            putInt(RESULT_FORMAT_INDEX, requireArguments().getInt(ARG_FORMAT_INDEX))
            value?.let {
                putInt(RESULT_VALUE, it.toInt())
            }
        })
    }

    private fun refreshOkButtonEnabledState() {
        (dialog as AlertDialog?)?.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = value != null
    }
}
