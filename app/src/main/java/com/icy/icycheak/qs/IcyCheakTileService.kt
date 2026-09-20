package com.icy.icycheak.qs

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.lifecycle.asFlow
import com.icy.icycheak.MainActivity
import com.icy.icycheak.data.settings.AppSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Quick Settings tile. Single high-value action: toggle Live Graphs on/off and
 * reflect the state on the tile. Long-press opens the app.
 */
class IcyCheakTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        unlockAndRun {
            val on = runBlocking { AppSettings.liveGraphsEnabled.first() }
            runBlocking { AppSettings.setLiveGraphs(!on) }
            updateTile()
        }
    }

    private fun updateTile() {
        val on = runBlocking { AppSettings.liveGraphsEnabled.first() }
        qsTile?.let { tile ->
            tile.label = if (on) "Live Graphs: On" else "Live Graphs: Off"
            tile.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            tile.updateTile()
        }
    }

    override fun onTileAdded() {
        super.onTileAdded()
        updateTile()
    }
}
