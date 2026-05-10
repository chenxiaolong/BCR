/*
 * SPDX-FileCopyrightText: 2024-2026 Andrew Gunnerson
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.chiller3.bcr.rule

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.telephony.SubscriptionManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.BundleCompat
import androidx.fragment.app.FragmentResultListener
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import com.chiller3.bcr.Permissions
import com.chiller3.bcr.PreferenceBaseFragment
import com.chiller3.bcr.Preferences
import com.chiller3.bcr.R
import com.chiller3.bcr.dialog.RecordRuleChoiceDialogFragment
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import kotlin.getValue
import kotlin.math.max

class RecordRuleEditorFragment : PreferenceBaseFragment(), Preference.OnPreferenceClickListener,
    FragmentResultListener {
    companion object {
        internal const val ARG_POSITION = "position"
        internal const val ARG_RECORD_RULE = "record_rule"
        internal const val ARG_IS_DEFAULT = "is_default"

        internal const val RESULT_POSITION = ARG_POSITION
        internal const val RESULT_RECORD_RULE = ARG_RECORD_RULE

        private const val STATE_POST_PERMISSION_ACTION = "post_permission_action"
    }

    @Parcelize
    private enum class PostPermissionAction : Parcelable {
        SELECT_CONTACT,
        SELECT_CONTACT_GROUP,
    }

    override val requestTag: String = RecordRuleEditorFragment::class.java.simpleName

    private val viewModel: RecordRuleEditorViewModel by viewModels()

    private var position = -1
    private var isDefault = false
    private lateinit var _recordRule: RecordRule
    private var recordRule: RecordRule
        get() = _recordRule
        set(rule) {
            _recordRule = rule

            setFragmentResult(requestTag, Bundle().apply {
                putInt(RESULT_POSITION, position)
                putParcelable(RESULT_RECORD_RULE, rule)
            })
        }

    private lateinit var prefs: Preferences
    private lateinit var categoryRuleConditions: PreferenceCategory
    private lateinit var prefCallNumber: Preference
    private lateinit var prefCallType: Preference
    private lateinit var prefSimSlot: Preference
    private lateinit var prefAction: Preference
    private lateinit var prefInitialState: Preference

    private var postPermissionAction: PostPermissionAction? = null

    private val requestPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                // We can now show the proper names of things.
                initContactOrGroup()

                processPostPermissionAction()
            } else {
                startActivity(Permissions.getAppInfoIntent(requireContext()))
            }
        }

    // We don't bother using persisted URI permissions because we need the full READ_CONTACTS
    // permission for this feature to work at all (eg. to perform lookups by number).
    private val requestContact =
        registerForActivityResult(ActivityResultContracts.PickContact()) { uri ->
            uri?.let { viewModel.selectContact(it) }
        }
    private val requestContactGroup =
        registerForActivityResult(PickContactGroup()) { group ->
            group?.let { viewModel.selectContactGroup(it) }
        }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val arguments = requireArguments()
        position = arguments.getInt(ARG_POSITION, -1)
        recordRule = BundleCompat.getParcelable(
            savedInstanceState ?: arguments,
            ARG_RECORD_RULE,
            RecordRule::class.java,
        )!!
        isDefault = arguments.getBoolean(ARG_IS_DEFAULT, false)

        if (savedInstanceState != null) {
            postPermissionAction = BundleCompat.getParcelable(
                savedInstanceState,
                STATE_POST_PERMISSION_ACTION,
                PostPermissionAction::class.java,
            )
        }

        initContactOrGroup()

        val context = requireContext()

        // Not strictly necessary since all preferences here are non-persistent.
        preferenceManager.setStorageDeviceProtected()
        setPreferencesFromResource(R.xml.preferences_record_rule, rootKey)

        prefs = Preferences(context)

        categoryRuleConditions = findPreference(Preferences.CATEGORY_RULE_CONDITIONS)!!

        prefCallNumber = findPreference(Preferences.PREF_CALL_NUMBER)!!
        prefCallNumber.onPreferenceClickListener = this

        prefCallType = findPreference(Preferences.PREF_CALL_TYPE)!!
        prefCallType.onPreferenceClickListener = this

        prefSimSlot = findPreference(Preferences.PREF_SIM_SLOT)!!
        prefSimSlot.onPreferenceClickListener = this

        prefAction = findPreference(Preferences.PREF_ACTION)!!
        prefAction.onPreferenceClickListener = this

        prefInitialState = findPreference(Preferences.PREF_INITIAL_STATE)!!
        prefInitialState.onPreferenceClickListener = this

        refreshCallNumber()
        refreshCallType()
        refreshSimSlot()
        refreshAction()
        refreshInitialState()

        // Conditions are not editable for the default rule.
        categoryRuleConditions.isVisible = !isDefault

        parentFragmentManager.setFragmentResultListener(RecordRuleChoiceDialogFragment.TAG, this, this)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.contactInfoLookup.collect {
                    refreshCallNumber()
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.contactGroupInfoLookup.collect {
                    refreshCallNumber()
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.contactInfoSelection.collect {
                    if (it != null) {
                        val callNumber = RecordRule.CallNumber.Contact(it.lookupKey)
                        recordRule = recordRule.copy(callNumber = callNumber)
                        viewModel.useSelectedContact()
                    }
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.contactGroupInfoSelection.collect {
                    if (it != null) {
                        val callNumber = RecordRule.CallNumber.ContactGroup(it.rowId, it.sourceId)
                        recordRule = recordRule.copy(callNumber = callNumber)
                        viewModel.useSelectedContactGroup()
                    }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putParcelable(ARG_RECORD_RULE, recordRule)
        outState.putParcelable(STATE_POST_PERMISSION_ACTION, postPermissionAction)
    }

    private fun initContactOrGroup() {
        when (val callNumber = recordRule.callNumber) {
            RecordRule.CallNumber.Any -> {}
            is RecordRule.CallNumber.Contact -> {
                viewModel.lookUpContact(callNumber.lookupKey)
            }
            is RecordRule.CallNumber.ContactGroup -> {
                viewModel.lookUpContactGroup(callNumber.rowId, callNumber.sourceId)
            }
            RecordRule.CallNumber.Unknown -> {}
        }
    }

    private fun refreshCallNumber() {
        when (val callNumber = recordRule.callNumber) {
            RecordRule.CallNumber.Any -> {
                prefCallNumber.setSummary(R.string.record_rule_number_any_item)
            }
            is RecordRule.CallNumber.Contact -> {
                val contact = viewModel.contactInfoLookup.value

                prefCallNumber.summary = getString(
                    R.string.record_rule_number_contact_summary,
                    contact?.displayName ?: callNumber.lookupKey,
                )
            }
            is RecordRule.CallNumber.ContactGroup -> {
                val group = viewModel.contactGroupInfoLookup.value

                prefCallNumber.summary = if (group?.title != null) {
                    getString(
                        R.string.record_rule_number_contact_group_with_account_summary,
                        group.title,
                        group.accountName ?: getString(R.string.pick_contact_group_local_group),
                    )
                } else {
                    getString(
                        R.string.record_rule_number_contact_group_unknown_summary,
                        callNumber.sourceId ?: callNumber.rowId.toString(),
                    )
                }
            }
            RecordRule.CallNumber.Unknown -> {
                prefCallNumber.setSummary(R.string.record_rule_number_unknown_item)
            }
        }
    }

    private fun refreshCallType() {
        prefCallType.isVisible = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

        val summaryResId = when (recordRule.callType) {
            RecordRule.CallType.ANY -> R.string.record_rule_type_any_item
            RecordRule.CallType.INCOMING -> R.string.record_rule_type_incoming_item
            RecordRule.CallType.OUTGOING -> R.string.record_rule_type_outgoing_item
            RecordRule.CallType.CONFERENCE -> R.string.record_rule_type_conference_item
        }

        prefCallType.setSummary(summaryResId)
    }

    private fun refreshSimSlot() {
        val context = requireContext()

        val canUseSimSlot = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                && context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) ==
                PackageManager.PERMISSION_GRANTED
        if (!canUseSimSlot) {
            prefSimSlot.isVisible = false
            return
        }

        val subscriptionManager = context.getSystemService(SubscriptionManager::class.java)
        var simCount = subscriptionManager.activeSubscriptionInfoCount

        prefSimSlot.summary = when (val simSlot = recordRule.simSlot) {
            RecordRule.SimSlot.Any -> getString(R.string.record_rule_sim_any_item)
            is RecordRule.SimSlot.Specific -> {
                // The SIM slot configuration should be visible even if the user removed a SIM after
                // the rule was initially created.
                simCount = max(simCount, simSlot.slot)
                getString(R.string.record_rule_sim_specific_item, simSlot.slot)
            }
        }

        prefSimSlot.isVisible = simCount > 1
    }

    private fun refreshAction() {
        val summaryResId = when (recordRule.action) {
            is RecordRule.Action.Save -> R.string.record_rule_action_save_desc
            is RecordRule.Action.Discard -> R.string.record_rule_action_discard_desc
            is RecordRule.Action.Ignore -> R.string.record_rule_action_ignore_desc
        }

        prefAction.setSummary(summaryResId)
    }

    private fun refreshInitialState() {
        val initialState = when (val action = recordRule.action) {
            is RecordRule.Action.Save -> action.initialState
            is RecordRule.Action.Discard -> action.initialState
            is RecordRule.Action.Ignore -> {
                prefInitialState.isVisible = false
                return
            }
        }

        prefInitialState.isVisible = true

        val summaryResId = when (initialState) {
            RecordRule.InitialState.RECORDING -> R.string.record_rule_initial_state_recording_desc
            RecordRule.InitialState.PAUSED -> R.string.record_rule_initial_state_paused_desc
        }

        prefInitialState.setSummary(summaryResId)
    }

    override fun onFragmentResult(requestKey: String, bundle: Bundle) {
        when (requestKey) {
            RecordRuleChoiceDialogFragment.TAG -> {
                val result = BundleCompat.getParcelable(
                    bundle,
                    RecordRuleChoiceDialogFragment.RESULT_RESULT,
                    RecordRuleChoiceDialogFragment.Result::class.java,
                ) ?: return

                when (result) {
                    RecordRuleChoiceDialogFragment.Result.CallNumber.ANY -> {
                        recordRule = recordRule.copy(callNumber = RecordRule.CallNumber.Any)
                        refreshCallNumber()
                    }
                    RecordRuleChoiceDialogFragment.Result.CallNumber.CONTACT -> {
                        selectContact()
                    }
                    RecordRuleChoiceDialogFragment.Result.CallNumber.CONTACT_GROUP -> {
                        selectContactGroup()
                    }
                    RecordRuleChoiceDialogFragment.Result.CallNumber.UNKNOWN -> {
                        recordRule = recordRule.copy(callNumber = RecordRule.CallNumber.Unknown)
                        refreshCallNumber()
                    }
                    is RecordRuleChoiceDialogFragment.Result.CallType -> {
                        recordRule = recordRule.copy(callType = result.callType)
                        refreshCallType()
                    }
                    is RecordRuleChoiceDialogFragment.Result.SimSlot -> {
                        recordRule = recordRule.copy(simSlot = result.simSlot)
                        refreshSimSlot()
                    }
                    is RecordRuleChoiceDialogFragment.Result.Action -> {
                        recordRule = recordRule.copy(action = result.action)
                        refreshAction()
                        refreshInitialState()
                    }
                    is RecordRuleChoiceDialogFragment.Result.InitialState -> {
                        val action = when (val action = recordRule.action) {
                            is RecordRule.Action.Save ->
                                action.copy(initialState = result.initialState)
                            is RecordRule.Action.Discard ->
                                action.copy(initialState = result.initialState)
                            RecordRule.Action.Ignore -> action
                        }

                        recordRule = recordRule.copy(action = action)

                        refreshInitialState()
                    }
                }
            }
        }
    }

    override fun onPreferenceClick(preference: Preference): Boolean {
        val action = when (preference) {
            prefCallNumber -> RecordRuleChoiceDialogFragment.Action.CallNumber
            prefCallType -> RecordRuleChoiceDialogFragment.Action.CallType
            prefSimSlot -> RecordRuleChoiceDialogFragment.Action.SimSlot
            prefAction -> RecordRuleChoiceDialogFragment.Action.Action(recordRule.action)
            prefInitialState -> RecordRuleChoiceDialogFragment.Action.InitialState
            else -> return false
        }

        RecordRuleChoiceDialogFragment.newInstance(action).show(
            parentFragmentManager.beginTransaction(),
            RecordRuleChoiceDialogFragment.TAG,
        )

        return true
    }

    private fun selectContact() {
        val context = requireContext()

        if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS)
                != PackageManager.PERMISSION_GRANTED) {
            postPermissionAction = PostPermissionAction.SELECT_CONTACT
            requestPermission.launch(Manifest.permission.READ_CONTACTS)
        } else {
            requestContact.launch(null)
        }
    }

    private fun selectContactGroup() {
        val context = requireContext()

        if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS)
                != PackageManager.PERMISSION_GRANTED) {
            postPermissionAction = PostPermissionAction.SELECT_CONTACT_GROUP
            requestPermission.launch(Manifest.permission.READ_CONTACTS)
        } else {
            requestContactGroup.launch(null)
        }
    }

    private fun processPostPermissionAction() {
        when (postPermissionAction) {
            PostPermissionAction.SELECT_CONTACT -> selectContact()
            PostPermissionAction.SELECT_CONTACT_GROUP -> selectContactGroup()
            null -> throw IllegalStateException("Prompted for permissions without action")
        }

        postPermissionAction = null
    }
}
