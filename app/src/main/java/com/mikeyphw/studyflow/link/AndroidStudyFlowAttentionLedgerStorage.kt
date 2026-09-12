package com.mikeyphw.studyflow.link

import android.content.Context
import androidx.core.content.edit
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionLedgerStorage

private const val AttentionLedgerPreferences = "studyflow_attention_ledger_v1"
private const val AttentionLedgerSnapshotKey = "snapshot"

class AndroidStudyFlowAttentionLedgerStorage(context: Context) : StudyFlowAttentionLedgerStorage {
    private val preferences = context.applicationContext.getSharedPreferences(
        AttentionLedgerPreferences,
        Context.MODE_PRIVATE,
    )

    override fun readEncodedSnapshot(): String? = synchronized(this) {
        preferences.getString(AttentionLedgerSnapshotKey, null)
    }

    override fun writeEncodedSnapshot(encoded: String) = synchronized(this) {
        preferences.edit(commit = true) {
            putString(AttentionLedgerSnapshotKey, encoded)
        }
    }
}
