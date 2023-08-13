package com.chiller3.bcr.dialog

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.text.InputType
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import com.chiller3.bcr.Preferences
import com.chiller3.bcr.R
import com.chiller3.bcr.databinding.DialogTextInputBinding
import com.chiller3.bcr.output.DaysRetention
import com.chiller3.bcr.output.NoRetention
import com.chiller3.bcr.output.Retention
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class FileRetentionDialogFragment : DialogFragment() {
    companion object {
        val TAG: String = FileRetentionDialogFragment::class.java.simpleName

        const val RESULT_SUCCESS = "success"
    }

    private lateinit var prefs: Preferences
    private lateinit var binding: DialogTextInputBinding
    private var retention: Retention? = null
    private var success: Boolean = false

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        prefs = Preferences(context)
        retention = Retention.fromPreferences(prefs)

        binding = DialogTextInputBinding.inflate(layoutInflater)

        binding.message.setText(R.string.file_retention_dialog_message)

        binding.text.inputType = InputType.TYPE_CLASS_NUMBER
        binding.text.addTextChangedListener {
            retention = if (it!!.isEmpty()) {
                NoRetention
            } else {
                try {
                    val days = it.toString().toUInt()
                    if (days == 0U) {
                        NoRetention
                    } else {
                        DaysRetention(days)
                    }
                } catch (e: NumberFormatException) {
                    binding.textLayout.error = getString(R.string.file_retention_error_too_large)
                    null
                }
            }

            refreshHelperText()
            refreshOkButtonEnabledState()
        }
        if (savedInstanceState == null) {
            when (val r = retention!!) {
                is DaysRetention -> binding.text.setText(r.days.toString())
                NoRetention -> binding.text.setText("")
            }
        }

        refreshHelperText()

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.file_retention_dialog_title)
            .setView(binding.root)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                prefs.outputRetention = retention!!
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

    private fun refreshHelperText() {
        binding.textLayout.helperText = retention?.toFormattedString(requireContext())
    }

    private fun refreshOkButtonEnabledState() {
        (dialog as AlertDialog?)?.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled =
            retention != null
    }
}