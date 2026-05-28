/*
 * SPDX-FileCopyrightText: 2024-2026 Andrew Gunnerson
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.chiller3.bcr.rule

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Parcelable
import android.telephony.SubscriptionManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chiller3.bcr.ContactGroupInfo
import com.chiller3.bcr.ContactInfo
import com.chiller3.bcr.Permissions
import com.chiller3.bcr.R
import com.chiller3.bcr.ui.AppScreen
import com.chiller3.bcr.ui.BetterSegmentedShapes
import com.chiller3.bcr.ui.Preference
import com.chiller3.bcr.ui.PreferenceCategory
import com.chiller3.bcr.ui.PreferenceColumn
import com.chiller3.bcr.ui.theme.AppTheme
import kotlinx.parcelize.Parcelize
import kotlin.math.max

@Parcelize
private enum class PostPermissionAction : Parcelable {
    SELECT_CONTACT,
    SELECT_CONTACT_GROUP,
}

@Composable
fun RecordRuleEditorScreen(
    position: Int,
    initialRule: RecordRule,
    isDefault: Boolean,
    onRuleUpdate: (RecordRule) -> Unit,
    onBack: () -> Unit,
    viewModel: RecordRuleEditorViewModel = viewModel(),
) {
    val resources = LocalResources.current

    val rule by viewModel.initOrGetExisting(initialRule).collectAsStateWithLifecycle()
    LaunchedEffect(rule) {
        onRuleUpdate(rule)
    }

    val contactInfo by viewModel.contactInfo.collectAsStateWithLifecycle()
    val contactGroupInfo by viewModel.contactGroupInfo.collectAsStateWithLifecycle()

    val context = LocalContext.current
    var postPermissionAction by rememberSaveable { mutableStateOf<PostPermissionAction?>(null) }

    // We don't bother using persisted URI permissions because we need the full READ_CONTACTS
    // permission for this feature to work at all (eg. to perform lookups by number).
    val requestContact = rememberLauncherForActivityResult(
        ActivityResultContracts.PickContact(),
    ) { uri ->
        uri?.let { viewModel.selectContact(it) }
    }

    val requestContactLaunch = {
        try {
            requestContact.launch(null)
        } catch (_: ActivityNotFoundException) {
            viewModel.addAlert(RecordRuleEditorAlert.ContactPickerNotFound)
        }
    }

    val requestContactGroup = rememberLauncherForActivityResult(PickContactGroup()) { group ->
        group?.let { viewModel.selectContactGroup(it) }
    }

    val requestPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            // We can now show the proper names of things.
            viewModel.refresh()

            when (postPermissionAction) {
                PostPermissionAction.SELECT_CONTACT -> requestContactLaunch()
                PostPermissionAction.SELECT_CONTACT_GROUP -> requestContactGroup.launch(null)
                null -> throw IllegalStateException("Prompted for permissions without action")
            }

            postPermissionAction = null
        } else {
            context.startActivity(Permissions.getAppInfoIntent(context))
        }
    }

    AppScreen(
        title = {
            val title = if (position < 0) {
                stringResource(R.string.pref_add_new_rule_name)
            } else if (isDefault) {
                stringResource(R.string.record_rules_list_default_name)
            } else {
                stringResource(R.string.record_rules_list_custom_name, position + 1)
            }

            Text(text = title)
        },
        onBack = onBack,
    ) { params ->
        LaunchedEffect(Unit) {
            viewModel.alerts.collect { alerts ->
                val alert = alerts.firstOrNull() ?: return@collect
                val msg = when (alert) {
                    RecordRuleEditorAlert.ContactPickerNotFound ->
                        resources.getString(R.string.contact_picker_not_found)
                }

                params.snackbarHostState.showSnackbar(message = msg, withDismissAction = true)
                viewModel.acknowledgeFirstAlert()
            }
        }

        RecordRuleEditorContent(
            rule = rule,
            isDefault = isDefault,
            contactInfo = contactInfo,
            contactGroupInfo = contactGroupInfo,
            simCount = simCount(rule),
            onRuleUpdate = { viewModel.setRule(it) },
            onContactSelect = {
                if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS)
                        != PackageManager.PERMISSION_GRANTED) {
                    postPermissionAction = PostPermissionAction.SELECT_CONTACT
                    requestPermission.launch(Manifest.permission.READ_CONTACTS)
                } else {
                    requestContactLaunch()
                }
            },
            onContactGroupSelect = {
                if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS)
                        != PackageManager.PERMISSION_GRANTED) {
                    postPermissionAction = PostPermissionAction.SELECT_CONTACT_GROUP
                    requestPermission.launch(Manifest.permission.READ_CONTACTS)
                } else {
                    requestContactGroup.launch(null)
                }
            },
            contentPadding = params.contentPadding,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RecordRuleEditorContent(
    rule: RecordRule,
    isDefault: Boolean,
    contactInfo: ContactInfo?,
    contactGroupInfo: ContactGroupInfo?,
    simCount: Int,
    onRuleUpdate: (RecordRule) -> Unit,
    onContactSelect: () -> Unit,
    onContactGroupSelect: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
) {
    var showChoiceDialog by rememberSaveable { mutableStateOf<RecordRuleChoiceAction?>(null) }

    val initialStateSummary = initialStateSummary(rule)

    PreferenceColumn(contentPadding = contentPadding) {
        if (!isDefault) {
            item(key = "rule_conditions") {
                PreferenceCategory(
                    title = { Text(text = stringResource(R.string.pref_header_rule_conditions)) },
                    modifier = Modifier.animateItem(),
                )
            }

            val hasCallType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
            val hasSimSlot = simCount > 1

            item(key = "call_number") {
                Preference(
                    onClick = { showChoiceDialog = RecordRuleChoiceAction.CallNumber },
                    shapes = if (hasCallType || hasSimSlot) {
                        BetterSegmentedShapes.top()
                    } else {
                        BetterSegmentedShapes.single()
                    },
                    title = { Text(text = stringResource(R.string.record_rule_number_title)) },
                    summary = { Text(text = callNumberSummary(rule, contactInfo, contactGroupInfo)) },
                    modifier = Modifier.animateItem(),
                )
            }

            if (hasCallType) {
                item(key = "call_type") {
                    Preference(
                        onClick = { showChoiceDialog = RecordRuleChoiceAction.CallType },
                        shapes = if (hasSimSlot) {
                            BetterSegmentedShapes.middle()
                        } else {
                            BetterSegmentedShapes.bottom()
                        },
                        title = { Text(text = stringResource(R.string.record_rule_type_title)) },
                        summary = { Text(text = callTypeSummary(rule)) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }

            if (simCount > 1) {
                item(key = "sim_slot") {
                    Preference(
                        onClick = { showChoiceDialog = RecordRuleChoiceAction.SimSlot },
                        shapes = BetterSegmentedShapes.bottom(),
                        title = { Text(text = stringResource(R.string.record_rule_type_title)) },
                        summary = { Text(text = simSlotSummary(rule)) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }

        item(key = "rule_actions") {
            PreferenceCategory(
                title = { Text(text = stringResource(R.string.pref_header_rule_actions)) },
                modifier = Modifier.animateItem(),
            )
        }

        val hasInitialState = initialStateSummary != null

        item(key = "action") {
            Preference(
                onClick = { showChoiceDialog = RecordRuleChoiceAction.Action(rule.action) },
                shapes = if (hasInitialState) {
                    BetterSegmentedShapes.top()
                } else {
                    BetterSegmentedShapes.single()
                },
                title = { Text(text = stringResource(R.string.record_rule_action_title)) },
                summary = { Text(text = actionSummary(rule)) },
                modifier = Modifier.animateItem(),
            )
        }

        if (hasInitialState) {
            item(key = "initial_state") {
                Preference(
                    onClick = { showChoiceDialog = RecordRuleChoiceAction.InitialState },
                    shapes = BetterSegmentedShapes.bottom(),
                    title = { Text(text = stringResource(R.string.record_rule_initial_state_title)) },
                    summary = { Text(text = initialStateSummary) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }

    showChoiceDialog?.let { action ->
        RecordRuleChoiceDialog(
            action = action,
            onSelect = { result ->
                when (result) {
                    RecordRuleChoiceResult.CallNumber.ANY -> {
                        onRuleUpdate(rule.copy(callNumber = RecordRule.CallNumber.Any))
                    }
                    RecordRuleChoiceResult.CallNumber.CONTACT -> {
                        onContactSelect()
                    }
                    RecordRuleChoiceResult.CallNumber.CONTACT_GROUP -> {
                        onContactGroupSelect()
                    }
                    RecordRuleChoiceResult.CallNumber.UNKNOWN -> {
                        onRuleUpdate(rule.copy(callNumber = RecordRule.CallNumber.Unknown))
                    }
                    is RecordRuleChoiceResult.CallType -> {
                        onRuleUpdate(rule.copy(callType = result.callType))
                    }
                    is RecordRuleChoiceResult.SimSlot -> {
                        onRuleUpdate(rule.copy(simSlot = result.simSlot))
                    }
                    is RecordRuleChoiceResult.Action -> {
                        onRuleUpdate(rule.copy(action = result.action))
                    }
                    is RecordRuleChoiceResult.InitialState -> {
                        val action = when (val action = rule.action) {
                            is RecordRule.Action.Save ->
                                action.copy(initialState = result.initialState)
                            is RecordRule.Action.Discard ->
                                action.copy(initialState = result.initialState)
                            RecordRule.Action.Ignore -> action
                        }

                        onRuleUpdate(rule.copy(action = action))
                    }
                }

                @Suppress("AssignedValueIsNeverRead")
                showChoiceDialog = null
            },
            onDismiss = {
                @Suppress("AssignedValueIsNeverRead")
                showChoiceDialog = null
            },
        )
    }
}

@Composable
private fun callNumberSummary(
    rule: RecordRule,
    contactInfo: ContactInfo?,
    contactGroupInfo: ContactGroupInfo?,
) = when (val callNumber = rule.callNumber) {
    RecordRule.CallNumber.Any -> {
        stringResource(R.string.record_rule_number_any_item)
    }
    is RecordRule.CallNumber.Contact -> {
        stringResource(
            R.string.record_rule_number_contact_summary,
            contactInfo?.displayName ?: callNumber.lookupKey,
        )
    }
    is RecordRule.CallNumber.ContactGroup -> {
        if (contactGroupInfo?.title != null) {
            stringResource(
                R.string.record_rule_number_contact_group_with_account_summary,
                contactGroupInfo.title,
                contactGroupInfo.accountName
                    ?: stringResource(R.string.pick_contact_group_local_group),
            )
        } else {
            stringResource(
                R.string.record_rule_number_contact_group_unknown_summary,
                callNumber.sourceId ?: callNumber.rowId.toString(),
            )
        }
    }
    RecordRule.CallNumber.Unknown -> {
        stringResource(R.string.record_rule_number_unknown_item)
    }
}

@Composable
private fun callTypeSummary(rule: RecordRule) = when (rule.callType) {
    RecordRule.CallType.ANY -> stringResource(R.string.record_rule_type_any_item)
    RecordRule.CallType.INCOMING -> stringResource(R.string.record_rule_type_incoming_item)
    RecordRule.CallType.OUTGOING -> stringResource(R.string.record_rule_type_outgoing_item)
    RecordRule.CallType.CONFERENCE -> stringResource(R.string.record_rule_type_conference_item)
}

@Composable
private fun simCount(rule: RecordRule): Int {
    val context = LocalContext.current

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
            && context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) ==
                    PackageManager.PERMISSION_GRANTED) {
        val subscriptionManager = context.getSystemService(SubscriptionManager::class.java)

        // The SIM slot configuration should be visible even if the user removed a SIM after the
        // rule was initially created.
        var simCount = subscriptionManager.activeSubscriptionInfoCount
        if (rule.simSlot is RecordRule.SimSlot.Specific) {
            simCount = max(simCount, rule.simSlot.slot)
        }

        return simCount
    }

    return 1
}

@Composable
private fun simSlotSummary(rule: RecordRule) = when (rule.simSlot) {
    RecordRule.SimSlot.Any -> stringResource(R.string.record_rule_sim_any_item)
    is RecordRule.SimSlot.Specific ->
        stringResource(R.string.record_rule_sim_specific_item, rule.simSlot.slot)
}

@Composable
private fun actionSummary(rule: RecordRule) = when (rule.action) {
    is RecordRule.Action.Save -> stringResource(R.string.record_rule_action_save_desc)
    is RecordRule.Action.Discard -> stringResource(R.string.record_rule_action_discard_desc)
    is RecordRule.Action.Ignore -> stringResource(R.string.record_rule_action_ignore_desc)
}

@Composable
private fun initialStateSummary(rule: RecordRule): String? {
    val initialState = when (rule.action) {
        is RecordRule.Action.Save -> rule.action.initialState
        is RecordRule.Action.Discard -> rule.action.initialState
        is RecordRule.Action.Ignore -> return null
    }

    return when (initialState) {
        RecordRule.InitialState.RECORDING ->
            stringResource(R.string.record_rule_initial_state_recording_desc)
        RecordRule.InitialState.PAUSED ->
            stringResource(R.string.record_rule_initial_state_paused_desc)
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
private fun PreviewCustomRule() {
    AppTheme {
        AppScreen(
            title = { Text(text = stringResource(R.string.record_rules_list_custom_name, 1)) },
            onBack = {},
        ) { params ->
            RecordRuleEditorContent(
                rule = RecordRule(
                    callNumber = RecordRule.CallNumber.Contact("contact"),
                    callType = RecordRule.CallType.INCOMING,
                    simSlot = RecordRule.SimSlot.Specific(0),
                    action = RecordRule.Action.Save(initialState = RecordRule.InitialState.PAUSED),
                ),
                isDefault = false,
                contactInfo = ContactInfo(lookupKey = "contact", displayName = "Somebody"),
                contactGroupInfo = null,
                simCount = 2,
                onRuleUpdate = {},
                onContactSelect = {},
                onContactGroupSelect = {},
                contentPadding = params.contentPadding,
            )
        }
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
private fun PreviewDefaultRule() {
    AppTheme {
        AppScreen(
            title = { Text(text = stringResource(R.string.record_rules_list_default_name)) },
            onBack = {},
        ) { params ->
            RecordRuleEditorContent(
                rule = RecordRule(
                    callNumber = RecordRule.CallNumber.Any,
                    callType = RecordRule.CallType.ANY,
                    simSlot = RecordRule.SimSlot.Any,
                    action = RecordRule.Action.Save(initialState = RecordRule.InitialState.PAUSED),
                ),
                isDefault = true,
                contactInfo = null,
                contactGroupInfo = null,
                simCount = 2,
                onRuleUpdate = {},
                onContactSelect = {},
                onContactGroupSelect = {},
                contentPadding = params.contentPadding,
            )
        }
    }
}
