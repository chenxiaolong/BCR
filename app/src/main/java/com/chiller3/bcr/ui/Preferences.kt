/*
 * SPDX-FileCopyrightText: 2026 Andrew Gunnerson
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.chiller3.bcr.ui

import android.content.SharedPreferences
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.chiller3.bcr.Preferences
import com.chiller3.bcr.ui.theme.Icons

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
object PreferenceDefaults {
    val HorizontalPadding = 16.dp
    val SegmentedGap = ListItemDefaults.SegmentedGap

    // Equal to ListItemStartPadding and ListItemEndPadding.
    val CategoryHorizontalPadding = 16.dp
    val CategoryTopPadding = 24.dp
    val CategoryBottomPadding = 8.dp

    val MaxWidth = 720.dp

    val ListPadding = PaddingValues(horizontal = HorizontalPadding)

    val containerColor: Color
        @Composable get() = MaterialTheme.colorScheme.surfaceContainerHigh
    val scrolledContainerColor: Color
        @Composable get() = MaterialTheme.colorScheme.surfaceContainerLow

    @Composable
    fun appBarColors() = TopAppBarDefaults.topAppBarColors(
        containerColor = containerColor,
        scrolledContainerColor = scrolledContainerColor,
    )

    @Composable
    fun preferenceColors() = ListItemDefaults.segmentedColors(
        containerColor = MaterialTheme.colorScheme.surfaceBright,
        disabledContainerColor = MaterialTheme.colorScheme.surfaceBright,
    )

    @Composable
    fun preferenceInfoColors() = ListItemDefaults.segmentedColors(
        containerColor = containerColor,
        disabledContainerColor = containerColor,
    )

    @Composable
    fun switchColors() = SwitchDefaults.colors(
        checkedIconColor = SwitchDefaults.colors().checkedTrackColor,
    )
}

@Composable
fun PreferenceColumn(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(),
    fillScreen: Boolean = true,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(PreferenceDefaults.SegmentedGap),
    horizontalAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    flingBehavior: FlingBehavior = ScrollableDefaults.flingBehavior(),
    overscrollEffect: OverscrollEffect? = rememberOverscrollEffect(),
    content: LazyListScope.() -> Unit,
) {
    LazyColumn(
        modifier = if (fillScreen) {
            Modifier.fillMaxSize().then(modifier)
        } else {
            modifier
        },
        state = state,
        contentPadding = if (fillScreen) {
            contentPadding + PreferenceDefaults.ListPadding
        } else {
            contentPadding
        },
        verticalArrangement = verticalArrangement,
        horizontalAlignment = horizontalAlignment,
        flingBehavior = flingBehavior,
        overscrollEffect = overscrollEffect,
        content = content,
    )
}

@Composable
fun PreferenceCategory(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    segmentedGap: Dp = PreferenceDefaults.SegmentedGap,
) {
    Box(
        modifier = Modifier
            .widthIn(max = PreferenceDefaults.MaxWidth)
            .fillMaxWidth()
            .padding(
                start = PreferenceDefaults.CategoryHorizontalPadding,
                top = PreferenceDefaults.CategoryTopPadding - segmentedGap,
                end = PreferenceDefaults.CategoryHorizontalPadding,
                bottom = PreferenceDefaults.CategoryBottomPadding - segmentedGap,
            )
            .then(modifier),
        contentAlignment = Alignment.CenterStart,
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.primary) {
            ProvideTextStyle(value = MaterialTheme.typography.labelLarge) {
                // This is a separate box so that the focusable item is not full-width.
                Box(modifier = Modifier.semantics(mergeDescendants = true) { heading() }) {
                    title()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun Preference(
    onClick: () -> Unit,
    shapes: ListItemShapes,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    summary: @Composable (() -> Unit)? = null,
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    colors: ListItemColors = PreferenceDefaults.preferenceColors(),
    title: @Composable () -> Unit,
) {
    SegmentedListItem(
        onClick = onClick,
        shapes = shapes,
        modifier = Modifier.widthIn(max = PreferenceDefaults.MaxWidth).then(modifier),
        enabled = enabled,
        supportingContent = summary,
        verticalAlignment = verticalAlignment,
        onLongClick = onLongClick,
        onLongClickLabel = onLongClickLabel,
        colors = colors,
        content = title,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SwitchPreference(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    shapes: ListItemShapes,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    summary: @Composable (() -> Unit)? = null,
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    colors: ListItemColors = PreferenceDefaults.preferenceColors(),
    switchColors: SwitchColors = PreferenceDefaults.switchColors(),
    title: @Composable () -> Unit,
) {
    SegmentedListItem(
        onClick = { onCheckedChange(!checked) },
        shapes = shapes,
        modifier = Modifier
            .widthIn(max = PreferenceDefaults.MaxWidth)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .then(modifier),
        enabled = enabled,
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = null,
                enabled = enabled,
                thumbContent = {
                    Icon(
                        imageVector = if (checked) Icons.Check else Icons.Close,
                        contentDescription = null,
                        modifier = Modifier.size(SwitchDefaults.IconSize),
                    )
                },
                colors = switchColors,
            )
        },
        supportingContent = summary,
        verticalAlignment = verticalAlignment,
        onLongClick = onLongClick,
        onLongClickLabel = onLongClickLabel,
        colors = colors,
        content = title,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PreferenceGap(
    modifier: Modifier = Modifier,
    gap: Dp = PreferenceDefaults.HorizontalPadding,
    segmentedGap: Dp = PreferenceDefaults.SegmentedGap,
) {
    Spacer(modifier = Modifier.size(gap - segmentedGap * 2).then(modifier))
}

@Composable
fun PreferencesChangedEffect(
    key1: Any?,
    vararg keys: Any?,
    onPreferenceChanged: (String?) -> Unit,
) {
    val context = LocalContext.current

    DisposableEffect(key1, *keys) {
        val prefs = Preferences(context)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            onPreferenceChanged(key)
        }

        prefs.registerListener(listener)

        onDispose {
            prefs.unregisterListener(listener)
        }
    }
}
