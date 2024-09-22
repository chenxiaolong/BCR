package com.chiller3.bcr.rule

import androidx.fragment.app.Fragment
import com.chiller3.bcr.PreferenceBaseActivity
import com.chiller3.bcr.R

class PickContactGroupActivity : PreferenceBaseActivity() {
    override val titleResId: Int = R.string.pick_contact_group_title

    override val showUpButton: Boolean = true

    override fun createFragment(): Fragment = PickContactGroupFragment()

    companion object {
        const val RESULT_CONTACT_GROUP = "contact_group"
    }
}
