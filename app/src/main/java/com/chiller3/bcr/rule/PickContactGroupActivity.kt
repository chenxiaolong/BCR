/*
 * SPDX-FileCopyrightText: 2024-2026 Andrew Gunnerson
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.chiller3.bcr.rule

import com.chiller3.bcr.PreferenceBaseActivity
import com.chiller3.bcr.R

class PickContactGroupActivity : PreferenceBaseActivity() {
    override val actionBarTitle
        get() = getString(R.string.pick_contact_group_title)

    override val showUpButton = true

    override fun createFragment() = PickContactGroupFragment()

    companion object {
        const val RESULT_CONTACT_GROUP = "contact_group"
    }
}
