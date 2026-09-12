package com.mikeyphw.studyflow.tv.link

import android.content.Context
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionLedgerStorage

private const val TvAttentionLedgerPreferences = "studyflow_tv_attention_ledger_v1"
private const val TvAttentionLedgerSnapshotKey = "snapshot"

class StudyFlowTvAttentionLedgerStorage(context: Context) : StudyFlowAttentionLedgerStorage {
    private val preferences = context.applicationContext.getSharedPreferences(
        TvAttentionLedgerPreferences,
        Context.MODE_PRIVATE,
    )

    override fun readEncodedSnapshot(): String? = synchronized(this) {
        preferences.getString(TvAttentionLedgerSnapshotKey, null)
    }

    override fun writeEncodedSnapshot(encoded: String) {
        synchronized(this) {
            preferences.edit().putString(TvAttentionLedgerSnapshotKey, encoded).commit()
        }
    }
}
