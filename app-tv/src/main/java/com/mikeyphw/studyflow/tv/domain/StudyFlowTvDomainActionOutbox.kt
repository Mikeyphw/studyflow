package com.mikeyphw.studyflow.tv.domain

import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvControlCommand
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvControlOrigin
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvDomainAction
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvDomainActionMapper
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvDomainActionProtocol
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvDomainActionResult
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvPresentationSnapshot

/**
 * Small in-memory TV outbox. It never applies StudyFlow mutations locally; the paired phone pulls
 * the oldest request, commits it through canonical domain managers, then acknowledges the result.
 */
class StudyFlowTvDomainActionOutbox {
    private val pending = LinkedHashMap<String, StudyFlowTvDomainAction>()
    private var lastResult: StudyFlowTvDomainActionResult? = null

    fun enqueue(
        command: StudyFlowTvControlCommand,
        snapshot: StudyFlowTvPresentationSnapshot?,
        origin: StudyFlowTvControlOrigin,
    ): Boolean {
        val action = StudyFlowTvDomainActionMapper.fromCommand(command, snapshot, origin) ?: return false
        if (pending.containsKey(action.actionId)) return true
        if (pending.size >= StudyFlowTvDomainActionProtocol.maxPendingActions) {
            pending.remove(pending.keys.first())
        }
        pending[action.actionId] = action
        return true
    }

    fun peek(): StudyFlowTvDomainAction? = pending.values.firstOrNull()

    fun acknowledge(result: StudyFlowTvDomainActionResult): Boolean {
        if (pending.remove(result.actionId) == null) return false
        lastResult = result
        return true
    }

    fun lastResult(): StudyFlowTvDomainActionResult? = lastResult
    fun size(): Int = pending.size
}
