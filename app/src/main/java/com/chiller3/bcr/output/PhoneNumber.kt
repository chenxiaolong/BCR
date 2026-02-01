/*
 * SPDX-FileCopyrightText: 2023-2026 Andrew Gunnerson
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.chiller3.bcr.output

import android.content.Context
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import android.util.Log
import java.util.Locale

data class PhoneNumber(private val number: String) {
    init {
        require(number.isNotEmpty()) { "Number cannot be empty" }
    }

    fun format(context: Context, format: Format) = when (format) {
        Format.E_164 -> {
            val country = getIsoCountryCode(context) ?: return null
            PhoneNumberUtils.formatNumberToE164(number, country)
        }
        Format.DIGITS_ONLY -> number.filter { Character.digit(it, 10) != -1 }
        Format.COUNTRY_SPECIFIC -> {
            val country = getIsoCountryCode(context) ?: return null
            val formatted = PhoneNumberUtils.formatNumber(number, country)
            if (formatted == null) {
                Log.w(TAG, "Phone number cannot be formatted for country $country")
                null
            } else {
                formatted
            }
        }
    }

    override fun toString(): String = number

    companion object {
        private val TAG = PhoneNumber::class.java.simpleName

        /**
         * Get the current ISO country code for phone number formatting.
         */
        private fun getIsoCountryCode(context: Context): String? {
            val telephonyManager = context.getSystemService(TelephonyManager::class.java)
            var result: String? = null

            if (telephonyManager.phoneType == TelephonyManager.PHONE_TYPE_GSM) {
                result = telephonyManager.networkCountryIso
            }
            if (result.isNullOrEmpty()) {
                result = telephonyManager.simCountryIso
            }
            if (result.isNullOrEmpty()) {
                result = Locale.getDefault().country
            }
            if (result.isNullOrEmpty()) {
                Log.w(TAG, "Failed to detect country")
                return null
            }
            return result.uppercase()
        }
    }

    enum class Format {
        E_164,
        DIGITS_ONLY,
        COUNTRY_SPECIFIC,
    }
}
