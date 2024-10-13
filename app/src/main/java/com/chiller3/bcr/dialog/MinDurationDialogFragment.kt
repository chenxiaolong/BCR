/*
 * SPDX-FileCopyrightText: 2024 Andrew Gunnerson
 * SPDX-License-Identifier: GPL-3.0-only
 */

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
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class MinDurationDialogFragment : DialogFragment() {
    companion object {
        val TAG: String = MinDurationDialogFragment::class.java.simpleName

        const val RESULT_SUCCESS = "success"
    }

    private lateinit var prefs: Preferences
    private lateinit var binding: DialogTextInputBinding
    private var minDuration: Int? = null
    private var success: Boolean = false

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        prefs = Preferences(context)
        minDuration = prefs.minDuration

        binding = DialogTextInputBinding.inflate(layoutInflater)

        binding.message.setText(R.string.min_duration_dialog_message)

        binding.text.inputType = InputType.TYPE_CLASS_NUMBER
        binding.text.addTextChangedListener {
            minDuration = if (it!!.isEmpty()) {
                0
            } else {
                try {
                    val seconds = it.toString().toInt()
                    if (seconds >= 0) {
                        seconds
                    } else {
                        null
                    }
                } catch (e: NumberFormatException) {
                    null
                }
            }

            refreshHelperText()
            refreshOkButtonEnabledState()
        }
        if (savedInstanceState == null) {
            binding.text.setText(minDuration?.toString())
        }

        refreshHelperText()

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.min_duration_dialog_title)
            .setView(binding.root)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                prefs.minDuration = minDuration!!
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
        val context = requireContext()

        binding.textLayout.helperText = minDuration?.let {
            context.resources.getQuantityString(R.plurals.min_duration_dialog_seconds, it, it)
        }
    }

    private fun refreshOkButtonEnabledState() {
        (dialog as AlertDialog?)?.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled =
            minDuration != null
    }
}