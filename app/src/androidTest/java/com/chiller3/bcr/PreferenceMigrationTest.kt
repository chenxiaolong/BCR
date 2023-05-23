package com.chiller3.bcr

import androidx.core.content.edit
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chiller3.bcr.rule.RecordRule

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*

@RunWith(AndroidJUnit4::class)
class PreferenceMigrationTest {
    private fun withNoRecordRules(block: (prefs: Preferences) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = Preferences(context)
        val oldRecordRules = prefs.recordRules

        try {
            prefs.recordRules = null
            block(prefs)
        } finally {
            prefs.recordRules = oldRecordRules
        }
    }

    @Test
    fun migrateInitiallyPausedUnset() {
        withNoRecordRules { prefs ->
            prefs.prefs.edit {
                remove(Preferences.PREF_INITIALLY_PAUSED)
            }

            prefs.migrateInitiallyPaused()

            assertEquals(null, prefs.recordRules)
        }
    }

    @Test
    fun migrateInitiallyPausedOn() {
        withNoRecordRules { prefs ->
            prefs.prefs.edit {
                putBoolean(Preferences.PREF_INITIALLY_PAUSED, true)
            }

            prefs.migrateInitiallyPaused()

            assertEquals(
                listOf(
                    RecordRule.UnknownCalls(false),
                    RecordRule.AllCalls(false),
                ),
                prefs.recordRules,
            )
        }
    }

    @Test
    fun migrateInitiallyPausedOff() {
        withNoRecordRules { prefs ->
            prefs.prefs.edit {
                putBoolean(Preferences.PREF_INITIALLY_PAUSED, false)
            }

            prefs.migrateInitiallyPaused()

            assertEquals(
                listOf(
                    RecordRule.UnknownCalls(true),
                    RecordRule.AllCalls(true),
                ),
                prefs.recordRules,
            )
        }
    }
}
