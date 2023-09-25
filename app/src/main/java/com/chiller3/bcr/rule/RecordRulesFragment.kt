package com.chiller3.bcr.rule

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.MenuProvider
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.get
import androidx.preference.size
import com.chiller3.bcr.Preferences
import com.chiller3.bcr.R
import com.chiller3.bcr.view.LongClickableSwitchPreference
import com.chiller3.bcr.view.OnPreferenceLongClickListener
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import kotlin.properties.Delegates

class RecordRulesFragment : PreferenceFragmentCompat(), Preference.OnPreferenceClickListener,
    Preference.OnPreferenceChangeListener, OnPreferenceLongClickListener {
    private val viewModel: RecordRulesViewModel by viewModels()

    private lateinit var categoryRules: PreferenceCategory
    private lateinit var prefAddRule: Preference

    private var ruleOffset by Delegates.notNull<Int>()
    private var rules = emptyList<DisplayedRecordRule>()

    private val requestContact =
        registerForActivityResult(ActivityResultContracts.PickContact()) { uri ->
            // We don't bother using persisted URI permissions for the contact because we need the
            // full READ_CONTACTS permission for this feature to work at all (lookups by number).
            uri?.let { viewModel.addContactRule(it) }
        }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.record_rules_preferences, rootKey)

        categoryRules = findPreference(Preferences.CATEGORY_RULES)!!

        ruleOffset = categoryRules.preferenceCount

        prefAddRule = findPreference(Preferences.PREF_ADD_RULE)!!
        prefAddRule.onPreferenceClickListener = this

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.messages.collect {
                    it.firstOrNull()?.let { message ->
                        when (message) {
                            Message.RuleAdded -> {
                                showSnackBar(getString(R.string.record_rules_rule_added)) {
                                    viewModel.acknowledgeFirstMessage()
                                }
                            }
                            Message.RuleExists -> {
                                showSnackBar(getString(R.string.record_rules_rule_exists)) {
                                    viewModel.acknowledgeFirstMessage()
                                }
                            }
                        }
                    }
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.rules.collect {
                    updateRules(it)
                }
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

    private fun updateRules(newRules: List<DisplayedRecordRule>) {
        // The list is going to be short enough that it doesn't make sense to use DiffUtil and
        // deal with PreferenceGroup's awkward indexing/ordering mechanism. Just replace all the
        // preferences every time.

        val context = requireContext()
        val contactsGranted = context.checkSelfPermission(Manifest.permission.READ_CONTACTS) ==
                PackageManager.PERMISSION_GRANTED

        prefAddRule.isEnabled = contactsGranted

        for (i in (ruleOffset until categoryRules.size).reversed()) {
            val p = categoryRules[i]
            categoryRules.removePreference(p)
        }

        for ((i, rule) in newRules.withIndex()) {
            val p = LongClickableSwitchPreference(context).apply {
                key = Preferences.PREF_RULE_PREFIX + i
                isPersistent = false
                when (rule) {
                    is DisplayedRecordRule.AllCalls -> {
                        if (contactsGranted) {
                            title = getString(R.string.record_rule_type_all_other_calls_name)
                            summary = getString(R.string.record_rule_type_all_other_calls_desc)
                        } else {
                            title = getString(R.string.record_rule_type_all_calls_name)
                            summary = getString(R.string.record_rule_type_all_calls_desc)
                        }
                        isEnabled = true
                    }
                    is DisplayedRecordRule.UnknownCalls -> {
                        title = getString(R.string.record_rule_type_unknown_calls_name)
                        summary = getString(R.string.record_rule_type_unknown_calls_desc)
                        isEnabled = contactsGranted
                    }
                    is DisplayedRecordRule.Contact -> {
                        title = getString(
                            R.string.record_rule_type_contact_name,
                            rule.displayName ?: rule.lookupKey,
                        )
                        summary = getString(R.string.record_rule_type_contact_desc)
                        isEnabled = contactsGranted
                        onPreferenceLongClickListener = this@RecordRulesFragment
                    }
                }
                isIconSpaceReserved = false
                isChecked = rule.record
                onPreferenceChangeListener = this@RecordRulesFragment
            }
            categoryRules.addPreference(p)
        }

        rules = newRules
    }

    override fun onPreferenceClick(preference: Preference): Boolean {
        when (preference) {
            prefAddRule -> {
                requestContact.launch(null)
                return true
            }
        }

        return false
    }

    override fun onPreferenceChange(preference: Preference, newValue: Any?): Boolean {
        when {
            preference.key.startsWith(Preferences.PREF_RULE_PREFIX) -> {
                val index = preference.key.substring(Preferences.PREF_RULE_PREFIX.length).toInt()
                viewModel.setRuleRecord(index, newValue as Boolean)
                return true
            }
        }

        return false
    }

    override fun onPreferenceLongClick(preference: Preference): Boolean {
        when {
            preference.key.startsWith(Preferences.PREF_RULE_PREFIX) -> {
                val index = preference.key.substring(Preferences.PREF_RULE_PREFIX.length).toInt()
                viewModel.deleteRule(index)
                return true
            }
        }

        return false
    }

    private fun showSnackBar(text: CharSequence, onDismiss: () -> Unit) {
        Snackbar.make(requireView(), text, Snackbar.LENGTH_LONG)
            .addCallback(object : Snackbar.Callback() {
                override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                    onDismiss()
                }
            })
            .show()
    }
}
