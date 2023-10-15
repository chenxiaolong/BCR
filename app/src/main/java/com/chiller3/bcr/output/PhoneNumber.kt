package com.chiller3.bcr.output

import android.content.Context
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import android.util.Log
import java.util.Locale

data class PhoneNumber(private val number: String) {
    fun format(context: Context, format: Format) = when (format) {
        Format.DIGITS_ONLY -> number.filter { Character.digit(it, 10) != -1 }
        Format.COUNTRY_SPECIFIC -> {
            val country = getIsoCountryCode(context)
            if (country == null) {
                Log.w(TAG, "Failed to detect country")
                null
            } else {
                val formatted = PhoneNumberUtils.formatNumber(number, country)
                if (formatted == null) {
                    Log.w(TAG, "Phone number cannot be formatted for country $country")
                    null
                } else {
                    formatted
                }
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
                return null
            }
            return result.uppercase()
        }
    }

    enum class Format {
        DIGITS_ONLY,
        COUNTRY_SPECIFIC,
    }
}