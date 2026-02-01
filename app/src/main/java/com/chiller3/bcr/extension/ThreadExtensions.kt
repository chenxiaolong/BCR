/*
 * SPDX-FileCopyrightText: 2026 Andrew Gunnerson
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.chiller3.bcr.extension

import android.os.Build

val Thread.threadIdCompat: Long
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
        this.threadId()
    } else {
        @Suppress("DEPRECATION")
        this.id
    }
