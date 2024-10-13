/*
 * SPDX-FileCopyrightText: 2023-2024 Andrew Gunnerson
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.chiller3.bcr.extension

import android.telecom.Call
import com.chiller3.bcr.output.PhoneNumber

val Call.Details.phoneNumber: PhoneNumber?
    get() = handle?.phoneNumber?.let {
        try {
            PhoneNumber(it)
        } catch (e: IllegalArgumentException) {
            null
        }
    }
