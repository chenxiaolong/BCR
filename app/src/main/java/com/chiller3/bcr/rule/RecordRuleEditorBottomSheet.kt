/*
 * SPDX-FileCopyrightText: 2024 Andrew Gunnerson
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.chiller3.bcr.rule

import android.Manifest
import android.content.DialogInterface
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.telephony.SubscriptionManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.BundleCompat
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.chiller3.bcr.Permissions
import com.chiller3.bcr.R
import com.chiller3.bcr.databinding.BottomSheetChipBinding
import com.chiller3.bcr.databinding.RecordRuleEditorBottomSheetBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.ChipGroup
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import kotlin.getValue
import kotlin.math.max

class RecordRuleEditorBottomSheet : BottomSheetDialogFragment(),
    ChipGroup.OnCheckedStateChangeListener, View.OnClickListener {
    private val viewModel: RecordRuleEditorViewModel by viewModels()

    private lateinit var binding: RecordRuleEditorBottomSheetBinding
    private var position = -1
    private var isDefault = false
    private lateinit var recordRule: RecordRule
    private var lastContact: RecordRule.CallNumber.Contact? = null
    private var lastContactGroup: RecordRule.CallNumber.ContactGroup? = null

    private var chipIdCallNumberAny: Int = -1
    private var chipIdCallNumberContact: Int = -1
    private var chipIdCallNumberContactGroup: Int = -1
    private var chipIdCallNumberUnknown: Int = -1

    private var chipIdCallTypeAny: Int = -1
    private var chipIdCallTypeIncoming: Int = -1
    private var chipIdCallTypeOutgoing: Int = -1
    private var chipIdCallTypeConference: Int = -1

    private var chipIdSimSlotAny: Int = -1
    private var chipIdToSimSlot = HashMap<Int, Int>()
    private var simSlotToChipId = HashMap<Int, Int>()

    private var chipIdActionSave: Int = -1
    private var chipIdActionDiscard: Int = -1
    private var chipIdActionIgnore: Int = -1

    private var postPermissionAction: PostPermissionAction? = null

    private val requestPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                // We can now show the proper names of things.
                lastContact?.let { viewModel.lookUpContact(it.lookupKey) }
                lastContactGroup?.let { viewModel.lookUpContactGroup(it.rowId, it.sourceId) }

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

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding = RecordRuleEditorBottomSheetBinding.inflate(inflater, container, false)

        val arguments = requireArguments()
        position = arguments.getInt(ARG_POSITION, -1)
        recordRule = BundleCompat.getParcelable(
            savedInstanceState ?: arguments,
            ARG_RECORD_RULE,
            RecordRule::class.java,
        )!!
        isDefault = arguments.getBoolean(ARG_IS_DEFAULT, false)

        if (savedInstanceState != null) {
            lastContact = BundleCompat.getParcelable(
                savedInstanceState,
                STATE_LAST_CONTACT,
                RecordRule.CallNumber.Contact::class.java,
            )
            lastContactGroup = BundleCompat.getParcelable(
                savedInstanceState,
                STATE_LAST_CONTACT_GROUP,
                RecordRule.CallNumber.ContactGroup::class.java,
            )
            postPermissionAction = BundleCompat.getParcelable(
                savedInstanceState,
                STATE_POST_PERMISSION_ACTION,
                PostPermissionAction::class.java,
            )
        }

        initContactOrGroup()

        binding.title.text = if (position < 0) {
            getString(R.string.pref_add_new_rule_name)
        } else if (isDefault) {
            getString(R.string.record_rules_list_default_name)
        } else {
            getString(R.string.record_rules_list_custom_name, position + 1)
        }

        binding.callNumberChange.setOnClickListener(this)

        addCallNumberChips(inflater)
        addCallTypeChips(inflater)
        addSimSlotChips(inflater)
        addActionChips(inflater)

        binding.callNumberGroup.setOnCheckedStateChangeListener(this)
        binding.callTypeGroup.setOnCheckedStateChangeListener(this)
        binding.simSlotGroup.setOnCheckedStateChangeListener(this)
        binding.actionGroup.setOnCheckedStateChangeListener(this)

        refreshCallNumber()
        refreshCallType()
        refreshSimSlot()
        refreshAction()
        refreshActionDescription()

        // Conditions are not editable for the default rule.
        binding.callNumberLayout.isVisible = !isDefault
        binding.callTypeLayout.isVisible = !isDefault
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        // This implicitly accounts for Android versions too old to report a call's SIM slot.
        binding.simSlotLayout.isVisible = !isDefault && chipIdToSimSlot.size > 1

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
                        lastContact = callNumber
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
                        lastContactGroup = callNumber
                        recordRule = recordRule.copy(callNumber = callNumber)
                        viewModel.useSelectedContactGroup()
                    }
                }
            }
        }

        return binding.root
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putParcelable(ARG_RECORD_RULE, recordRule)
        outState.putParcelable(STATE_LAST_CONTACT, lastContact)
        outState.putParcelable(STATE_LAST_CONTACT_GROUP, lastContactGroup)
        outState.putParcelable(STATE_POST_PERMISSION_ACTION, postPermissionAction)
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)

        setFragmentResult(TAG, bundleOf(
            RESULT_POSITION to position,
            RESULT_RECORD_RULE to recordRule,
        ))
    }

    private fun addChip(inflater: LayoutInflater, parent: ViewGroup): BottomSheetChipBinding {
        val chipBinding = BottomSheetChipBinding.inflate(inflater, parent, false)
        val id = View.generateViewId()
        chipBinding.root.id = id
        chipBinding.root.layoutDirection = View.LAYOUT_DIRECTION_LOCALE
        parent.addView(chipBinding.root)
        return chipBinding
    }

    private fun addCallNumberChips(inflater: LayoutInflater) {
        var chipBinding = addChip(inflater, binding.callNumberGroup)
        chipBinding.root.setText(R.string.record_rule_number_any_chip)
        chipIdCallNumberAny = chipBinding.root.id

        chipBinding = addChip(inflater, binding.callNumberGroup)
        chipBinding.root.setText(R.string.record_rule_number_contact_chip)
        chipIdCallNumberContact = chipBinding.root.id

        chipBinding = addChip(inflater, binding.callNumberGroup)
        chipBinding.root.setText(R.string.record_rule_number_contact_group_chip)
        chipIdCallNumberContactGroup = chipBinding.root.id

        chipBinding = addChip(inflater, binding.callNumberGroup)
        chipBinding.root.setText(R.string.record_rule_number_unknown_chip)
        chipIdCallNumberUnknown = chipBinding.root.id
    }

    private fun addCallTypeChips(inflater: LayoutInflater) {
        var chipBinding = addChip(inflater, binding.callTypeGroup)
        chipBinding.root.setText(R.string.record_rule_type_any_chip)
        chipIdCallTypeAny = chipBinding.root.id

        chipBinding = addChip(inflater, binding.callTypeGroup)
        chipBinding.root.setText(R.string.record_rule_type_incoming_chip)
        chipIdCallTypeIncoming = chipBinding.root.id

        chipBinding = addChip(inflater, binding.callTypeGroup)
        chipBinding.root.setText(R.string.record_rule_type_outgoing_chip)
        chipIdCallTypeOutgoing = chipBinding.root.id

        chipBinding = addChip(inflater, binding.callTypeGroup)
        chipBinding.root.setText(R.string.record_rule_type_conference_chip)
        chipIdCallTypeConference = chipBinding.root.id
    }

    private fun addSimSlotChips(inflater: LayoutInflater) {
        var chipBinding = addChip(inflater, binding.simSlotGroup)
        chipBinding.root.setText(R.string.record_rule_sim_any_chip)
        chipIdSimSlotAny = chipBinding.root.id

        val context = requireContext()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
            && context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE)
                == PackageManager.PERMISSION_GRANTED
            && context.packageManager.hasSystemFeature(
                PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION)) {
            val subscriptionManager = context.getSystemService(SubscriptionManager::class.java)

            val actualSimCount = subscriptionManager.activeSubscriptionInfoCount
            val simCount = recordRule.simSlot.let {
                // We want to preserve the rule, even if the user removed one of the SIMs after the
                // rule was originally created.
                if (it is RecordRule.SimSlot.Specific) {
                    max(actualSimCount, it.slot)
                } else {
                    actualSimCount
                }
            }

            for (simSlot in 1..simCount) {
                chipBinding = addChip(inflater, binding.simSlotGroup)
                chipBinding.root.text =
                    context.getString(R.string.record_rule_sim_specific_chip, simSlot)
                chipIdToSimSlot[chipBinding.root.id] = simSlot
                simSlotToChipId[simSlot] = chipBinding.root.id
            }
        }
    }

    private fun addActionChips(inflater: LayoutInflater) {
        var chipBinding = addChip(inflater, binding.actionGroup)
        chipBinding.root.setText(R.string.record_rule_action_save_chip)
        chipIdActionSave = chipBinding.root.id

        chipBinding = addChip(inflater, binding.actionGroup)
        chipBinding.root.setText(R.string.record_rule_action_discard_chip)
        chipIdActionDiscard = chipBinding.root.id

        chipBinding = addChip(inflater, binding.actionGroup)
        chipBinding.root.setText(R.string.record_rule_action_ignore_chip)
        chipIdActionIgnore = chipBinding.root.id
    }

    private fun initContactOrGroup() {
        when (val callNumber = recordRule.callNumber) {
            RecordRule.CallNumber.Any -> {}
            is RecordRule.CallNumber.Contact -> {
                lastContact = callNumber
                viewModel.lookUpContact(callNumber.lookupKey)
            }
            is RecordRule.CallNumber.ContactGroup -> {
                lastContactGroup = callNumber
                viewModel.lookUpContactGroup(callNumber.rowId, callNumber.sourceId)
            }
            RecordRule.CallNumber.Unknown -> {}
        }
    }

    private fun refreshCallNumber() {
        when (val callNumber = recordRule.callNumber) {
            RecordRule.CallNumber.Any -> {
                binding.callNumberGroup.check(chipIdCallNumberAny)
                binding.callNumberName.isVisible = false
                binding.callNumberChange.isVisible = false
            }
            is RecordRule.CallNumber.Contact -> {
                val contact = viewModel.contactInfoLookup.value

                binding.callNumberGroup.check(chipIdCallNumberContact)
                binding.callNumberName.isVisible = true
                binding.callNumberName.text = contact?.displayName ?: callNumber.lookupKey
                binding.callNumberChange.isVisible = true
                binding.callNumberChange.setText(R.string.record_rule_editor_change_contact)
            }
            is RecordRule.CallNumber.ContactGroup -> {
                val group = viewModel.contactGroupInfoLookup.value

                binding.callNumberGroup.check(chipIdCallNumberContactGroup)
                binding.callNumberName.isVisible = true
                binding.callNumberName.text = buildString {
                    if (group != null) {
                        append(group.title)
                        append('\n')
                        append(group.accountName ?: getString(R.string.pick_contact_group_local_group))
                    } else {
                        append(callNumber.sourceId ?: callNumber.rowId.toString())
                    }
                }
                binding.callNumberChange.isVisible = true
                binding.callNumberChange.setText(R.string.record_rule_editor_change_contact_group)
            }
            RecordRule.CallNumber.Unknown -> {
                binding.callNumberGroup.check(chipIdCallNumberUnknown)
                binding.callNumberName.isVisible = false
                binding.callNumberChange.isVisible = false
            }
        }
    }

    private fun refreshCallType() {
        val chipId = when (recordRule.callType) {
            RecordRule.CallType.ANY -> chipIdCallTypeAny
            RecordRule.CallType.INCOMING -> chipIdCallTypeIncoming
            RecordRule.CallType.OUTGOING -> chipIdCallTypeOutgoing
            RecordRule.CallType.CONFERENCE -> chipIdCallTypeConference
        }

        binding.callTypeGroup.check(chipId)
    }

    private fun refreshSimSlot() {
        val chipId = when (val simSlot = recordRule.simSlot) {
            RecordRule.SimSlot.Any -> chipIdSimSlotAny
            is RecordRule.SimSlot.Specific -> simSlotToChipId[simSlot.slot]!!
        }

        binding.simSlotGroup.check(chipId)
    }

    private fun refreshAction() {
        val chipId = when (recordRule.action) {
            RecordRule.Action.SAVE -> chipIdActionSave
            RecordRule.Action.DISCARD -> chipIdActionDiscard
            RecordRule.Action.IGNORE -> chipIdActionIgnore
        }

        binding.actionGroup.check(chipId)
    }

    private fun refreshActionDescription() {
        val descriptionId = when (recordRule.action) {
            RecordRule.Action.SAVE -> R.string.record_rule_action_save_desc
            RecordRule.Action.DISCARD -> R.string.record_rule_action_discard_desc
            RecordRule.Action.IGNORE -> R.string.record_rule_action_ignore_desc
        }

        binding.actionDesc.setText(descriptionId)
    }

    override fun onCheckedChanged(group: ChipGroup, checkedIds: MutableList<Int>) {
        when (group) {
            binding.callNumberGroup -> {
                when (checkedIds.first()) {
                    chipIdCallNumberAny ->
                        recordRule = recordRule.copy(callNumber = RecordRule.CallNumber.Any)
                    chipIdCallNumberContact -> {
                        val lastContact = lastContact
                        if (lastContact != null) {
                            recordRule = recordRule.copy(callNumber = lastContact)
                        } else {
                            selectContact()
                        }
                    }
                    chipIdCallNumberContactGroup -> {
                        val lastContactGroup = lastContactGroup
                        if (lastContactGroup != null) {
                            recordRule = recordRule.copy(callNumber = lastContactGroup)
                        } else {
                            selectContactGroup()
                        }
                    }
                    chipIdCallNumberUnknown ->
                        recordRule = recordRule.copy(callNumber = RecordRule.CallNumber.Unknown)
                }

                // If we're switching to a contact or contact group, we'll show the originally
                // selected option until the new contact or contact group is selected.
                refreshCallNumber()
            }
            binding.callTypeGroup -> {
                val callType = when (checkedIds.first()) {
                    chipIdCallTypeAny -> RecordRule.CallType.ANY
                    chipIdCallTypeIncoming -> RecordRule.CallType.INCOMING
                    chipIdCallTypeOutgoing -> RecordRule.CallType.OUTGOING
                    chipIdCallTypeConference -> RecordRule.CallType.CONFERENCE
                    else -> throw IllegalStateException("Invalid call type chip ID")
                }

                recordRule = recordRule.copy(callType = callType)
            }
            binding.simSlotGroup -> {
                val simSlot = when (val checkedId = checkedIds.first()) {
                    chipIdSimSlotAny -> RecordRule.SimSlot.Any
                    else -> chipIdToSimSlot[checkedId]?.let { RecordRule.SimSlot.Specific(it) }
                        ?: throw IllegalStateException("Invalid SIM slot chip ID")
                }

                recordRule = recordRule.copy(simSlot = simSlot)
            }
            binding.actionGroup -> {
                val action = when (checkedIds.first()) {
                    chipIdActionSave -> RecordRule.Action.SAVE
                    chipIdActionDiscard -> RecordRule.Action.DISCARD
                    chipIdActionIgnore -> RecordRule.Action.IGNORE
                    else -> throw IllegalStateException("Invalid action chip ID")
                }

                recordRule = recordRule.copy(action = action)

                refreshActionDescription()
            }
        }
    }

    override fun onClick(v: View?) {
        when (v) {
            binding.callNumberChange -> {
                when (val callNumber = recordRule.callNumber) {
                    is RecordRule.CallNumber.Contact -> selectContact()
                    is RecordRule.CallNumber.ContactGroup -> selectContactGroup()
                    RecordRule.CallNumber.Any, RecordRule.CallNumber.Unknown ->
                        throw IllegalStateException("Clicked change button for $callNumber")
                }
            }
        }
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

    @Parcelize
    private enum class PostPermissionAction : Parcelable {
        SELECT_CONTACT,
        SELECT_CONTACT_GROUP,
    }

    companion object {
        val TAG: String = RecordRuleEditorBottomSheet::class.java.simpleName

        private const val ARG_POSITION = "position"
        private const val ARG_RECORD_RULE = "record_rule"
        private const val ARG_IS_DEFAULT = "is_default"

        const val RESULT_POSITION = ARG_POSITION
        const val RESULT_RECORD_RULE = ARG_RECORD_RULE

        private const val STATE_LAST_CONTACT = "last_contact"
        private const val STATE_LAST_CONTACT_GROUP = "last_contact_group"
        private const val STATE_POST_PERMISSION_ACTION = "post_permission_action"

        fun newInstance(
            position: Int,
            recordRule: RecordRule,
            isDefault: Boolean,
        ) = RecordRuleEditorBottomSheet().apply {
            arguments = bundleOf(
                ARG_POSITION to position,
                ARG_RECORD_RULE to recordRule,
                ARG_IS_DEFAULT to isDefault,
            )
        }
    }
}
