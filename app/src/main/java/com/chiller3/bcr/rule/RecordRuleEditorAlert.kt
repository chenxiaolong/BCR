/*
 * SPDX-FileCopyrightText: 2026 Andrew Gunnerson
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.chiller3.bcr.rule

sealed interface RecordRuleEditorAlert {
    data object ContactPickerNotFound : RecordRuleEditorAlert
}
