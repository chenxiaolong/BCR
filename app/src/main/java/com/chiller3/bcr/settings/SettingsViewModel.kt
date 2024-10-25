/*
 * SPDX-FileCopyrightText: 2023-2024 Andrew Gunnerson
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.chiller3.bcr.settings

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chiller3.bcr.Logcat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel : ViewModel() {
    companion object {
        private val TAG = SettingsViewModel::class.java.simpleName
    }

    private val _alerts = MutableStateFlow<List<SettingsAlert>>(emptyList())
    val alerts = _alerts.asStateFlow()

    fun acknowledgeFirstAlert() {
        _alerts.update { it.drop(1) }
    }

    fun saveLogs(uri: Uri) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    Logcat.dump(uri)
                }
                _alerts.update { it + SettingsAlert.LogcatSucceeded(uri) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to dump logs to $uri", e)
                _alerts.update { it + SettingsAlert.LogcatFailed(uri, e.toString()) }
            }
        }
    }
}
