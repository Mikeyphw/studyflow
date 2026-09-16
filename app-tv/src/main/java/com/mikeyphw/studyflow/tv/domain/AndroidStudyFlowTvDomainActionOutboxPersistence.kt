package com.mikeyphw.studyflow.tv.domain

import android.content.Context
import android.content.SharedPreferences
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvDomainAction
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvDomainActionResult
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Android-backed OV18 durability for canonical TV mutation requests. */
class AndroidStudyFlowTvDomainActionOutboxPersistence(context: Context) : StudyFlowTvDomainActionOutboxPersistence {
    private val preferences: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFERENCES,
        Context.MODE_PRIVATE,
    )

    override fun load(): StudyFlowTvDomainActionOutboxSnapshot? {
        val encoded = preferences.getString(KEY_SNAPSHOT, null) ?: return null
        return runCatching {
            val wire = Json.decodeFromString<OutboxSnapshotWire>(encoded)
            StudyFlowTvDomainActionOutboxSnapshot(
                pending = wire.pending.distinctBy { it.actionId }.take(MAX_RESTORED_PENDING),
                lastResult = wire.lastResult,
            )
        }.getOrElse {
            preferences.edit()
                .putString("$KEY_CORRUPT_PREFIX${System.currentTimeMillis()}", encoded)
                .remove(KEY_SNAPSHOT)
                .apply()
            null
        }
    }

    override fun save(snapshot: StudyFlowTvDomainActionOutboxSnapshot): Boolean {
        val wire = OutboxSnapshotWire(
            pending = snapshot.pending.distinctBy { it.actionId }.takeLast(MAX_RESTORED_PENDING),
            lastResult = snapshot.lastResult,
        )
        return preferences.edit()
            .putString(KEY_SNAPSHOT, Json.encodeToString(wire))
            .commit()
    }

    @Serializable
    private data class OutboxSnapshotWire(
        val pending: List<StudyFlowTvDomainAction>,
        val lastResult: StudyFlowTvDomainActionResult?,
    )

    private companion object {
        const val PREFERENCES = "studyflow-tv-domain-action-outbox-v1"
        const val KEY_SNAPSHOT = "outbox-snapshot"
        const val KEY_CORRUPT_PREFIX = "corrupt-outbox-snapshot:"
        const val MAX_RESTORED_PENDING = 32
    }
}
