/*
 * SPDX-FileCopyrightText: 2022-2026 Andrew Gunnerson
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.chiller3.bcr.settings

import com.chiller3.bcr.PreferenceBaseActivity
import com.chiller3.bcr.R

class SettingsActivity : PreferenceBaseActivity() {
    override val actionBarTitle
        get() = getString(R.string.app_name_full)

    override val showUpButton = false

    override fun createFragment() = SettingsFragment()
}
