/*
 * SPDX-FileCopyrightText: 2024-2026 Andrew Gunnerson
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.chiller3.bcr.rule

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.chiller3.bcr.ui.theme.AppTheme

class PickContactGroupActivity : ComponentActivity() {
    companion object {
        const val RESULT_CONTACT_GROUP = "contact_group"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            AppTheme {
                PickContactGroupScreen(
                    onGroupSelect = { group ->
                        setResult(RESULT_OK, Intent().apply {
                            putExtra(RESULT_CONTACT_GROUP, group)
                        })
                        finish()
                    },
                    onBack = ::finish,
                )
            }
        }
    }
}
