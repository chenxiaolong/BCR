/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.chiller3.bcr.rule

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.telecom.ConnectionService
import com.chiller3.bcr.R
import com.chiller3.bcr.RecorderInCallService

private fun sourceAppLabel(context: Context, packageName: String): String =
    if (packageName == RecorderInCallService.PHONE_PACKAGE) {
        context.getString(R.string.record_rule_source_cellular_item)
    } else {
        try {
            val info = context.packageManager.getApplicationInfo(packageName, 0)
            info.loadLabel(context.packageManager).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            packageName
        }
    }

fun sourceAppSummary(context: Context, source: RecordRule.SourceApp): String = when (source) {
    RecordRule.SourceApp.Any -> context.getString(R.string.record_rule_source_any_item)
    is RecordRule.SourceApp.Specific -> sourceAppLabel(context, source.packageName)
}

fun sourceAppChoices(context: Context, current: RecordRule.SourceApp): List<Pair<String, String>> {
    val packages = context.packageManager.queryIntentServices(
        Intent(ConnectionService.SERVICE_INTERFACE), 0,
    ).map { it.serviceInfo.packageName }.toMutableSet()
    packages += RecorderInCallService.PHONE_PACKAGE
    // Keep an existing selection editable even if its app was removed or disabled.
    if (current is RecordRule.SourceApp.Specific) {
        packages += current.packageName
    }
    return packages.map { it to sourceAppLabel(context, it) }
        .sortedWith(compareBy({ it.second.lowercase() }, { it.first }))
}
