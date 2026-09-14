package com.mikeyphw.studyflow.tv.focus

import android.view.View

/**
 * OV20 dynamic Android View focus binder.
 *
 * Earlier TV focus guarantees were mostly pure-model contracts. This binder applies the same
 * deterministic policy to the live View graph after rows/cards are rendered, so D-pad movement,
 * initial focus and disabled-item skips survive dynamic presentation updates.
 */
object StudyFlowTvDynamicFocusBinder {
    const val overlay = "OV20"
    const val dynamicViewWiringRequired = true
    const val disabledItemsSkipped = true
    const val deterministicInitialViewFocus = true

    fun wire(
        items: List<StudyFlowTvFocusItem>,
        viewsByItemId: Map<String, View>,
        currentId: String? = null,
    ): String? {
        val enabled = items.filter { it.enabled && viewsByItemId[it.id] != null }
        if (enabled.isEmpty()) return null
        enabled.forEach { item ->
            val view = viewsByItemId.getValue(item.id)
            view.isFocusable = true
            view.isFocusableInTouchMode = false
            view.nextFocusUpId = focusIdFor(enabled, viewsByItemId, item.id, StudyFlowTvRemoteCommand.UP)
            view.nextFocusDownId = focusIdFor(enabled, viewsByItemId, item.id, StudyFlowTvRemoteCommand.DOWN)
            view.nextFocusLeftId = focusIdFor(enabled, viewsByItemId, item.id, StudyFlowTvRemoteCommand.LEFT)
            view.nextFocusRightId = focusIdFor(enabled, viewsByItemId, item.id, StudyFlowTvRemoteCommand.RIGHT)
        }
        val initial = StudyFlowTvFocusPolicy.nextFocusId(enabled, currentId, StudyFlowTvRemoteCommand.SELECT)
            ?: StudyFlowTvFocusPolicy.initialFocusId(enabled)
        initial?.let { id -> viewsByItemId[id]?.post { viewsByItemId[id]?.requestFocus() } }
        return initial
    }

    private fun focusIdFor(
        items: List<StudyFlowTvFocusItem>,
        viewsByItemId: Map<String, View>,
        currentId: String,
        command: StudyFlowTvRemoteCommand,
    ): Int {
        val targetId = StudyFlowTvFocusPolicy.nextFocusId(items, currentId, command) ?: currentId
        return viewsByItemId[targetId]?.id ?: View.NO_ID
    }
}
