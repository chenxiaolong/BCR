/*
 * SPDX-FileCopyrightText: 2023-2024 Andrew Gunnerson
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.chiller3.bcr.settings

import android.net.Uri

sealed interface SettingsAlert {
    data class LogcatSucceeded(val uri: Uri) : SettingsAlert

    data class LogcatFailed(val uri: Uri, val error: String) : SettingsAlert
}
