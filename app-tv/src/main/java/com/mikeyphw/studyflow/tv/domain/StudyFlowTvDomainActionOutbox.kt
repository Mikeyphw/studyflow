package com.mikeyphw.studyflow.tv.domain

import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvControlCommand
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvControlOrigin
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvDomainAction
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvDomainActionMapper
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvDomainActionProtocol
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvDomainActionResult
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvPresentationSnapshot

/**
 * Durable TV domain-action queue. The TV still does not mutate StudyFlow business state locally;
 * it records canonical mutation requests and waits for the paired data-owning phone to commit and
 * acknowledge them. OV18 makes that queue restart-safe and fail-closed under backpressure.
 */
class StudyFlowTvDomainActionOutbox(
    private val persistence: StudyFlowTvDomainActionOutboxPersistence? = null,
) {
    private val pending = LinkedHashMap<String, StudyFlowTvDomainAction>()
    private var lastResult: StudyFlowTvDomainActionResult? = null
    private var lastRejectionReason: String? = null

    init {
        persistence?.load()?.let { snapshot ->
            pending.putAll(snapshot.pending.associateBy { it.actionId })
            lastResult = snapshot.lastResult
        }
    }

    fun enqueue(
        command: StudyFlowTvControlCommand,
        snapshot: StudyFlowTvPresentationSnapshot?,
        origin: StudyFlowTvControlOrigin,
    ): Boolean {
        val action = StudyFlowTvDomainActionMapper.fromCommand(command, snapshot, origin) ?: run {
            lastRejectionReason = "not-canonical-domain-action"
            return false
        }
        if (pending.containsKey(action.actionId)) {
            lastRejectionReason = null
            return true
        }
        if (pending.size >= StudyFlowTvDomainActionProtocol.maxPendingActions) {
            lastRejectionReason = "domain-action-outbox-full"
            return false
        }
        pending[action.actionId] = action
        lastRejectionReason = null
        persist()
        return true
    }

    fun peek(): StudyFlowTvDomainAction? = pending.values.firstOrNull()

    fun pendingActions(): List<StudyFlowTvDomainAction> = pending.values.toList()

    fun acknowledge(result: StudyFlowTvDomainActionResult): Boolean {
        if (pending.remove(result.actionId) == null) {
            lastRejectionReason = "domain-action-not-pending"
            return false
        }
        lastResult = result
        lastRejectionReason = null
        persist()
        return true
    }

    fun lastResult(): StudyFlowTvDomainActionResult? = lastResult
    fun lastRejectionReason(): String? = lastRejectionReason
    fun size(): Int = pending.size

    private fun persist() {
        persistence?.save(StudyFlowTvDomainActionOutboxSnapshot(pending.values.toList(), lastResult))
    }

    companion object {
        const val overlay = "OV18"
        const val persistentOutboxRequired = true
        const val dropsOldestPendingAction = false
        const val explicitBackpressureReason = "domain-action-outbox-full"
    }
}

data class StudyFlowTvDomainActionOutboxSnapshot(
    val pending: List<StudyFlowTvDomainAction>,
    val lastResult: StudyFlowTvDomainActionResult?,
)

interface StudyFlowTvDomainActionOutboxPersistence {
    fun load(): StudyFlowTvDomainActionOutboxSnapshot?
    fun save(snapshot: StudyFlowTvDomainActionOutboxSnapshot): Boolean
}
