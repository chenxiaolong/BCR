package com.chiller3.bcr

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
import com.chiller3.bcr.databinding.DialogTextInputBinding
import com.chiller3.bcr.format.Format
import com.chiller3.bcr.format.RangedParamInfo
import com.chiller3.bcr.format.RangedParamType
import com.google.android.material.dialog.MaterialAlertDialogBuilder

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
            paramInfo.format(paramInfo.range.first),
            paramInfo.format(paramInfo.range.last),
        )

        if (paramInfo.type == RangedParamType.Bitrate) {
            binding.textLayout.suffixText = "kbps"
            binding.text.textAlignment = View.TEXT_ALIGNMENT_TEXT_END
        }

        binding.text.inputType = InputType.TYPE_CLASS_NUMBER
        binding.text.addTextChangedListener {
            value = if (it!!.isNotEmpty()) {
                val newValue = it.toString().toUInt().times(multiplier.toULong())
                if (newValue in paramInfo.range) {
                    newValue.toUInt()
                } else {
                    null
                }
            } else {
                null
            }

            refreshOkButtonEnabledState()
        }

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.format_param_dialog_title)
            .setView(binding.root)
            .setPositiveButton(R.string.dialog_action_ok) { _, _ ->
                prefs.setFormatParam(format, value!!)
                success = true
            }
            .setNegativeButton(R.string.dialog_action_cancel, null)
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