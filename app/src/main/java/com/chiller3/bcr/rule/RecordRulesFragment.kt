/*
 * SPDX-FileCopyrightText: 2023-2026 Andrew Gunnerson
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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.IntentCompat
import androidx.core.view.MenuProvider
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
    override val requestTag: String = RecordRulesFragment::class.java.simpleName

    private val viewModel: RecordRulesViewModel by viewModels()

    private lateinit var prefAddNewRule: Preference

    private val globalAdapter = ConcatAdapter()
    private lateinit var rulesAdapter: RecordRulesAdapter

    private val requestRecordRuleSettings =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
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

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        // Not strictly necessary since all preferences here are non-persistent.
        preferenceManager.setStorageDeviceProtected()
        setPreferencesFromResource(R.xml.preferences_record_rules, rootKey)

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
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.reset, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem) = when (menuItem.itemId) {
                R.id.reset -> {
                    viewModel.reset()
                    true
                }
                else -> false
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
                    action = RecordRule.Action.Save(initialState = RecordRule.InitialState.RECORDING),
                )

                val context = requireContext()
                val intent = RecordRuleEditorActivity.createIntent(context, -1, newRule, false)
                requestRecordRuleSettings.launch(intent)

                return true
            }
        }

        return false
    }

    override fun onRulesChanged(rules: List<RecordRule>) {
        viewModel.replaceRules(rules)
    }

    override fun onRuleSelected(position: Int, rule: RecordRule, isDefault: Boolean) {
        val context = requireContext()
        val intent = RecordRuleEditorActivity.createIntent(context, position, rule, isDefault)
        requestRecordRuleSettings.launch(intent)
    }
}
