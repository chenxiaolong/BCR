/*
 * SPDX-FileCopyrightText: 2022-2024 Andrew Gunnerson
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.chiller3.bcr.settings

import androidx.fragment.app.Fragment
import com.chiller3.bcr.PreferenceBaseActivity
import com.chiller3.bcr.R

class SettingsActivity : PreferenceBaseActivity() {
    override val titleResId: Int = R.string.app_name_full

    override val showUpButton: Boolean = false

    override fun createFragment(): Fragment = SettingsFragment()
}
