package com.mikeyphw.studyflow.reminders

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.mikeyphw.studyflow.MainActivity

/** ATT08 live Quick Settings compact companion. */
class StudyFlowAttentionTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        val state = AndroidCompactCompanionStateStore(this).snapshot()
        qsTile?.apply {
            label = state.title ?: "StudyFlow"
            if (android.os.Build.VERSION.SDK_INT >= 29) subtitle = state.actionLabel ?: "Open"
            this.state = if (state.occurrenceId == null) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
            updateTile()
        }
    }

    @Suppress("DEPRECATION")
    override fun onClick() {
        super.onClick()
        startActivityAndCollapse(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    companion object {
        fun requestRefresh(context: Context) {
            requestListeningState(context, ComponentName(context, StudyFlowAttentionTileService::class.java))
        }
    }
}
