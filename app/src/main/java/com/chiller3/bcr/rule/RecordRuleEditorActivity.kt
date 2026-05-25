/*
 * SPDX-FileCopyrightText: 2026 Andrew Gunnerson
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.chiller3.bcr.rule

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.IntentCompat
import com.chiller3.bcr.ui.theme.AppTheme

class RecordRuleEditorActivity : ComponentActivity() {
    companion object {
        private const val ARG_POSITION = "position"
        private const val ARG_RECORD_RULE = "record_rule"
        private const val ARG_IS_DEFAULT = "is_default"

        const val RESULT_POSITION = ARG_POSITION
        const val RESULT_RECORD_RULE = ARG_RECORD_RULE

        fun createIntent(context: Context, position: Int, rule: RecordRule, isDefault: Boolean) =
            Intent(context, RecordRuleEditorActivity::class.java).apply {
                putExtra(ARG_POSITION, position)
                putExtra(ARG_RECORD_RULE, rule)
                putExtra(ARG_IS_DEFAULT, isDefault)
            }
    }

    private val position: Int by lazy { intent.getIntExtra(ARG_POSITION, -1) }

    private fun setResult(rule: RecordRule) {
        setResult(RESULT_OK, Intent().apply {
            putExtra(RESULT_POSITION, position)
            putExtra(RESULT_RECORD_RULE, rule)
        })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val initialRule = IntentCompat.getParcelableExtra(
            intent,
            ARG_RECORD_RULE,
            RecordRule::class.java,
        )!!
        setResult(initialRule)

        val isDefault = intent.getBooleanExtra(ARG_IS_DEFAULT, false)

        setContent {
            AppTheme {
                RecordRuleEditorScreen(
                    position = position,
                    initialRule = initialRule,
                    isDefault = isDefault,
                    onRuleUpdated = ::setResult,
                    onBack = ::finish,
                )
            }
        }
    }
}
