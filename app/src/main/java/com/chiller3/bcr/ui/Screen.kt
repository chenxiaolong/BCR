/*
 * SPDX-FileCopyrightText: 2026 Andrew Gunnerson
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.chiller3.bcr.ui

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.chiller3.bcr.R
import com.chiller3.bcr.ui.theme.Icons

data class AppScreenParams(
    val contentPadding: PaddingValues,
    val snackbarHostState: SnackbarHostState,
)

@Composable
fun AppScreen(
    title: @Composable () -> Unit,
    onBack: (() -> Unit)? = null,
    onReset: (() -> Unit)? = null,
    content: @Composable (AppScreenParams) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    val snackbarHostState = remember { SnackbarHostState() }
    var showMenu by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = title,
                navigationIcon = {
                    onBack?.let { onClick ->
                        IconButton(
                            onClick = onClick,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            ),
                        ) {
                            @SuppressLint("PrivateResource")
                            Icon(
                                imageVector = Icons.AutoMirrored.ArrowBack,
                                contentDescription = stringResource(
                                    androidx.appcompat.R.string.abc_action_bar_up_description,
                                ),
                            )
                        }
                    }
                },
                actions = {
                    onReset?.let { onClick ->
                        IconButton(onClick = { showMenu = true }) {
                            @SuppressLint("PrivateResource")
                            Icon(
                                imageVector = Icons.MoreVert,
                                contentDescription = stringResource(
                                    androidx.appcompat.R.string.abc_action_menu_overflow_description,
                                ),
                            )
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            properties = PopupProperties(
                                // This works around an upstream bug where the popup incorrectly
                                // respects the display cutout inset when the device is in landscape
                                // orientation and the cutout is on the screen side opposite the
                                // button.
                                clippingEnabled = false,
                                // From DefaultMenuProperties, which is not public. Without this,
                                // accessibility tools do not immediately focus the popup.
                                focusable = true,
                            ),
                        ) {
                            DropdownMenuItem(
                                text = { Text(text = stringResource(R.string.reset_to_defaults)) },
                                onClick = {
                                    onClick()
                                    showMenu = false
                                },
                            )
                        }
                    }
                },
                colors = PreferenceDefaults.appBarColors(),
                scrollBehavior = scrollBehavior,
            )
        },
        containerColor = PreferenceDefaults.containerColor,
    ) { contentPadding ->
        val outerPadding = contentPadding.copy(start = 0.dp, end = 0.dp, bottom = 0.dp)
        val innerPadding = contentPadding.copy(top = 0.dp)

        Box(modifier = Modifier.padding(outerPadding)) {
            content(AppScreenParams(
                contentPadding = innerPadding,
                snackbarHostState = snackbarHostState,
            ))
        }
    }
}
