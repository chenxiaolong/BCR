package com.chiller3.bcr.extension

import android.telecom.Call
import com.chiller3.bcr.phoneNumber

val Call.Details.phoneNumber: String?
    get() = handle?.phoneNumber
