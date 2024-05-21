package com.chiller3.bcr

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import androidx.preference.PreferenceManager
import com.chiller3.bcr.settings.SettingsActivity

class RecorderTileService : TileService(), SharedPreferences.OnSharedPreferenceChangeListener {
    private lateinit var prefs: Preferences

    override fun onCreate() {
        super.onCreate()

        prefs = Preferences(this)
    }

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

    @SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        super.onClick()

        if (!Permissions.haveRequired(this)) {
            val intent = Intent(this, SettingsActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startActivityAndCollapse(PendingIntent.getActivity(
                    this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
        } else {
            prefs.isCallRecordingEnabled = !prefs.isCallRecordingEnabled
        }

        refreshTileState()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        refreshTileState()
    }

    private fun refreshTileState() {
        val tile = qsTile
        if (tile == null) {
            Log.w(TAG, "Tile was null during refreshTileState")
            return
        }

        // Tile.STATE_UNAVAILABLE is intentionally not used when permissions haven't been granted.
        // Clicking the tile in that state does not invoke the click handler, so it wouldn't be
        // possible to launch SettingsActivity to grant the permissions.
        if (Permissions.haveRequired(this) && prefs.isCallRecordingEnabled) {
            tile.state = Tile.STATE_ACTIVE
        } else {
            tile.state = Tile.STATE_INACTIVE
        }

        tile.updateTile()
    }

    companion object {
        private val TAG = RecorderTileService::class.java.simpleName
    }
}
