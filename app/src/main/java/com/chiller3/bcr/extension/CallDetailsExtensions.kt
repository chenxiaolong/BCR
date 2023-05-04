package com.chiller3.bcr.extension

import android.telecom.Call

val Call.Details.phoneNumber: String?
    get() = handle?.phoneNumber
