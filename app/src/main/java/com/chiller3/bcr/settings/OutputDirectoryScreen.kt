/*
 * SPDX-FileCopyrightText: 2022-2026 Andrew Gunnerson
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.chiller3.bcr.settings

import android.content.res.Configuration
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.chiller3.bcr.Preferences
import com.chiller3.bcr.R
import com.chiller3.bcr.extension.DOCUMENTSUI_AUTHORITY
import com.chiller3.bcr.extension.formattedString
import com.chiller3.bcr.output.DaysRetention
import com.chiller3.bcr.output.OutputFilenameGenerator
import com.chiller3.bcr.output.Retention
import com.chiller3.bcr.template.Template
import com.chiller3.bcr.template.templateSyntaxColors
import com.chiller3.bcr.ui.AppScreen
import com.chiller3.bcr.ui.BetterSegmentedShapes
import com.chiller3.bcr.ui.Preference
import com.chiller3.bcr.ui.PreferenceColumn
import com.chiller3.bcr.ui.theme.AppTheme

@Composable
fun OutputDirectoryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { Preferences(context) }

    // The output directory is locked to internal device-protected storage while in direct boot, and
    // Preferences.outputDir throws if it's changed in that state. Disable the picker/reset so the
    // user can't trigger that crash (e.g. with the "force direct boot" debug option enabled).
    val isDirectBoot = remember { prefs.isDirectBoot }

    var reloadPrefs by remember { mutableIntStateOf(0) }
    var outputDir by remember(reloadPrefs) { mutableStateOf(prefs.outputDirOrDefault) }
    var template by remember(reloadPrefs) {
        mutableStateOf(prefs.filenameTemplate ?: Preferences.DEFAULT_FILENAME_TEMPLATE)
    }
    var retention by remember(reloadPrefs) { mutableStateOf(Retention.fromPreferences(prefs)) }

    val requestSafOutputDir = rememberLauncherForActivityResult(OpenPersistentDocumentTree()) { uri ->
        uri?.let {
            prefs.outputDir = it
            outputDir = it
        }
    }

    AppScreen(
        title = { Text(text = stringResource(R.string.pref_output_dir_name)) },
        onBack = onBack,
        onReset = {
            if (!isDirectBoot) {
                prefs.outputDir = null
            }
            prefs.filenameTemplate = null
            prefs.outputRetention = null
            reloadPrefs++
        },
    ) { params ->
        OutputDirectoryContent(
            outputDir = outputDir,
            template = template,
            retention = retention,
            outputDirEnabled = !isDirectBoot,
            onOutputDirClick = {
                requestSafOutputDir.launch(null)
            },
            onOutputDirReset = {
                if (!isDirectBoot) {
                    prefs.outputDir = null
                    reloadPrefs++
                }
            },
            onTemplateClick = { newTemplate ->
                prefs.filenameTemplate = newTemplate
                reloadPrefs++
            },
            onTemplateReset = {
                prefs.filenameTemplate = null
                reloadPrefs++
            },
            onRetentionClick = { newRetention ->
                prefs.outputRetention = newRetention
                reloadPrefs++
            },
            onRetentionReset = {
                prefs.outputRetention = null
                reloadPrefs++
            },
            contentPadding = params.contentPadding,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun OutputDirectoryContent(
    outputDir: Uri,
    template: Template,
    retention: Retention,
    outputDirEnabled: Boolean,
    onOutputDirClick: () -> Unit,
    onOutputDirReset: () -> Unit,
    onTemplateClick: (Template) -> Unit,
    onTemplateReset: () -> Unit,
    onRetentionClick: (Retention) -> Unit,
    onRetentionReset: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val outputDirFormatted = remember(outputDir) { outputDir.formattedString }
    val syntaxColors = templateSyntaxColors()
    val templateFormatted = remember(syntaxColors, template) {
        syntaxColors.annotated(template.toString())
    }
    val (retentionSupported, retentionSummary) = buildRetentionSummary(template, retention)

    var showTemplateDialog by rememberSaveable { mutableStateOf(false) }
    var showRetentionDialog by rememberSaveable { mutableStateOf(false) }

    PreferenceColumn(contentPadding = contentPadding) {
        item(key = "output_dir") {
            Preference(
                onClick = onOutputDirClick,
                onLongClick = onOutputDirReset,
                enabled = outputDirEnabled,
                shapes = BetterSegmentedShapes.top(),
                title = { Text(text = stringResource(R.string.pref_output_dir_name)) },
                summary = { Text(text = outputDirFormatted) },
            )
        }

        item(key = "filename_template") {
            Preference(
                onClick = { showTemplateDialog = true },
                onLongClick = onTemplateReset,
                shapes = BetterSegmentedShapes.middle(),
                title = { Text(text = stringResource(R.string.pref_filename_template_name)) },
                summary = { Text(text = templateFormatted) },
            )
        }

        item(key = "output_retention") {
            Preference(
                onClick = { showRetentionDialog = true },
                onLongClick = onRetentionReset,
                enabled = retentionSupported,
                shapes = BetterSegmentedShapes.bottom(),
                title = { Text(text = stringResource(R.string.pref_file_retention_name)) },
                summary = { Text(text = retentionSummary) },
            )
        }
    }

    if (showTemplateDialog) {
        FilenameTemplateDialog(
            template = template,
            onSelect = { newTemplate ->
                onTemplateClick(newTemplate)
                showTemplateDialog = false
            },
            onDismiss = {
                showTemplateDialog = false
            },
            onReset = {
                onTemplateReset()
                showTemplateDialog = false
            },
        )
    }

    if (showRetentionDialog) {
        FileRetentionDialog(
            retention = retention,
            onSelect = { newRetention ->
                onRetentionClick(newRetention)
                showRetentionDialog = false
            },
            onDismiss = {
                showRetentionDialog = false
            }
        )
    }
}

@Composable
private fun buildRetentionSummary(template: Template, retention: Retention): Pair<Boolean, String> {
    val locations = template.findVariableRef(OutputFilenameGenerator.DATE_VAR, true)
    val retentionUsable = locations != null &&
            locations.second != setOf(Template.VariableRefLocation.Arbitrary)

    val summary = if (retentionUsable) {
        retention.toFormattedString(LocalResources.current)
    } else {
        stringResource(R.string.retention_unusable)
    }

    return retentionUsable to summary
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
private fun PreviewOutputDirectoryScreen() {
    val uri = DocumentsContract.buildTreeDocumentUri(DOCUMENTSUI_AUTHORITY, "primary:Recordings")

    AppTheme {
        AppScreen(
            title = { Text(text = stringResource(R.string.pref_output_dir_name)) },
            onBack = {},
            onReset = {},
        ) { params ->
            OutputDirectoryContent(
                outputDir = uri,
                template = Preferences.DEFAULT_FILENAME_TEMPLATE,
                retention = DaysRetention(365U),
                outputDirEnabled = true,
                onOutputDirClick = {},
                onOutputDirReset = {},
                onTemplateClick = {},
                onTemplateReset = {},
                onRetentionClick = {},
                onRetentionReset = {},
                contentPadding = params.contentPadding,
            )
        }
    }
}
