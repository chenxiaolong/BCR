/*
 * SPDX-FileCopyrightText: 2022-2026 Andrew Gunnerson
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.chiller3.bcr.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.net.toUri
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chiller3.bcr.BuildConfig
import com.chiller3.bcr.DirectBootMigrationService
import com.chiller3.bcr.Logcat
import com.chiller3.bcr.Permissions
import com.chiller3.bcr.Preferences
import com.chiller3.bcr.R
import com.chiller3.bcr.extension.DOCUMENTSUI_AUTHORITY
import com.chiller3.bcr.extension.formattedString
import com.chiller3.bcr.format.AudioSource
import com.chiller3.bcr.format.Format
import com.chiller3.bcr.format.NoParamInfo
import com.chiller3.bcr.format.RangedParamInfo
import com.chiller3.bcr.output.DaysRetention
import com.chiller3.bcr.output.Retention
import com.chiller3.bcr.rule.RecordRulesActivity
import com.chiller3.bcr.ui.AppScreen
import com.chiller3.bcr.ui.BetterSegmentedShapes
import com.chiller3.bcr.ui.Preference
import com.chiller3.bcr.ui.PreferenceCategory
import com.chiller3.bcr.ui.PreferenceColumn
import com.chiller3.bcr.ui.PreferenceGap
import com.chiller3.bcr.ui.PreferencesChangedEffect
import com.chiller3.bcr.ui.SwitchPreference
import com.chiller3.bcr.ui.theme.AppTheme

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = viewModel()) {
    val context = LocalContext.current
    val resources = LocalResources.current

    val prefs = remember { Preferences(context) }
    var reloadPrefs by remember { mutableIntStateOf(0) }
    val callRecording = remember(reloadPrefs) {
        prefs.isCallRecordingEnabled && Permissions.haveRequired(context)
    }
    val outputDir = remember(reloadPrefs) { prefs.outputDirOrDefault }
    val retention = remember(reloadPrefs) { Retention.fromPreferences(prefs) }
    val savedFormat = remember(reloadPrefs) { Format.fromPreferences(prefs) }
    val minDuration = remember(reloadPrefs) { prefs.minDuration }
    val writeMetadata = remember(reloadPrefs) { prefs.writeMetadata }
    val recordTelecomApps = remember(reloadPrefs) { prefs.recordTelecomApps }
    val recordDialingState = remember(reloadPrefs) { prefs.recordDialingState }
    val notificationOpenDir = remember(reloadPrefs) { prefs.notificationOpenDir }
    val showLauncherIcon = remember(reloadPrefs) { prefs.showLauncherIcon }
    val isDebugMode = remember(reloadPrefs) { prefs.isDebugMode }
    val forceDirectBoot = remember(reloadPrefs) { prefs.forceDirectBoot }

    val requestPermissionRequired = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        // Call recording can still be enabled if optional permissions were not granted.
        if (granted.all { it.key !in Permissions.REQUIRED || it.value }) {
            prefs.isCallRecordingEnabled = true
            reloadPrefs++
        } else {
            context.startActivity(Permissions.getAppInfoIntent(context))
        }
    }
    val requestSettings = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        reloadPrefs++
    }
    val requestSafSaveLogs = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(Logcat.MIMETYPE),
    ) { uri ->
        uri?.let { viewModel.saveLogs(it) }
    }

    AppScreen(
        title = { Text(text = stringResource(R.string.app_name_full)) },
    ) { params ->
        LaunchedEffect(Unit) {
            viewModel.alerts.collect { alerts ->
                val alert = alerts.firstOrNull() ?: return@collect
                val msg = when (alert) {
                    is SettingsAlert.LogcatSucceeded -> resources.getString(
                        R.string.alert_logcat_success,
                        alert.uri.formattedString,
                    )
                    is SettingsAlert.LogcatFailed -> resources.getString(
                        R.string.alert_logcat_failure,
                        alert.uri.formattedString,
                        alert.error,
                    )
                    SettingsAlert.DocumentsUINotFound -> resources.getString(
                        R.string.documentsui_not_found,
                    )
                }

                params.snackbarHostState.showSnackbar(message = msg, withDismissAction = true)
                viewModel.acknowledgeFirstAlert()
            }
        }

        SettingsContent(
            callRecording = callRecording,
            outputDir = outputDir,
            retention = retention,
            savedFormat = savedFormat,
            minDuration = minDuration,
            writeMetadata = writeMetadata,
            recordTelecomApps = recordTelecomApps,
            recordDialingState = recordDialingState,
            notificationOpenDir = notificationOpenDir,
            showLauncherIcon = showLauncherIcon,
            isDebugMode = isDebugMode,
            forceDirectBoot = forceDirectBoot,
            onCallRecordingChange = { enabled ->
                if (!enabled || Permissions.haveRequired(context)) {
                    prefs.isCallRecordingEnabled = enabled
                    reloadPrefs++
                } else {
                    // Ask for optional permissions the first time only.
                    requestPermissionRequired.launch(Permissions.REQUIRED + Permissions.OPTIONAL)
                }
            },
            onRecordRulesSettings = {
                context.startActivity(Intent(context, RecordRulesActivity::class.java))
            },
            onOutputDirSettings = {
                requestSettings.launch(Intent(context, OutputDirectoryActivity::class.java))
            },
            onOutputDirOpen = {
                try {
                    context.startActivity(prefs.outputDirOrDefaultIntent)
                } catch (_: ActivityNotFoundException) {
                    viewModel.addAlert(SettingsAlert.DocumentsUINotFound)
                }
            },
            onOutputFormatSettings = {
                requestSettings.launch(Intent(context, OutputFormatActivity::class.java))
            },
            onMinDurationChange = { duration ->
                prefs.minDuration = duration
                reloadPrefs++
            },
            onWriteMetadataChange = { enabled ->
                prefs.writeMetadata = enabled
                reloadPrefs++
            },
            onRecordTelecomAppsChange = { enabled ->
                prefs.recordTelecomApps = enabled
                reloadPrefs++
            },
            onRecordDialingStateChange = { enabled ->
                prefs.recordDialingState = enabled
                reloadPrefs++
            },
            onNotificationOpenDirChange = { enabled ->
                prefs.notificationOpenDir = enabled
                reloadPrefs++
            },
            onShowLauncherIconChange = { enabled ->
                prefs.showLauncherIcon = enabled
                reloadPrefs++
            },
            onDebugModeChange = { enabled ->
                prefs.isDebugMode = enabled
                reloadPrefs++
            },
            onSourceRepoOpen = {
                val uri = BuildConfig.PROJECT_URL_AT_COMMIT.toUri()
                context.startActivity(Intent(Intent.ACTION_VIEW, uri))
            },
            onForceDirectBootChange = { enabled ->
                prefs.forceDirectBoot = enabled
                reloadPrefs++
            },
            onMigrateDirectBoot = {
                context.startService(Intent(context, DirectBootMigrationService::class.java))
            },
            onSaveLogs = {
                requestSafSaveLogs.launch(Logcat.FILENAME_DEFAULT)
            },
            contentPadding = params.contentPadding,
        )
    }

    PreferencesChangedEffect(LocalLifecycleOwner.current) { key ->
        // This can be changed by the quick settings tile.
        if (key == Preferences.PREF_CALL_RECORDING) {
            reloadPrefs++
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SettingsContent(
    callRecording: Boolean,
    outputDir: Uri,
    retention: Retention,
    savedFormat: Format.Companion.SavedFormat,
    minDuration: Int,
    writeMetadata: Boolean,
    recordTelecomApps: Boolean,
    recordDialingState: Boolean,
    notificationOpenDir: Boolean,
    showLauncherIcon: Boolean,
    isDebugMode: Boolean,
    forceDirectBoot: Boolean,
    onCallRecordingChange: (Boolean) -> Unit,
    onRecordRulesSettings: () -> Unit,
    onOutputDirSettings: () -> Unit,
    onOutputDirOpen: () -> Unit,
    onOutputFormatSettings: () -> Unit,
    onMinDurationChange: (Int) -> Unit,
    onWriteMetadataChange: (Boolean) -> Unit,
    onRecordTelecomAppsChange: (Boolean) -> Unit,
    onRecordDialingStateChange: (Boolean) -> Unit,
    onNotificationOpenDirChange: (Boolean) -> Unit,
    onShowLauncherIconChange: (Boolean) -> Unit,
    onDebugModeChange: (Boolean) -> Unit,
    onSourceRepoOpen: () -> Unit,
    onForceDirectBootChange: (Boolean) -> Unit,
    onMigrateDirectBoot: () -> Unit,
    onSaveLogs: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
) {
    var showMinDurationDialog by rememberSaveable { mutableStateOf(false) }

    PreferenceColumn(contentPadding = contentPadding) {
        item(key = "general") {
            PreferenceCategory(
                title = { Text(text = stringResource(R.string.pref_header_general)) },
                modifier = Modifier.animateItem(),
            )
        }

        item(key = "call_recording") {
            SwitchPreference(
                checked = callRecording,
                onCheckedChange = onCallRecordingChange,
                shapes = BetterSegmentedShapes.top(),
                title = { Text(text = stringResource(R.string.pref_call_recording_name)) },
                summary = { Text(text = stringResource(R.string.pref_call_recording_desc)) },
                modifier = Modifier.animateItem(),
            )
        }

        item(key = "record_rules") {
            Preference(
                onClick = onRecordRulesSettings,
                shapes = BetterSegmentedShapes.middle(),
                title = { Text(text = stringResource(R.string.pref_record_rules_name)) },
                summary = { Text(text = stringResource(R.string.pref_record_rules_desc)) },
                modifier = Modifier.animateItem(),
            )
        }

        item(key = "output_dir") {
            Preference(
                onClick = onOutputDirSettings,
                onLongClick = onOutputDirOpen,
                shapes = BetterSegmentedShapes.middle(),
                title = { Text(text = stringResource(R.string.pref_output_dir_name)) },
                summary = { Text(text = outputDirSummary(outputDir, retention)) },
                modifier = Modifier.animateItem(),
            )
        }

        item(key = "output_format") {
            Preference(
                onClick = onOutputFormatSettings,
                shapes = BetterSegmentedShapes.bottom(),
                title = { Text(text = stringResource(R.string.pref_output_format_name)) },
                summary = { Text(text = outputFormatSummary(savedFormat)) },
                modifier = Modifier.animateItem(),
            )
        }

        item(key = "advanced") {
            PreferenceGap(modifier = Modifier.animateItem())
        }

        item(key = "min_duration") {
            Preference(
                onClick = { showMinDurationDialog = true },
                shapes = BetterSegmentedShapes.top(),
                title = { Text(text = stringResource(R.string.pref_min_duration_name)) },
                summary = { Text(text = minDurationSummary(minDuration)) },
                modifier = Modifier.animateItem(),
            )
        }

        item(key = "write_metadata") {
            SwitchPreference(
                checked = writeMetadata,
                onCheckedChange = onWriteMetadataChange,
                shapes = BetterSegmentedShapes.middle(),
                title = { Text(text = stringResource(R.string.pref_write_metadata_name)) },
                summary = { Text(text = stringResource(R.string.pref_write_metadata_desc)) },
                modifier = Modifier.animateItem(),
            )
        }

        item(key = "record_telecom_apps") {
            SwitchPreference(
                checked = recordTelecomApps,
                onCheckedChange = onRecordTelecomAppsChange,
                shapes = BetterSegmentedShapes.middle(),
                title = { Text(text = stringResource(R.string.pref_record_telecom_apps_name)) },
                summary = { Text(text = stringResource(R.string.pref_record_telecom_apps_desc)) },
                modifier = Modifier.animateItem(),
            )
        }

        item(key = "record_dialing_state") {
            SwitchPreference(
                checked = recordDialingState,
                onCheckedChange = onRecordDialingStateChange,
                shapes = BetterSegmentedShapes.middle(),
                title = { Text(text = stringResource(R.string.pref_record_dialing_state_name)) },
                summary = { Text(text = stringResource(R.string.pref_record_dialing_state_desc)) },
                modifier = Modifier.animateItem(),
            )
        }

        item(key = "notification_open_dir") {
            SwitchPreference(
                checked = notificationOpenDir,
                onCheckedChange = onNotificationOpenDirChange,
                shapes = BetterSegmentedShapes.middle(),
                title = { Text(text = stringResource(R.string.pref_notification_open_dir_name)) },
                summary = { Text(text = stringResource(R.string.pref_notification_open_dir_desc)) },
                modifier = Modifier.animateItem(),
            )
        }

        item(key = "show_launcher_icon") {
            SwitchPreference(
                checked = showLauncherIcon,
                onCheckedChange = onShowLauncherIconChange,
                shapes = BetterSegmentedShapes.bottom(),
                title = { Text(text = stringResource(R.string.pref_show_launcher_icon_name)) },
                summary = { Text(text = stringResource(R.string.pref_show_launcher_icon_desc)) },
                modifier = Modifier.animateItem(),
            )
        }

        item(key = "about") {
            PreferenceCategory(
                title = { Text(text = stringResource(R.string.pref_header_about)) },
                modifier = Modifier.animateItem(),
            )
        }

        item(key = "version") {
            Preference(
                onClick = onSourceRepoOpen,
                onLongClick = { onDebugModeChange(!isDebugMode) },
                shapes = BetterSegmentedShapes.single(),
                title = { Text(text = stringResource(R.string.pref_version_name)) },
                summary = { Text(text = versionSummary(isDebugMode)) },
                modifier = Modifier.animateItem(),
            )
        }

        if (isDebugMode) {
            item(key = "debug") {
                PreferenceCategory(
                    title = { Text(text = stringResource(R.string.pref_header_debug)) },
                    modifier = Modifier.animateItem(),
                )
            }

            item(key = "force_direct_boot") {
                SwitchPreference(
                    checked = forceDirectBoot,
                    onCheckedChange = onForceDirectBootChange,
                    shapes = BetterSegmentedShapes.top(),
                    title = { Text(text = stringResource(R.string.pref_force_direct_boot_name)) },
                    summary = { Text(text = stringResource(R.string.pref_force_direct_boot_desc)) },
                    modifier = Modifier.animateItem(),
                )
            }

            item(key = "migrate_direct_boot") {
                Preference(
                    onClick = onMigrateDirectBoot,
                    shapes = BetterSegmentedShapes.middle(),
                    title = { Text(text = stringResource(R.string.pref_migrate_direct_boot_name)) },
                    summary = { Text(text = stringResource(R.string.pref_migrate_direct_boot_desc)) },
                    modifier = Modifier.animateItem(),
                )
            }

            item(key = "save_logs") {
                Preference(
                    onClick = onSaveLogs,
                    shapes = BetterSegmentedShapes.bottom(),
                    title = { Text(text = stringResource(R.string.pref_save_logs_name)) },
                    summary = { Text(text = stringResource(R.string.pref_save_logs_desc)) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }

    if (showMinDurationDialog) {
        MinDurationDialog(
            minDuration = minDuration,
            onSelected = { duration ->
                onMinDurationChange(duration)
                @Suppress("AssignedValueIsNeverRead")
                showMinDurationDialog = false
            },
            onDismissed = {
                @Suppress("AssignedValueIsNeverRead")
                showMinDurationDialog = false
            },
        )
    }
}

@Composable
private fun outputDirSummary(outputDir: Uri, retention: Retention) = buildString {
    append(stringResource(R.string.pref_output_dir_desc))
    append("\n\n")
    append(outputDir.formattedString)
    append(stringResource(R.string.summary_separator))
    append(retention.toFormattedString(LocalResources.current))
}

@Composable
private fun outputFormatSummary(savedFormat: Format.Companion.SavedFormat) = buildString {
    val resources = LocalResources.current

    val formatParam = savedFormat.param ?: savedFormat.format.paramInfo.default
    val sampleRate = savedFormat.sampleRate ?: savedFormat.format.sampleRateInfo.default

    val separator = stringResource(R.string.summary_separator)

    append(stringResource(R.string.pref_output_format_desc))
    append("\n\n")
    append(savedFormat.format.name)
    append(separator)

    when (val info = savedFormat.format.paramInfo) {
        is RangedParamInfo -> {
            append(info.format(resources, formatParam))
            append(separator)
        }
        NoParamInfo -> {}
    }

    append(savedFormat.format.sampleRateInfo.format(resources, sampleRate))
    append(separator)

    append(stringResource(savedFormat.audioSource.nameResId))
}

@Composable
private fun minDurationSummary(minDuration: Int) = if (minDuration == 0) {
    stringResource(R.string.pref_min_duration_desc_zero)
} else {
    LocalResources.current.getQuantityString(
        R.plurals.pref_min_duration_desc,
        minDuration,
        minDuration,
    )
}

@Composable
private fun versionSummary(isDebugMode: Boolean): String {
    val suffix = if (isDebugMode) "+debugmode" else ""

    return "${BuildConfig.VERSION_NAME} (${BuildConfig.BUILD_TYPE}${suffix})"
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
private fun PreviewSettingsScreen() {
    val uri = DocumentsContract.buildTreeDocumentUri(DOCUMENTSUI_AUTHORITY, "primary:Recordings")
    val format = Format.all.first()
    val savedFormat = Format.Companion.SavedFormat(
        format = format,
        param = format.paramInfo.default,
        sampleRate = format.sampleRateInfo.default,
        audioSource = AudioSource.VOICE_UPLINK_DOWNLINK,
    )

    AppTheme {
        AppScreen(
            title = { Text(text = stringResource(R.string.app_name_full)) },
        ) { params ->
            SettingsContent(
                callRecording = true,
                outputDir = uri,
                retention = DaysRetention(365U),
                savedFormat = savedFormat,
                minDuration = 5,
                writeMetadata = true,
                recordTelecomApps = false,
                recordDialingState = false,
                notificationOpenDir = false,
                showLauncherIcon = true,
                isDebugMode = true,
                forceDirectBoot = false,
                onCallRecordingChange = {},
                onRecordRulesSettings = {},
                onOutputDirSettings = {},
                onOutputDirOpen = {},
                onOutputFormatSettings = {},
                onMinDurationChange = {},
                onWriteMetadataChange = {},
                onRecordTelecomAppsChange = {},
                onRecordDialingStateChange = {},
                onNotificationOpenDirChange = {},
                onShowLauncherIconChange = {},
                onDebugModeChange = {},
                onSourceRepoOpen = {},
                onForceDirectBootChange = {},
                onMigrateDirectBoot = {},
                onSaveLogs = {},
                contentPadding = params.contentPadding,
            )
        }
    }
}
