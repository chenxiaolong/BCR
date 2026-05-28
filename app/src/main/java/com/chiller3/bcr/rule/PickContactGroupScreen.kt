/*
 * SPDX-FileCopyrightText: 2024-2026 Andrew Gunnerson
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.chiller3.bcr.rule

import android.content.res.Configuration
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chiller3.bcr.ContactGroupInfo
import com.chiller3.bcr.R
import com.chiller3.bcr.ui.AppScreen
import com.chiller3.bcr.ui.Preference
import com.chiller3.bcr.ui.PreferenceColumn
import com.chiller3.bcr.ui.betterSegmentedShapes
import com.chiller3.bcr.ui.theme.AppTheme

@Composable
fun PickContactGroupScreen(
    onGroupSelect: (ContactGroupInfo) -> Unit,
    onBack: () -> Unit,
    viewModel: PickContactGroupViewModel = viewModel(),
) {
    val resources = LocalResources.current

    val groups by viewModel.groups.collectAsStateWithLifecycle()

    AppScreen(
        title = { Text(text = stringResource(R.string.pick_contact_group_title)) },
        onBack = onBack,
    ) { params ->
        LaunchedEffect(Unit) {
            viewModel.alerts.collect { alerts ->
                val alert = alerts.firstOrNull() ?: return@collect
                val msg = when (alert) {
                    is PickContactGroupAlert.QueryFailed ->
                        resources.getString(R.string.alert_contact_group_query_failure, alert.error)
                }

                params.snackbarHostState.showSnackbar(message = msg, withDismissAction = true)
                viewModel.acknowledgeFirstAlert()
            }
        }

        PickContactGroupContent(
            groups = groups,
            onGroupSelect = onGroupSelect,
            contentPadding = params.contentPadding,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PickContactGroupContent(
    groups: List<ContactGroupInfo>,
    onGroupSelect: (ContactGroupInfo) -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
) {
    PreferenceColumn(contentPadding = contentPadding) {
        itemsIndexed(items = groups) { index, group ->
            val summary = group.accountName
                ?: stringResource(R.string.pick_contact_group_local_group)

            Preference(
                onClick = { onGroupSelect(group) },
                shapes = betterSegmentedShapes(index = index, count = groups.size),
                title = { Text(text = group.title) },
                summary = { Text(text = summary) },
            )
        }
    }
}

@Preview(
    name = "Light Mode",
    showBackground = true,
)
@Preview(
    name = "Dark Mode",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    showBackground = true,
)
@Composable
private fun PreviewPickContactGroupScreen() {
    val groups = listOf(
        ContactGroupInfo(rowId = 0, sourceId = "group", title = "Family", accountName = null),
        ContactGroupInfo(rowId = 1, sourceId = null, title = "Other", accountName = "Synced"),
    )

    AppTheme {
        AppScreen(
            title = { Text(text = stringResource(R.string.pick_contact_group_title)) },
            onBack = {},
        ) { params ->
            PickContactGroupContent(
                groups = groups,
                onGroupSelect = {},
                contentPadding = params.contentPadding,
            )
        }
    }
}
