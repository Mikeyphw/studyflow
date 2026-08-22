package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.StudySyncEntityVersion
import com.mikeyphw.studyflow.core.model.StudySyncMergeAction
import com.mikeyphw.studyflow.core.model.StudySyncMergeConflict
import com.mikeyphw.studyflow.core.model.StudySyncMergeConflictReason
import com.mikeyphw.studyflow.core.model.StudySyncMergeDecision
import com.mikeyphw.studyflow.core.model.StudySyncMergePlan

object StudySyncMergeEngine {
    fun decide(
        local: StudySyncEntityVersion?,
        remote: StudySyncEntityVersion?,
        base: StudySyncEntityVersion? = null,
    ): StudySyncMergeDecision {
        val representative = local ?: remote ?: base
        require(representative != null) { "At least one entity version is required." }
        requireKeysMatch(local, remote, base)

        val entityType = representative.entityType
        val entityId = representative.entityId

        if (local != null && remote == null) {
            return decision(entityType, entityId, StudySyncMergeAction.KEEP_LOCAL, local, null, base)
        }
        if (local == null && remote != null) {
            return decision(entityType, entityId, StudySyncMergeAction.IMPORT_REMOTE, null, remote, base)
        }
        if (local == null && remote == null && base != null) {
            return decision(entityType, entityId, StudySyncMergeAction.NO_CHANGE, null, null, base)
        }
        check(local != null && remote != null)

        if (local.sameContentAs(remote)) {
            return decision(entityType, entityId, StudySyncMergeAction.NO_CHANGE, local, remote, base)
        }
        if (base == null) {
            return conflictDecision(
                entityType = entityType,
                entityId = entityId,
                local = local,
                remote = remote,
                base = base,
                reason = conflictReason(local, remote),
            )
        }

        val localChanged = !local.sameContentAs(base)
        val remoteChanged = !remote.sameContentAs(base)

        return when {
            !localChanged && !remoteChanged ->
                decision(entityType, entityId, StudySyncMergeAction.NO_CHANGE, local, remote, base)
            localChanged && !remoteChanged ->
                decision(entityType, entityId, StudySyncMergeAction.KEEP_LOCAL, local, remote, base)
            !localChanged && remoteChanged ->
                decision(entityType, entityId, StudySyncMergeAction.USE_REMOTE, local, remote, base)
            else -> conflictDecision(
                entityType = entityType,
                entityId = entityId,
                local = local,
                remote = remote,
                base = base,
                reason = conflictReason(local, remote),
            )
        }
    }

    fun plan(
        local: List<StudySyncEntityVersion>,
        remote: List<StudySyncEntityVersion>,
        base: List<StudySyncEntityVersion> = emptyList(),
    ): StudySyncMergePlan {
        val localByKey = local.associateByStableKey("local")
        val remoteByKey = remote.associateByStableKey("remote")
        val baseByKey = base.associateByStableKey("base")
        val keys = (localByKey.keys + remoteByKey.keys + baseByKey.keys).sorted()
        return StudySyncMergePlan(
            decisions = keys.map { key ->
                decide(localByKey[key], remoteByKey[key], baseByKey[key])
            },
        )
    }

    private fun decision(
        entityType: String,
        entityId: String,
        action: StudySyncMergeAction,
        local: StudySyncEntityVersion?,
        remote: StudySyncEntityVersion?,
        base: StudySyncEntityVersion?,
    ): StudySyncMergeDecision = StudySyncMergeDecision(
        entityType = entityType,
        entityId = entityId,
        action = action,
        local = local,
        remote = remote,
        base = base,
    )

    private fun conflictDecision(
        entityType: String,
        entityId: String,
        local: StudySyncEntityVersion,
        remote: StudySyncEntityVersion,
        base: StudySyncEntityVersion?,
        reason: StudySyncMergeConflictReason,
    ): StudySyncMergeDecision = StudySyncMergeDecision(
        entityType = entityType,
        entityId = entityId,
        action = StudySyncMergeAction.MANUAL_CONFLICT,
        local = local,
        remote = remote,
        base = base,
        conflict = StudySyncMergeConflict(
            entityType = entityType,
            entityId = entityId,
            reason = reason,
            localUpdatedAtEpochMillis = local.updatedAtEpochMillis,
            remoteUpdatedAtEpochMillis = remote.updatedAtEpochMillis,
        ),
    )

    private fun conflictReason(
        local: StudySyncEntityVersion,
        remote: StudySyncEntityVersion,
    ): StudySyncMergeConflictReason = when {
        local.deleted && !remote.deleted -> StudySyncMergeConflictReason.DELETED_LOCALLY_CHANGED_REMOTELY
        !local.deleted && remote.deleted -> StudySyncMergeConflictReason.DELETED_REMOTELY_CHANGED_LOCALLY
        else -> StudySyncMergeConflictReason.BOTH_CHANGED
    }

    private fun requireKeysMatch(vararg versions: StudySyncEntityVersion?) {
        val present = versions.filterNotNull()
        val firstKey = present.first().stableKey
        require(present.all { version -> version.stableKey == firstKey }) {
            "Merge versions must reference the same stable entity id."
        }
    }

    private fun List<StudySyncEntityVersion>.associateByStableKey(
        label: String,
    ): Map<String, StudySyncEntityVersion> {
        val grouped = groupBy { version -> version.stableKey }
        val duplicates = grouped.filterValues { versions -> versions.size > 1 }.keys
        require(duplicates.isEmpty()) { "Duplicate $label sync entity ids: ${duplicates.joinToString()}" }
        return associateBy { version -> version.stableKey }
    }
}
