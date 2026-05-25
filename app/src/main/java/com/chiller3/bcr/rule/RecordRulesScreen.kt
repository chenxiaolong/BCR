/*
 * SPDX-FileCopyrightText: 2023-2026 Andrew Gunnerson
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.chiller3.bcr.rule

import android.content.res.Configuration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chiller3.bcr.ContactGroupInfo
import com.chiller3.bcr.ContactInfo
import com.chiller3.bcr.R
import com.chiller3.bcr.ui.AppScreen
import com.chiller3.bcr.ui.BetterSegmentedShapes
import com.chiller3.bcr.ui.Preference
import com.chiller3.bcr.ui.PreferenceCategory
import com.chiller3.bcr.ui.PreferenceColumn
import com.chiller3.bcr.ui.PreferenceDefaults
import com.chiller3.bcr.ui.betterSegmentedShapes
import com.chiller3.bcr.ui.theme.AppTheme
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RecordRulesScreen(
    onBack: () -> Unit,
    viewModel: RecordRulesViewModel = viewModel(),
) {
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()
    val groups by viewModel.groups.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val result = it.data!!
        val position = result.getIntExtra(RecordRuleEditorActivity.RESULT_POSITION, -1)
        val recordRule = IntentCompat.getParcelableExtra(
            result,
            RecordRuleEditorActivity.RESULT_RECORD_RULE,
            RecordRule::class.java,
        )!!

        if (position >= 0) {
            viewModel.replaceRule(position, recordRule)
        } else {
            viewModel.addRule(recordRule)
        }
    }

    AppScreen(
        title = { Text(text = stringResource(R.string.pref_record_rules_name)) },
        onBack = onBack,
        onReset = { viewModel.reset() },
    ) { params ->
        RecordRulesContent(
            rules = rules,
            contacts = contacts,
            groups = groups,
            onRuleAdd = {
                val newRule = RecordRule(
                    callNumber = RecordRule.CallNumber.Any,
                    callType = RecordRule.CallType.ANY,
                    simSlot = RecordRule.SimSlot.Any,
                    action = RecordRule.Action.Save(initialState = RecordRule.InitialState.RECORDING),
                )

                val intent = RecordRuleEditorActivity.createIntent(context, -1, newRule, false)
                launcher.launch(intent)
            },
            onRuleEdit = { pos, rule, isDefault ->
                val intent = RecordRuleEditorActivity.createIntent(
                    context, pos, rule.rule, isDefault,
                )

                launcher.launch(intent)
            },
            onRuleMove = { oldPos, newPos ->
                viewModel.moveRule(oldPos, newPos)
            },
            onRuleRemove = { pos ->
                viewModel.removeRule(pos)
            },
            contentPadding = params.contentPadding,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RecordRulesContent(
    rules: List<DisplayableRule>,
    contacts: Map<String, ContactInfo?>,
    groups: Map<Long, ContactGroupInfo?>,
    onRuleAdd: () -> Unit,
    onRuleEdit: (Int, DisplayableRule, Boolean) -> Unit,
    onRuleMove: (Int, Int) -> Unit,
    onRuleRemove: (Int) -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val hapticFeedback = LocalHapticFeedback.current
    val itemsBeforeDynamicItems = 3 // info, header, add_new_rule
    val lazyListState = rememberLazyListState()
    val reorderableLazyListState = rememberReorderableLazyListState(lazyListState) { from, to ->
        // These are indices within the LazyColumn.
        val fromIndex = from.index - itemsBeforeDynamicItems
        val toIndex = to.index - itemsBeforeDynamicItems
        onRuleMove(fromIndex, toIndex)

        hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
    }

    PreferenceColumn(
        state = lazyListState,
        contentPadding = contentPadding,
    ) {
        item(key = "info") {
            Preference(
                onClick = {},
                shapes = BetterSegmentedShapes.single(),
                title = {},
                summary = { Text(text = stringResource(R.string.record_rules_info)) },
                colors = PreferenceDefaults.preferenceInfoColors(),
                modifier = Modifier.animateItem(),
            )
        }

        item(key = "rules") {
            PreferenceCategory(
                title = { Text(text = stringResource(R.string.pref_header_rules)) },
                modifier = Modifier.animateItem(),
            )
        }

        item(key = "add_new_rule") {
            Preference(
                onClick = onRuleAdd,
                shapes = BetterSegmentedShapes.top(),
                title = { Text(text = stringResource(R.string.pref_add_new_rule_name)) },
                summary = { Text(text = stringResource(R.string.pref_add_new_rule_desc)) },
                modifier = Modifier.animateItem(),
            )
        }

        itemsIndexed(items = rules, key = { _, rule -> rule.id }) { index, rule ->
            val isDefault = index == rules.size - 1

            val title = ruleTitle(index, rules.size)
            val summary = ruleSummary(rule.rule, contacts, groups)

            ReorderableItem(
                state = reorderableLazyListState,
                key = rule.id,
                enabled = !isDefault,
            ) { isDragging ->
                val elevation by animateDpAsState(if (isDragging) 4.dp else 0.dp)

                val a11yRemove = stringResource(R.string.record_rules_list_action_remove)
                val a11yMoveUp = stringResource(R.string.record_rules_list_action_move_up)
                val a11yMoveDown = stringResource(R.string.record_rules_list_action_move_down)

                Surface(
                    modifier = Modifier
                        .longPressDraggableHandle(
                            enabled = !isDefault,
                            onDragStarted = {
                                hapticFeedback.performHapticFeedback(
                                    HapticFeedbackType.GestureThresholdActivate,
                                )
                            },
                            onDragStopped = {
                                hapticFeedback.performHapticFeedback(
                                    HapticFeedbackType.GestureEnd,
                                )
                            },
                        )
                        .semantics {
                            customActions = mutableListOf<CustomAccessibilityAction>().apply {
                                if (!isDefault) {
                                    add(CustomAccessibilityAction(
                                        label = a11yRemove,
                                        action = { onRuleRemove(index); true }
                                    ))

                                    if (index > 0) {
                                        add(CustomAccessibilityAction(
                                            label = a11yMoveUp,
                                            action = { onRuleMove(index, index - 1); true }
                                        ))
                                    }

                                    if (index < rules.size - 2) {
                                        add(CustomAccessibilityAction(
                                            label = a11yMoveDown,
                                            action = { onRuleMove(index, index + 1); true }
                                        ))
                                    }
                                }
                            }
                        },
                    color = Color.Transparent,
                    shadowElevation = elevation,
                ) {
                    SwipeToDismissBox(
                        state = rememberSwipeToDismissBoxState(),
                        backgroundContent = {},
                        enableDismissFromStartToEnd = !isDefault,
                        enableDismissFromEndToStart = !isDefault,
                        onDismiss = {
                            onRuleRemove(index)
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureEnd)
                        },
                    ) {
                        Preference(
                            onClick = { onRuleEdit(index, rule, isDefault) },
                            shapes = betterSegmentedShapes(
                                index = index + 1,
                                count = rules.size + 1,
                            ),
                            title = { Text(text = title) },
                            summary = { Text(text = summary) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ruleTitle(
    ruleIndex: Int,
    ruleCount: Int,
): String = if (ruleIndex == ruleCount - 1) {
    stringResource(R.string.record_rules_list_default_name)
} else {
    stringResource(R.string.record_rules_list_custom_name, ruleIndex + 1)
}

@Composable
private fun ruleSummary(
    rule: RecordRule,
    contacts: Map<String, ContactInfo?>,
    groups: Map<Long, ContactGroupInfo?>,
) = buildString {
    when (rule.callNumber) {
        RecordRule.CallNumber.Any -> {}
        is RecordRule.CallNumber.Contact -> {
            val contact = contacts[rule.callNumber.lookupKey]

            append(stringResource(
                R.string.record_rule_number_contact_summary,
                contact?.displayName ?: rule.callNumber.lookupKey,
            ))
            append('\n')
        }
        is RecordRule.CallNumber.ContactGroup -> {
            val group = groups[rule.callNumber.rowId]

            val msg = if (group?.title != null) {
                stringResource(
                    R.string.record_rule_number_contact_group_with_account_summary,
                    group.title,
                    group.accountName
                        ?: stringResource(R.string.pick_contact_group_local_group),
                )
            } else {
                stringResource(
                    R.string.record_rule_number_contact_group_unknown_summary,
                    rule.callNumber.sourceId ?: rule.callNumber.rowId.toString(),
                )
            }
            append(msg)
            append('\n')
        }
        RecordRule.CallNumber.Unknown -> {
            append(stringResource(R.string.record_rule_number_unknown_summary))
            append('\n')
        }
    }

    val typeMsgId = when (rule.callType) {
        RecordRule.CallType.ANY -> 0
        RecordRule.CallType.INCOMING -> R.string.record_rule_type_incoming_summary
        RecordRule.CallType.OUTGOING -> R.string.record_rule_type_outgoing_summary
        RecordRule.CallType.CONFERENCE -> R.string.record_rule_type_conference_summary
    }
    if (typeMsgId != 0) {
        append(stringResource(typeMsgId))
        append('\n')
    }

    val simSlotMsg = when (rule.simSlot) {
        RecordRule.SimSlot.Any -> null
        is RecordRule.SimSlot.Specific ->
            stringResource(R.string.record_rule_sim_specific_summary, rule.simSlot.slot)
    }
    simSlotMsg?.let {
        append(it)
        append('\n')
    }

    val actionMsgId = when (rule.action) {
        is RecordRule.Action.Save -> R.string.record_rule_action_save_summary
        is RecordRule.Action.Discard -> R.string.record_rule_action_discard_summary
        is RecordRule.Action.Ignore -> R.string.record_rule_action_ignore_summary
    }
    append(stringResource(actionMsgId))

    val initialState = when (val action = rule.action) {
        is RecordRule.Action.Save -> action.initialState
        is RecordRule.Action.Discard -> action.initialState
        RecordRule.Action.Ignore -> null
    }
    if (initialState != null) {
        val initialStateResId = when (initialState) {
            RecordRule.InitialState.RECORDING ->
                R.string.record_rule_initial_state_recording_summary
            RecordRule.InitialState.PAUSED ->
                R.string.record_rule_initial_state_paused_summary
        }
        append('\n')
        append(stringResource(initialStateResId))
    }
}

@Preview(
    name = "Light Mode",
    showBackground = true,
)
@Preview(
    name = "Dark Mode",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    showBackground = true,
)
@Composable
private fun PreviewRecordRulesScreen() {
    val rules = listOf(
        RecordRule(
            callNumber = RecordRule.CallNumber.Contact("contact"),
            callType = RecordRule.CallType.INCOMING,
            simSlot = RecordRule.SimSlot.Specific(0),
            action = RecordRule.Action.Save(initialState = RecordRule.InitialState.PAUSED),
        ),
        RecordRule(
            callNumber = RecordRule.CallNumber.ContactGroup(0, "group"),
            callType = RecordRule.CallType.ANY,
            simSlot = RecordRule.SimSlot.Any,
            action = RecordRule.Action.Ignore,
        ),
        RecordRule(
            callNumber = RecordRule.CallNumber.Unknown,
            callType = RecordRule.CallType.ANY,
            simSlot = RecordRule.SimSlot.Any,
            action = RecordRule.Action.Save(initialState = RecordRule.InitialState.RECORDING),
        ),
        RecordRule(
            callNumber = RecordRule.CallNumber.Any,
            callType = RecordRule.CallType.ANY,
            simSlot = RecordRule.SimSlot.Any,
            action = RecordRule.Action.Discard(initialState = RecordRule.InitialState.RECORDING),
        ),
    )
    val contacts = mapOf<String, ContactInfo?>(
        "contact" to ContactInfo(lookupKey = "contact", displayName = "Somebody"),
    )
    val groups = mapOf<Long, ContactGroupInfo?>(
        0L to ContactGroupInfo(rowId = 0, sourceId = "group", title = "Family", accountName = null),
    )

    AppTheme {
        AppScreen(
            title = { Text(text = stringResource(R.string.pref_record_rules_name)) },
            onBack = {},
            onReset = {},
        ) { params ->
            RecordRulesContent(
                rules = rules.mapIndexed { i, rule -> DisplayableRule(i, rule) },
                contacts = contacts,
                groups = groups,
                onRuleAdd = {},
                onRuleEdit = { _, _, _ -> },
                onRuleMove = { _, _ -> },
                onRuleRemove = { _ -> },
                contentPadding = params.contentPadding,
            )
        }
    }
}
