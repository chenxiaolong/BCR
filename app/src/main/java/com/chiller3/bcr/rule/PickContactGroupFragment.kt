/*
 * SPDX-FileCopyrightText: 2024 Andrew Gunnerson
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.chiller3.bcr.rule

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.Preference
import androidx.preference.get
import androidx.preference.size
import com.chiller3.bcr.ContactGroupInfo
import com.chiller3.bcr.PreferenceBaseFragment
import com.chiller3.bcr.R
import kotlinx.coroutines.launch

class PickContactGroupFragment : PreferenceBaseFragment(), Preference.OnPreferenceClickListener {
    private val viewModel: PickContactGroupViewModel by viewModels()

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.record_rules_preferences, rootKey)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.groups.collect {
                    updateGroups(it)
                }
            }
        }
    }

    private fun updateGroups(newGroups: List<ContactGroupInfo>) {
        val context = requireContext()

        for (i in (0 until preferenceScreen.size).reversed()) {
            val p = preferenceScreen[i]
            preferenceScreen.removePreference(p)
        }

        for ((i, group) in newGroups.withIndex()) {
            val p = Preference(context).apply {
                key = PREF_GROUP_PREFIX + i
                isPersistent = false
                title = group.title
                isIconSpaceReserved = false
                onPreferenceClickListener = this@PickContactGroupFragment
            }
            preferenceScreen.addPreference(p)
        }
    }

    override fun onPreferenceClick(preference: Preference): Boolean {
        when {
            preference.key.startsWith(PREF_GROUP_PREFIX) -> {
                val index = preference.key.substring(PREF_GROUP_PREFIX.length).toInt()
                val activity = requireActivity()

                activity.setResult(Activity.RESULT_OK, Intent().putExtra(
                    PickContactGroupActivity.RESULT_CONTACT_GROUP,
                    viewModel.groups.value[index],
                ))
                activity.finish()

                return true
            }
        }

        return false
    }

    companion object {
        private const val PREF_GROUP_PREFIX = "group_"
    }
}
