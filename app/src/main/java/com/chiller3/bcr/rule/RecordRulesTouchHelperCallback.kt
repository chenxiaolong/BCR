/*
 * SPDX-FileCopyrightText: 2024 Andrew Gunnerson
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.chiller3.bcr.rule

import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.chiller3.bcr.rule.RecordRulesAdapter.CustomViewHolder

internal class RecordRulesTouchHelperCallback(private val adapter: RecordRulesAdapter) :
    ItemTouchHelper.SimpleCallback(
        ItemTouchHelper.UP or ItemTouchHelper.DOWN,
        ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT,
    ) {
    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder,
    ): Boolean {
        adapter.onRuleMove(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
        return true
    }

    override fun onSwiped(
        viewHolder: RecyclerView.ViewHolder,
        direction: Int,
    ) = adapter.onRuleRemove(viewHolder.bindingAdapterPosition)

    override fun canDropOver(
        recyclerView: RecyclerView,
        current: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder,
    ): Boolean {
        // Don't allow moving over something that is not ours.
        if (target !is CustomViewHolder) {
            return false
        }

        // Prevent moving an item into where the default rule lives.
        return !adapter.isDefaultRule(target.bindingAdapterPosition)
    }

    override fun getMovementFlags(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
    ): Int {
        // Don't allow moving something that is not ours.
        if (viewHolder !is CustomViewHolder) {
            return makeMovementFlags(0, 0)
        }

        // Prevent moving or dismissing the default rule.
        if (adapter.isDefaultRule(viewHolder.bindingAdapterPosition)) {
            return makeMovementFlags(0, 0)
        }

        return super.getMovementFlags(recyclerView, viewHolder)
    }
}
