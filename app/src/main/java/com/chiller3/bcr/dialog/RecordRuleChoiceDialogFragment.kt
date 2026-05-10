/*
 * SPDX-FileCopyrightText: 2026 Andrew Gunnerson
 * SPDX-License-Identifier: GPL-3.0-only
 */

@file:OptIn(ExperimentalUnsignedTypes::class)

package com.chiller3.bcr.dialog

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.DialogInterface
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.telephony.SubscriptionManager
import androidx.core.os.BundleCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import com.chiller3.bcr.R
import com.chiller3.bcr.rule.RecordRule
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.parcelize.Parcelize

open class RecordRuleChoiceDialogFragment : DialogFragment() {
    companion object {
        val TAG: String = RecordRuleChoiceDialogFragment::class.java.simpleName

        private const val ARG_ACTION = "action"
        const val RESULT_RESULT = "result"

        fun newInstance(action: Action) =
            RecordRuleChoiceDialogFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_ACTION, action)
                }
            }
    }

    @Parcelize
    sealed interface Action : Parcelable {
        @Parcelize
        data object CallNumber : RecordRuleChoiceDialogFragment.Action

        @Parcelize
        data object CallType : RecordRuleChoiceDialogFragment.Action

        @Parcelize
        data object SimSlot : RecordRuleChoiceDialogFragment.Action

        @Parcelize
        data class Action(val origAction: RecordRule.Action) : RecordRuleChoiceDialogFragment.Action

        @Parcelize
        data object InitialState : RecordRuleChoiceDialogFragment.Action
    }

    @Parcelize
    sealed interface Result : Parcelable {
        @Parcelize
        enum class CallNumber : Result {
            ANY,
            CONTACT,
            CONTACT_GROUP,
            UNKNOWN,
        }

        @Parcelize
        data class CallType(val callType: RecordRule.CallType) : Result

        @Parcelize
        data class SimSlot(val simSlot: RecordRule.SimSlot) : Result

        @Parcelize
        data class Action(val action: RecordRule.Action) : Result

        @Parcelize
        data class InitialState(val initialState: RecordRule.InitialState) : Result
    }

    private val action by lazy {
        BundleCompat.getParcelable(requireArguments(), ARG_ACTION, Action::class.java)!!
    }
    private val items by lazy {
        mutableListOf<Pair<Result, String>>().apply {
            val context = requireContext()

            when (val action = action) {
                Action.CallNumber -> {
                    for (callNumber in Result.CallNumber.entries) {
                        val resId = when (callNumber) {
                            Result.CallNumber.ANY -> R.string.record_rule_number_any_item
                            Result.CallNumber.CONTACT -> R.string.record_rule_number_contact_item
                            Result.CallNumber.CONTACT_GROUP -> R.string.record_rule_number_contact_group_item
                            Result.CallNumber.UNKNOWN -> R.string.record_rule_number_unknown_item
                        }

                        add(callNumber to getString(resId))
                    }
                }
                Action.CallType -> {
                    for (callType in RecordRule.CallType.entries) {
                        val resId = when (callType) {
                            RecordRule.CallType.ANY -> R.string.record_rule_type_any_item
                            RecordRule.CallType.INCOMING -> R.string.record_rule_type_incoming_item
                            RecordRule.CallType.OUTGOING -> R.string.record_rule_type_outgoing_item
                            RecordRule.CallType.CONFERENCE -> R.string.record_rule_type_conference_item
                        }

                        add(Result.CallType(callType) to getString(resId))
                    }
                }
                Action.SimSlot -> {
                    add(Result.SimSlot(RecordRule.SimSlot.Any)
                            to getString(R.string.record_rule_sim_any_item))

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                            && context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) ==
                            PackageManager.PERMISSION_GRANTED) {
                        val subscriptionManager = context.getSystemService(SubscriptionManager::class.java)
                        // Android Studio is broken and doesn't recognize the permission check.
                        @SuppressLint("MissingPermission")
                        var simCount = subscriptionManager.activeSubscriptionInfoCount

                        for (i in 1..simCount) {
                            add(Result.SimSlot(RecordRule.SimSlot.Specific(i))
                                    to getString(R.string.record_rule_sim_specific_item, i))
                        }
                    }
                }
                is Action.Action -> {
                    val initialState = when (action.origAction) {
                        is RecordRule.Action.Save -> action.origAction.initialState
                        is RecordRule.Action.Discard -> action.origAction.initialState
                        RecordRule.Action.Ignore -> RecordRule.InitialState.RECORDING
                    }

                    add(Result.Action(RecordRule.Action.Save(initialState))
                            to getString(R.string.record_rule_action_save_item))
                    add(Result.Action(RecordRule.Action.Discard(initialState))
                            to getString(R.string.record_rule_action_discard_item))
                    add(Result.Action(RecordRule.Action.Ignore)
                            to getString(R.string.record_rule_action_ignore_item))
                }
                Action.InitialState -> {
                    for (initialState in RecordRule.InitialState.entries) {
                        val resId = when (initialState) {
                            RecordRule.InitialState.RECORDING ->
                                R.string.record_rule_initial_state_recording_item
                            RecordRule.InitialState.PAUSED ->
                                R.string.record_rule_initial_state_paused_item
                        }

                        add(Result.InitialState(initialState) to getString(resId))
                    }
                }
            }
        }
    }

    private var result: Result? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val title = when (action) {
            Action.CallNumber -> R.string.record_rule_number_title
            Action.CallType -> R.string.record_rule_type_title
            Action.SimSlot -> R.string.record_rule_sim_title
            is Action.Action -> R.string.record_rule_action_title
            Action.InitialState -> R.string.record_rule_initial_state_title
        }

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setItems(items.map { it.second }.toTypedArray()) { _, i ->
                result = items[i].first
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)

        setFragmentResult(tag!!, Bundle().apply { putParcelable(RESULT_RESULT, result) })
    }
}
