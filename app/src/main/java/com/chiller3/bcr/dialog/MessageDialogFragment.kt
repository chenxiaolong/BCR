package com.chiller3.bcr.dialog

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import com.chiller3.bcr.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class MessageDialogFragment : DialogFragment() {
    companion object {
        val TAG: String = MessageDialogFragment::class.java.simpleName

        private const val ARG_TITLE = "title"
        private const val ARG_MESSAGE = "message"

        const val RESULT_SUCCESS = "success"

        fun newInstance(title: CharSequence?, message: CharSequence?) =
            MessageDialogFragment().apply {
                arguments = bundleOf(
                    ARG_TITLE to title,
                    ARG_MESSAGE to message,
                )
            }
    }

    private var success: Boolean = false

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val arguments = requireArguments()

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(arguments.getCharSequence(ARG_TITLE))
            .setMessage(arguments.getCharSequence(ARG_MESSAGE))
            .setPositiveButton(android.R.string.ok) { _, _ ->
                success = true
            }
            .create()
            .apply {
                setCanceledOnTouchOutside(false)
            }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)

        setFragmentResult(tag!!, bundleOf(RESULT_SUCCESS to success))
    }
}