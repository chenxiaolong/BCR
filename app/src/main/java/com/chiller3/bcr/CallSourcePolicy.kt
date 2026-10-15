/* SPDX-License-Identifier: GPL-3.0-only */
package com.chiller3.bcr

/** Filters before opening the audio source so another recorder can own cellular calls. */
internal object CallSourcePolicy {
    fun shouldRecord(packageName: String?, cellularCalls: Boolean, telecomApps: Boolean): Boolean =
        when (packageName) {
            null, "" -> false
            "com.android.phone" -> cellularCalls
            else -> telecomApps
        }
}
