package com.chiller3.bcr.dialog

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.text.InputType
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
import com.chiller3.bcr.format.RangedParamInfo
import com.chiller3.bcr.format.RangedParamType
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.lang.NumberFormatException

class FormatParamDialogFragment : DialogFragment() {
    companion object {
        val TAG: String = FormatParamDialogFragment::class.java.simpleName

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

        val paramInfo = format.paramInfo
        if (paramInfo !is RangedParamInfo) {
            throw IllegalStateException("Selected format is not configurable")
        }

        val multiplier = when (paramInfo.type) {
            RangedParamType.CompressionLevel -> 1U
            RangedParamType.Bitrate -> 1000U
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
            RangedParamType.Bitrate ->
                getString(R.string.format_param_bitrate, "\u0000")
        }
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
                    val newValue = it.toString().toUInt().times(multiplier.toULong())
                    if (newValue in paramInfo.range) {
                        value = newValue.toUInt()
                    }
                } catch (e: NumberFormatException) {
                    // Ignore
                }
            }

            refreshOkButtonEnabledState()
        }

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.format_param_dialog_title)
            .setView(binding.root)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                prefs.setFormatParam(format, value!!)
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