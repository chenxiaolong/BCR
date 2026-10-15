package com.chiller3.bcr

import org.junit.Assert.assertEquals
import org.junit.Test

class CallSourcePolicyTest {
    @Test fun independentRecordingSwitches() {
        for (cellular in listOf(false, true)) {
            for (apps in listOf(false, true)) {
                assertEquals(cellular, CallSourcePolicy.shouldRecord("com.android.phone", cellular, apps))
                assertEquals(apps, CallSourcePolicy.shouldRecord("com.google.android.apps.tachyon", cellular, apps))
                assertEquals(apps, CallSourcePolicy.shouldRecord("example.other.telecom", cellular, apps))
                assertEquals(false, CallSourcePolicy.shouldRecord(null, cellular, apps))
                assertEquals(false, CallSourcePolicy.shouldRecord("", cellular, apps))
            }
        }
    }
}
