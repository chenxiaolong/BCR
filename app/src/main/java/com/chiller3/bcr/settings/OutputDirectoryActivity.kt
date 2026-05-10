/*
 * SPDX-FileCopyrightText: 2026 Andrew Gunnerson
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.chiller3.bcr.settings

import com.chiller3.bcr.PreferenceBaseActivity
import com.chiller3.bcr.R

class OutputDirectoryActivity : PreferenceBaseActivity() {
    override val actionBarTitle
        get() = getString(R.string.pref_output_dir_name)

    override val showUpButton = true

    override fun createFragment() = OutputDirectoryFragment()
}
