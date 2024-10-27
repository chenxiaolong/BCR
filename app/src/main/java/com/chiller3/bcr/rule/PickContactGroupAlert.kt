/*
 * SPDX-FileCopyrightText: 2024 Andrew Gunnerson
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.chiller3.bcr.rule

sealed interface PickContactGroupAlert {
    data class QueryFailed(val error: String) : PickContactGroupAlert
}
