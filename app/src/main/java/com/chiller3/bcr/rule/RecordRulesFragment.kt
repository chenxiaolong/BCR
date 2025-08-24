/*
 * SPDX-FileCopyrightText: 2023-2024 Andrew Gunnerson
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.chiller3.bcr.rule

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.os.BundleCompat
import androidx.core.view.MenuProvider
import androidx.fragment.app.setFragmentResultListener
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.Preference
import androidx.preference.PreferenceScreen
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.chiller3.bcr.PreferenceBaseFragment
import com.chiller3.bcr.Preferences
import com.chiller3.bcr.R
import kotlinx.coroutines.launch

class RecordRulesFragment : PreferenceBaseFragment(), Preference.OnPreferenceClickListener,
    RecordRulesAdapter.Listener {
    private val viewModel: RecordRulesViewModel by viewModels()

    private lateinit var prefAddNewRule: Preference

    private val globalAdapter = ConcatAdapter()
    private lateinit var rulesAdapter: RecordRulesAdapter

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.record_rules_preferences, rootKey)

        val context = requireContext()

        prefAddNewRule = findPreference(Preferences.PREF_ADD_NEW_RULE)!!
        prefAddNewRule.onPreferenceClickListener = this

        rulesAdapter = RecordRulesAdapter(context, this)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.rules.collect {
                    rulesAdapter.setDisplayableRules(it)
                }
            }
        }

        setFragmentResultListener(RecordRuleEditorBottomSheet.TAG) { _, result ->
            val position = result.getInt(RecordRuleEditorBottomSheet.RESULT_POSITION)
            val recordRule = BundleCompat.getParcelable(
                result,
                RecordRuleEditorBottomSheet.RESULT_RECORD_RULE,
                RecordRule::class.java,
            )!!

            if (position >= 0) {
                viewModel.replaceRule(position, recordRule)
            } else {
                viewModel.addRule(recordRule)
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.record_rules, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.reset -> {
                        viewModel.reset()
                        true
                    }
                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    override fun onCreateRecyclerView(
        inflater: LayoutInflater,
        parent: ViewGroup,
        savedInstanceState: Bundle?
    ): RecyclerView = super.onCreateRecyclerView(inflater, parent, savedInstanceState).apply {
        ItemTouchHelper(RecordRulesTouchHelperCallback(rulesAdapter)).attachToRecyclerView(this)
    }

    override fun onCreateLayoutManager(): RecyclerView.LayoutManager {
        return RecordRulesLayoutManager(requireContext(), globalAdapter)
    }

    override fun onCreateAdapter(preferenceScreen: PreferenceScreen): RecyclerView.Adapter<*> {
        globalAdapter.addAdapter(super.onCreateAdapter(preferenceScreen))
        globalAdapter.addAdapter(rulesAdapter)
        return globalAdapter
    }

    override fun onPreferenceClick(preference: Preference): Boolean {
        when (preference) {
            prefAddNewRule -> {
                val newRule = RecordRule(
                    callNumber = RecordRule.CallNumber.Any,
                    callType = RecordRule.CallType.ANY,
                    simSlot = RecordRule.SimSlot.Any,
                    action = RecordRule.Action.SAVE,
                )

                RecordRuleEditorBottomSheet.newInstance(-1, newRule, false)
                    .show(parentFragmentManager.beginTransaction(), RecordRuleEditorBottomSheet.TAG)
                return true
            }
        }

        return false
    }

    override fun onRulesChanged(rules: List<RecordRule>) {
        viewModel.replaceRules(rules)
    }

    override fun onRuleSelected(position: Int, rule: RecordRule, isDefault: Boolean) {
        RecordRuleEditorBottomSheet.newInstance(position, rule, isDefault)
            .show(parentFragmentManager.beginTransaction(), RecordRuleEditorBottomSheet.TAG)
    }
}
