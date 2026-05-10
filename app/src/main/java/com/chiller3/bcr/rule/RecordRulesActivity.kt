/*
 * SPDX-FileCopyrightText: 2023-2026 Andrew Gunnerson
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.chiller3.bcr.rule

import com.chiller3.bcr.PreferenceBaseActivity
import com.chiller3.bcr.R

class RecordRulesActivity : PreferenceBaseActivity() {
    override val actionBarTitle
        get() = getString(R.string.pref_record_rules_name)

    override val showUpButton = true

    override fun createFragment() = RecordRulesFragment()
}
