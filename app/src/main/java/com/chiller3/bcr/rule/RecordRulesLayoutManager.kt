/*
 * SPDX-FileCopyrightText: 2024 Andrew Gunnerson
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.chiller3.bcr.rule

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chiller3.bcr.R

internal class RecordRulesLayoutManager(
    context: Context,
    private val globalAdapter: ConcatAdapter,
) : LinearLayoutManager(context) {
    private val actionMoveUp = AccessibilityNodeInfoCompat.AccessibilityActionCompat(
        R.id.record_rule_drag_move_up,
        context.getString(R.string.record_rules_list_action_move_up),
    )
    private val actionMoveDown = AccessibilityNodeInfoCompat.AccessibilityActionCompat(
        R.id.record_rule_drag_move_down,
        context.getString(R.string.record_rules_list_action_move_down),
    )
    private val actionRemove = AccessibilityNodeInfoCompat.AccessibilityActionCompat(
        R.id.record_rule_swipe_remove,
        context.getString(R.string.record_rules_list_action_remove),
    )

    private fun getRecordRuleAdapterAndPosition(globalPosition: Int):
            Pair<RecordRulesAdapter, Int>? {
        val pair = globalAdapter.getWrappedAdapterAndPosition(globalPosition)
        val adapter = pair.first

        return if (adapter is RecordRulesAdapter) {
            adapter to pair.second
        } else {
            null
        }
    }

    override fun onInitializeAccessibilityNodeInfoForItem(
        recycler: RecyclerView.Recycler,
        state: RecyclerView.State,
        host: View,
        info: AccessibilityNodeInfoCompat,
    ) {
        super.onInitializeAccessibilityNodeInfoForItem(recycler, state, host, info)

        val (adapter, position) = getRecordRuleAdapterAndPosition(getPosition(host)) ?: return

        if (canMoveUp(adapter, position)) {
            info.addAction(actionMoveUp)
        }
        if (canMoveDown(adapter, position)) {
            info.addAction(actionMoveDown)
        }
        if (canRemove(adapter, position)) {
            info.addAction(actionRemove)
        }
    }

    override fun performAccessibilityActionForItem(
        recycler: RecyclerView.Recycler,
        state: RecyclerView.State,
        view: View,
        action: Int,
        args: Bundle?,
    ): Boolean {
        val adapterAndPosition = getRecordRuleAdapterAndPosition(getPosition(view))
        if (adapterAndPosition != null) {
            val (adapter, position) = adapterAndPosition

            when (action) {
                actionMoveUp.id -> if (canMoveUp(adapter, position)) {
                    adapter.onRuleMove(position, position - 1)
                    return true
                }
                actionMoveDown.id -> if (canMoveDown(adapter, position)) {
                    adapter.onRuleMove(position, position + 1)
                    return true
                }
                actionRemove.id -> if (canRemove(adapter, position)) {
                    adapter.onRuleRemove(position)
                    return true
                }
            }
        }

        return super.performAccessibilityActionForItem(recycler, state, view, action, args)
    }

    private fun canMoveUp(adapter: RecordRulesAdapter, position: Int) = position > 0
            && !adapter.isDefaultRule(position) && !adapter.isDefaultRule(position - 1)

    private fun canMoveDown(adapter: RecordRulesAdapter, position: Int) = position < itemCount - 1
            && !adapter.isDefaultRule(position) && !adapter.isDefaultRule(position + 1)

    private fun canRemove(adapter: RecordRulesAdapter, position: Int) =
        !adapter.isDefaultRule(position)
}
