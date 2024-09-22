package com.chiller3.bcr.rule

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.content.IntentCompat
import com.chiller3.bcr.ContactGroupInfo

/** Launch our own picker for contact groups. There is no standard Android component for this. */
class PickContactGroup : ActivityResultContract<Void?, ContactGroupInfo?>() {
    override fun createIntent(context: Context, input: Void?): Intent {
        return Intent(context, PickContactGroupActivity::class.java)
    }

    override fun parseResult(resultCode: Int, intent: Intent?): ContactGroupInfo? {
        return intent.takeIf { resultCode == Activity.RESULT_OK }?.let {
            IntentCompat.getParcelableExtra(
                it,
                PickContactGroupActivity.RESULT_CONTACT_GROUP,
                ContactGroupInfo::class.java,
            )
        }
    }
}
