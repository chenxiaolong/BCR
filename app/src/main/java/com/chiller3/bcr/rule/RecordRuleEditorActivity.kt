/*
 * SPDX-FileCopyrightText: 2026 Andrew Gunnerson
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.chiller3.bcr.rule

import android.content.Context
import android.content.Intent
import com.chiller3.bcr.PreferenceBaseActivity
import com.chiller3.bcr.R

class RecordRuleEditorActivity : PreferenceBaseActivity() {
    companion object {
        const val RESULT_POSITION = RecordRuleEditorFragment.RESULT_POSITION
        const val RESULT_RECORD_RULE = RecordRuleEditorFragment.RESULT_RECORD_RULE

        fun createIntent(context: Context, position: Int, rule: RecordRule, isDefault: Boolean) =
            Intent(context, RecordRuleEditorActivity::class.java).apply {
                putExtra(RecordRuleEditorFragment.ARG_POSITION, position)
                putExtra(RecordRuleEditorFragment.ARG_RECORD_RULE, rule)
                putExtra(RecordRuleEditorFragment.ARG_IS_DEFAULT, isDefault)
            }
    }

    private val position: Int by lazy {
        intent.getIntExtra(RecordRuleEditorFragment.ARG_POSITION, -1)
    }
    private val isDefault: Boolean by lazy {
        intent.getBooleanExtra(RecordRuleEditorFragment.ARG_IS_DEFAULT, false)
    }

    override val actionBarTitle
        get() = if (position < 0) {
            getString(R.string.pref_add_new_rule_name)
        } else if (isDefault) {
            getString(R.string.record_rules_list_default_name)
        } else {
            getString(R.string.record_rules_list_custom_name, position + 1)
        }

    override val showUpButton = true

    override fun createFragment() = RecordRuleEditorFragment().apply {
        arguments = intent.extras
    }
}
