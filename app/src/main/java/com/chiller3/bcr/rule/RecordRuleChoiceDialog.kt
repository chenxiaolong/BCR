/*
 * SPDX-FileCopyrightText: 2026 Andrew Gunnerson
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.chiller3.bcr.rule

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import android.os.Parcelable
import android.telephony.SubscriptionManager
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.chiller3.bcr.R
import com.chiller3.bcr.ui.Preference
import com.chiller3.bcr.ui.PreferenceColumn
import com.chiller3.bcr.ui.betterSegmentedShapes
import kotlinx.parcelize.Parcelize

@Parcelize
sealed interface RecordRuleChoiceAction : Parcelable {
    @Parcelize
    data object CallNumber : RecordRuleChoiceAction

    @Parcelize
    data object CallType : RecordRuleChoiceAction

    @Parcelize
    data object SimSlot : RecordRuleChoiceAction

    @Parcelize
    data class Action(val origAction: RecordRule.Action) : RecordRuleChoiceAction

    @Parcelize
    data object InitialState : RecordRuleChoiceAction
}

@Parcelize
sealed interface RecordRuleChoiceResult : Parcelable {
    @Parcelize
    enum class CallNumber : RecordRuleChoiceResult {
        ANY,
        CONTACT,
        CONTACT_GROUP,
        UNKNOWN,
    }

    @Parcelize
    data class CallType(val callType: RecordRule.CallType) : RecordRuleChoiceResult

    @Parcelize
    data class SimSlot(val simSlot: RecordRule.SimSlot) : RecordRuleChoiceResult

    @Parcelize
    data class Action(val action: RecordRule.Action) : RecordRuleChoiceResult

    @Parcelize
    data class InitialState(val initialState: RecordRule.InitialState) : RecordRuleChoiceResult
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RecordRuleChoiceDialog(
    action: RecordRuleChoiceAction,
    onSelected: (RecordRuleChoiceResult) -> Unit,
    onDismissed: () -> Unit,
) {
    val choices = actionChoices(action)

    AlertDialog(
        title = { Text(text = actionTitle(action)) },
        text = {
            PreferenceColumn(fillScreen = false) {
                itemsIndexed(choices) { index, (result, text) ->
                    Preference(
                        onClick = { onSelected(result) },
                        shapes = betterSegmentedShapes(index = index, count = choices.size),
                        title = { Text(text = text) },
                    )
                }
            }
        },
        onDismissRequest = { onDismissed() },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = { onDismissed() }) {
                Text(text = stringResource(android.R.string.cancel))
            }
        },
    )
}

@Composable
private fun actionTitle(action: RecordRuleChoiceAction) = when (action) {
    RecordRuleChoiceAction.CallNumber -> stringResource(R.string.record_rule_number_title)
    RecordRuleChoiceAction.CallType -> stringResource(R.string.record_rule_type_title)
    RecordRuleChoiceAction.SimSlot -> stringResource(R.string.record_rule_sim_title)
    is RecordRuleChoiceAction.Action -> stringResource(R.string.record_rule_action_title)
    RecordRuleChoiceAction.InitialState -> stringResource(R.string.record_rule_initial_state_title)
}

@Composable
private fun actionChoices(action: RecordRuleChoiceAction) =
    mutableListOf<Pair<RecordRuleChoiceResult, String>>().apply {
        val context = LocalContext.current

        when (val action = action) {
            RecordRuleChoiceAction.CallNumber -> {
                for (callNumber in RecordRuleChoiceResult.CallNumber.entries) {
                    val resId = when (callNumber) {
                        RecordRuleChoiceResult.CallNumber.ANY ->
                            R.string.record_rule_number_any_item
                        RecordRuleChoiceResult.CallNumber.CONTACT ->
                            R.string.record_rule_number_contact_item
                        RecordRuleChoiceResult.CallNumber.CONTACT_GROUP ->
                            R.string.record_rule_number_contact_group_item
                        RecordRuleChoiceResult.CallNumber.UNKNOWN ->
                            R.string.record_rule_number_unknown_item
                    }

                    add(callNumber to stringResource(resId))
                }
            }
            RecordRuleChoiceAction.CallType -> {
                for (callType in RecordRule.CallType.entries) {
                    val resId = when (callType) {
                        RecordRule.CallType.ANY -> R.string.record_rule_type_any_item
                        RecordRule.CallType.INCOMING -> R.string.record_rule_type_incoming_item
                        RecordRule.CallType.OUTGOING -> R.string.record_rule_type_outgoing_item
                        RecordRule.CallType.CONFERENCE -> R.string.record_rule_type_conference_item
                    }

                    add(RecordRuleChoiceResult.CallType(callType) to stringResource(resId))
                }
            }
            RecordRuleChoiceAction.SimSlot -> {
                add(RecordRuleChoiceResult.SimSlot(RecordRule.SimSlot.Any)
                        to stringResource(R.string.record_rule_sim_any_item))

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                        && context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) ==
                                PackageManager.PERMISSION_GRANTED) {
                    val subscriptionManager = context.getSystemService(SubscriptionManager::class.java)
                    // Android Studio is broken and doesn't recognize the permission check.
                    @SuppressLint("MissingPermission")
                    val simCount = subscriptionManager.activeSubscriptionInfoCount

                    for (i in 1..simCount) {
                        add(RecordRuleChoiceResult.SimSlot(RecordRule.SimSlot.Specific(i))
                                to stringResource(R.string.record_rule_sim_specific_item, i))
                    }
                }
            }
            is RecordRuleChoiceAction.Action -> {
                val initialState = when (action.origAction) {
                    is RecordRule.Action.Save -> action.origAction.initialState
                    is RecordRule.Action.Discard -> action.origAction.initialState
                    RecordRule.Action.Ignore -> RecordRule.InitialState.RECORDING
                }

                add(RecordRuleChoiceResult.Action(RecordRule.Action.Save(initialState))
                        to stringResource(R.string.record_rule_action_save_item))
                add(RecordRuleChoiceResult.Action(RecordRule.Action.Discard(initialState))
                        to stringResource(R.string.record_rule_action_discard_item))
                add(RecordRuleChoiceResult.Action(RecordRule.Action.Ignore)
                        to stringResource(R.string.record_rule_action_ignore_item))
            }
            RecordRuleChoiceAction.InitialState -> {
                for (initialState in RecordRule.InitialState.entries) {
                    val resId = when (initialState) {
                        RecordRule.InitialState.RECORDING ->
                            R.string.record_rule_initial_state_recording_item
                        RecordRule.InitialState.PAUSED ->
                            R.string.record_rule_initial_state_paused_item
                    }

                    add(RecordRuleChoiceResult.InitialState(initialState) to stringResource(resId))
                }
            }
        }
    }
