package com.mikeyphw.studyflow.reminders

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.mikeyphw.studyflow.MainActivity

/** ATT08 live Quick Settings compact companion; tiles exist only on Android 7.0+. */
@RequiresApi(Build.VERSION_CODES.N)
class StudyFlowAttentionTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        val state = AndroidCompactCompanionStateStore(this).snapshot()
        qsTile?.apply {
            label = state.title ?: "StudyFlow"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) subtitle = state.actionLabel ?: "Open"
            this.state = if (state.occurrenceId == null) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            // API 24-33 requires Intent; the overload is unsupported on Android 14+.
            startActivityAndCollapseLegacy(intent)
        }
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    @Suppress("DEPRECATION")
    private fun startActivityAndCollapseLegacy(intent: Intent) {
        startActivityAndCollapse(intent)
    }

    companion object {
        fun requestRefresh(context: Context) {
            // Also guard callers: API 23 has no Quick Settings TileService.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
            requestListeningState(context, ComponentName(context, StudyFlowAttentionTileService::class.java))
        }
    }
}
