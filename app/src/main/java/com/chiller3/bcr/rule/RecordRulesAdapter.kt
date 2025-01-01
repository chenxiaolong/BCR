/*
 * SPDX-FileCopyrightText: 2024 Andrew Gunnerson
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.chiller3.bcr.rule

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.res.TypedArrayUtils
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.chiller3.bcr.ContactGroupInfo
import com.chiller3.bcr.ContactInfo
import com.chiller3.bcr.R
import com.chiller3.bcr.rule.RecordRulesAdapter.CustomViewHolder
import kotlin.math.absoluteValue

internal class RecordRulesAdapter(
    private val context: Context,
    private val listener: Listener,
) : RecyclerView.Adapter<CustomViewHolder?>() {
    interface Listener {
        fun onRulesChanged(rules: List<RecordRule>)

        fun onRuleSelected(position: Int, rule: RecordRule, isDefault: Boolean)
    }

    internal inner class CustomViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val iconFrame = view.findViewById<View>(androidx.preference.R.id.icon_frame)
            ?: view.findViewById<View>(android.R.id.icon_frame)
        private val titleView = view.findViewById<TextView>(android.R.id.title)
        private val summaryView = view.findViewById<TextView>(android.R.id.summary)

        var title: CharSequence
            get() = titleView.text
            set(text) {
                titleView.text = text
            }

        var summary: CharSequence
            get() = summaryView.text
            set(text) {
                summaryView.text = text
            }

        init {
            iconFrame?.isVisible = false

            // We currently don't exceed the default 4 line limit, but we potentially could in the
            // future if more conditions are implemented.
            summaryView.maxLines = Integer.MAX_VALUE

            view.setOnClickListener {
                val position = bindingAdapterPosition
                listener.onRuleSelected(position, rules[position], isDefaultRule(position))
            }
        }
    }

    private var rules: ArrayList<RecordRule> = arrayListOf()
        set(newRules) {
            val diff = DiffUtil.calculateDiff(
                object : DiffUtil.Callback() {
                    override fun getOldListSize(): Int = field.size

                    override fun getNewListSize(): Int = newRules.size

                    // The index identifies the rule. If the position of a rule changes, it needs to
                    // be rendered again for its new title.
                    override fun areItemsTheSame(
                        oldItemPosition: Int,
                        newItemPosition: Int,
                    ): Boolean = oldItemPosition == newItemPosition

                    override fun areContentsTheSame(
                        oldItemPosition: Int,
                        newItemPosition: Int,
                    ): Boolean = field[oldItemPosition] == newRules[newItemPosition]
                },
                true,
            )

            field = newRules

            diff.dispatchUpdatesTo(this)
        }
    private var contacts: Map<String, ContactInfo?> = emptyMap()
    private var groups: Map<Long, ContactGroupInfo?> = emptyMap()

    @SuppressLint("PrivateResource", "RestrictedApi")
    private val preferenceLayout = run {
        val defStyleAttr = TypedArrayUtils.getAttr(
            context,
            androidx.preference.R.attr.preferenceStyle,
            android.R.attr.preferenceStyle,
        )
        val attrs = context.obtainStyledAttributes(
            null,
            androidx.preference.R.styleable.Preference,
            defStyleAttr,
            0,
        )
        try {
            TypedArrayUtils.getResourceId(
                attrs,
                androidx.preference.R.styleable.Preference_layout,
                androidx.preference.R.styleable.Preference_android_layout,
                androidx.preference.R.layout.preference,
            )
        } finally {
            attrs.recycle()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CustomViewHolder {
        val item = LayoutInflater.from(context).inflate(preferenceLayout, parent, false)
        return CustomViewHolder(item)
    }

    override fun onBindViewHolder(holder: CustomViewHolder, position: Int) {
        val rule = rules[position]

        holder.title = if (isDefaultRule(position)) {
            context.getString(R.string.record_rules_list_default_name)
        } else {
            context.getString(R.string.record_rules_list_custom_name, position + 1)
        }

        holder.summary = buildString {
            when (rule.callNumber) {
                RecordRule.CallNumber.Any -> {}
                is RecordRule.CallNumber.Contact -> {
                    val contact = contacts[rule.callNumber.lookupKey]

                    append(context.getString(
                        R.string.record_rule_number_contact_summary,
                        contact?.displayName ?: rule.callNumber.lookupKey,
                    ))
                    append('\n')
                }
                is RecordRule.CallNumber.ContactGroup -> {
                    val group = groups[rule.callNumber.rowId]

                    val msg = if (group?.title != null) {
                        context.getString(
                            R.string.record_rule_number_contact_group_with_account_summary,
                            group.title,
                            group.accountName
                                ?: context.getString(R.string.pick_contact_group_local_group),
                        )
                    } else {
                        context.getString(
                            R.string.record_rule_number_contact_group_unknown_summary,
                            rule.callNumber.sourceId ?: rule.callNumber.rowId.toString(),
                        )
                    }
                    append(msg)
                    append('\n')
                }
                RecordRule.CallNumber.Unknown -> {
                    append(context.getString(R.string.record_rule_number_unknown_summary))
                    append('\n')
                }
            }

            val typeMsgId = when (rule.callType) {
                RecordRule.CallType.ANY -> 0
                RecordRule.CallType.INCOMING -> R.string.record_rule_type_incoming_summary
                RecordRule.CallType.OUTGOING -> R.string.record_rule_type_outgoing_summary
                RecordRule.CallType.CONFERENCE -> R.string.record_rule_type_conference_summary
            }
            if (typeMsgId != 0) {
                append(context.getString(typeMsgId))
                append('\n')
            }

            val simSlotMsg = when (rule.simSlot) {
                RecordRule.SimSlot.Any -> null
                is RecordRule.SimSlot.Specific ->
                    context.getString(R.string.record_rule_sim_specific_summary, rule.simSlot.slot)
            }
            simSlotMsg?.let {
                append(it)
                append('\n')
            }

            val actionMsgId = when (rule.action) {
                RecordRule.Action.SAVE -> R.string.record_rule_action_save_summary
                RecordRule.Action.DISCARD -> R.string.record_rule_action_discard_summary
                RecordRule.Action.IGNORE -> R.string.record_rule_action_ignore_summary
            }
            append(context.getString(actionMsgId))
        }
    }

    override fun getItemCount(): Int = rules.size

    fun setDisplayableRules(displayableRules: DisplayableRules) {
        rules = ArrayList(displayableRules.rules)
        contacts = displayableRules.contacts
        groups = displayableRules.groups
    }

    internal fun isDefaultRule(position: Int) = position == rules.size - 1

    internal fun onRuleMove(fromPosition: Int, toPosition: Int) {
        if ((fromPosition - toPosition).absoluteValue == 1) {
            val rule = rules[fromPosition]
            rules[fromPosition] = rules[toPosition]
            rules[toPosition] = rule
        } else if (fromPosition != toPosition) {
            val rule = rules.removeAt(fromPosition)
            rules.add(toPosition, rule)
        }

        notifyItemChanged(fromPosition)
        if (fromPosition != toPosition) {
            notifyItemChanged(toPosition)
        }
        // This needs to be called, even when both positions are the same. Otherwise, dragging a
        // rule slowly over another rule cancels the current drag motion and starts a new one.
        notifyItemMoved(fromPosition, toPosition)

        if (fromPosition != toPosition) {
            listener.onRulesChanged(rules)
        }
    }

    internal fun onRuleRemove(position: Int) {
        rules.removeAt(position)

        notifyItemRemoved(position)

        // The titles for everything afterwards needs to be renumbered.
        notifyItemRangeChanged(position, itemCount - position)

        listener.onRulesChanged(rules)
    }
}
