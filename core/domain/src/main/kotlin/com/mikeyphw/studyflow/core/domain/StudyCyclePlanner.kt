package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.StudyCycleItem
import com.mikeyphw.studyflow.core.model.StudyCycleItemId
import com.mikeyphw.studyflow.core.model.StudyCycleSnapshot

object StudyCyclePlanner {
    fun currentEligibleItem(
        snapshot: StudyCycleSnapshot,
        eligibleItemIds: Set<StudyCycleItemId>,
    ): StudyCycleItem? {
        val eligible = snapshot.orderedItems.filter { it.enabled && it.id in eligibleItemIds }
        if (eligible.isEmpty()) return null
        return eligible.firstOrNull { it.id == snapshot.cycle.currentItemId } ?: eligible.first()
    }

    fun nextAfterCompletion(
        snapshot: StudyCycleSnapshot,
        completedItemId: StudyCycleItemId,
        eligibleItemIds: Set<StudyCycleItemId>,
    ): StudyCycleItem? {
        val completedPosition = snapshot.orderedItems.firstOrNull { it.id == completedItemId }?.position
            ?: return currentEligibleItem(snapshot, eligibleItemIds)
        val eligible = snapshot.orderedItems.filter { it.enabled && it.id in eligibleItemIds }
        if (eligible.isEmpty()) return null
        return eligible.firstOrNull { it.position > completedPosition } ?: eligible.first()
    }

    fun nextAfterSkip(
        snapshot: StudyCycleSnapshot,
        eligibleItemIds: Set<StudyCycleItemId>,
    ): StudyCycleItem? {
        val current = currentEligibleItem(snapshot, eligibleItemIds) ?: return null
        return nextAfterCompletion(snapshot, current.id, eligibleItemIds)
    }
}
