package com.chiller3.bcr

import android.content.Context
import android.util.AttributeSet
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder

/**
 * A thin shell over [Preference] that allows registering a long click listener.
 */
class LongClickablePreference(context: Context, attrs: AttributeSet?) : Preference(context, attrs) {
    var onPreferenceLongClickListener: OnPreferenceLongClickListener? = null

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        holder.itemView.setOnLongClickListener {
            onPreferenceLongClickListener?.onPreferenceLongClick(this) ?: true
        }
    }

    interface OnPreferenceLongClickListener {
        fun onPreferenceLongClick(preference: Preference): Boolean
    }
}
