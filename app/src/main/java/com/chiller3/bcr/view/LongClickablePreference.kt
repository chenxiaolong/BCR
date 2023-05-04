package com.chiller3.bcr.view

import android.content.Context
import android.util.AttributeSet
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import androidx.preference.SwitchPreferenceCompat

/**
 * A thin shell over [Preference] that allows registering a long click listener.
 */
class LongClickablePreference : Preference {
    var onPreferenceLongClickListener: OnPreferenceLongClickListener? = null

    @Suppress("unused")
    constructor(context: Context) : super(context)

    @Suppress("unused")
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    @Suppress("unused")
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) :
            super(context, attrs, defStyleAttr)

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        val listener = onPreferenceLongClickListener
        if (listener == null) {
            holder.itemView.setOnLongClickListener(null)
            holder.itemView.isLongClickable = false
        } else {
            holder.itemView.setOnLongClickListener {
                listener.onPreferenceLongClick(this)
            }
        }
    }
}

class LongClickableSwitchPreference : SwitchPreferenceCompat {
    var onPreferenceLongClickListener: OnPreferenceLongClickListener? = null

    @Suppress("unused")
    constructor(context: Context) : super(context)

    @Suppress("unused")
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    @Suppress("unused")
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) :
            super(context, attrs, defStyleAttr)
    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        val listener = onPreferenceLongClickListener
        if (listener == null) {
            holder.itemView.setOnLongClickListener(null)
            holder.itemView.isLongClickable = false
        } else {
            holder.itemView.setOnLongClickListener {
                listener.onPreferenceLongClick(this)
            }
        }
    }
}

interface OnPreferenceLongClickListener {
    fun onPreferenceLongClick(preference: Preference): Boolean
}
