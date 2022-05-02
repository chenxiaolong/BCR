package com.chiller3.bcr

import android.content.Intent
import android.content.SharedPreferences
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.preference.PreferenceManager

class RecorderTileService : TileService(), SharedPreferences.OnSharedPreferenceChangeListener {
    override fun onStartListening() {
        super.onStartListening()
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        prefs.registerOnSharedPreferenceChangeListener(this)

        refreshTileState()
    }

    override fun onStopListening() {
        super.onStopListening()
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        prefs.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onClick() {
        super.onClick()

        if (!Permissions.haveRequired(this)) {
            val intent = Intent(this, SettingsActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivityAndCollapse(intent)
        } else {
            val isEnabled = Preferences.isCallRecordingEnabled(this)
            Preferences.setCallRecordingEnabled(this, !isEnabled)
        }

        refreshTileState()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        refreshTileState()
    }

    private fun refreshTileState() {
        val tile = qsTile

        // Tile.STATE_UNAVAILABLE is intentionally not used when permissions haven't been granted.
        // Clicking the tile in that state does not invoke the click handler, so it wouldn't be
        // possible to launch SettingsActivity to grant the permissions.
        if (Permissions.haveRequired(this) && Preferences.isCallRecordingEnabled(this)) {
            tile.state = Tile.STATE_ACTIVE
        } else {
            tile.state = Tile.STATE_INACTIVE
        }

        tile.updateTile()
    }
}