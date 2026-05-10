/*
 * SPDX-FileCopyrightText: 2023-2026 Andrew Gunnerson
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.chiller3.bcr.dialog

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.text.InputType
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import com.chiller3.bcr.R
import com.chiller3.bcr.databinding.DialogTextInputBinding
import com.chiller3.bcr.format.Format
import com.chiller3.bcr.format.RangedParamInfo
import com.chiller3.bcr.format.RangedParamType
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class FormatParamDialogFragment : DialogFragment() {
    companion object {
        val TAG: String = FormatParamDialogFragment::class.java.simpleName

        private const val ARG_FORMAT_INDEX = "format_index"
        private const val RESULT_FORMAT_INDEX = ARG_FORMAT_INDEX
        private const val RESULT_VALUE = "value"

        fun newInstance(format: Format) =
            FormatParamDialogFragment().apply {
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

        val paramInfo = format.paramInfo
        if (paramInfo !is RangedParamInfo) {
            throw IllegalStateException("Selected format is not configurable")
        }

        val multiplier = when (paramInfo.type) {
            RangedParamType.CompressionLevel -> 1U
            RangedParamType.Bitrate -> {
                if (paramInfo.range.first % 1_000U == 0U && paramInfo.range.last % 1_000U == 0U) {
                    1000U
                } else {
                    1U
                }
            }
        }

        binding = DialogTextInputBinding.inflate(layoutInflater)

        binding.message.text = getString(
            R.string.format_param_dialog_message,
            paramInfo.format(context, paramInfo.range.first),
            paramInfo.format(context, paramInfo.range.last),
        )

        // Try to detect if the displayed format is a prefix or suffix since it's not the same in
        // every language (eg. "Level 8" vs "8级")
        val translated = when (paramInfo.type) {
            RangedParamType.CompressionLevel ->
                getString(R.string.format_param_compression_level, "\u0000")
            RangedParamType.Bitrate -> if (multiplier == 1_000U) {
                getString(R.string.format_param_bitrate_kbps, "\u0000")
            } else {
                getString(R.string.format_param_bitrate_bps, "\u0000")
            }
        }
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
                    val newValue = it.toString().toUInt().times(multiplier.toULong())
                    if (newValue in paramInfo.range) {
                        value = newValue.toUInt()
                    }
                } catch (_: NumberFormatException) {
                    // Ignore
                }
            }

            refreshOkButtonEnabledState()
        }

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.format_param_dialog_title)
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