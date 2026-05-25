/*
 * SPDX-FileCopyrightText: 2026 Andrew Gunnerson
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.chiller3.bcr.ui

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemDefaults.shapes
import androidx.compose.material3.ListItemShapes
import androidx.compose.runtime.Composable

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun betterSegmentedShapes(
    index: Int,
    count: Int,
    defaultShapes: ListItemShapes = shapes(),
): ListItemShapes {
    if (count == 1) {
        val top = ListItemDefaults.segmentedShapes(0, 2, defaultShapes)
        val bottom = ListItemDefaults.segmentedShapes(1, 2, defaultShapes)

        val topShape = top.shape
        val bottomShape = bottom.shape

        if (topShape is CornerBasedShape && bottomShape is CornerBasedShape) {
            return top.copy(
                shape = topShape.copy(
                    bottomStart = bottomShape.bottomStart,
                    bottomEnd = bottomShape.bottomEnd,
                )
            )
        }
    }

    return ListItemDefaults.segmentedShapes(index, count, defaultShapes)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
object BetterSegmentedShapes {
    @Composable
    fun top() = betterSegmentedShapes(0, 3)

    @Composable
    fun middle() = betterSegmentedShapes(1, 3)

    @Composable
    fun bottom() = betterSegmentedShapes(2, 3)

    @Composable
    fun single() = betterSegmentedShapes(0, 1)
}
