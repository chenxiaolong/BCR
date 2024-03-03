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
