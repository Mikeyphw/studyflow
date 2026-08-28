package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.StudyActivityType
import com.mikeyphw.studyflow.core.model.StudyCycle
import com.mikeyphw.studyflow.core.model.StudyCycleId
import com.mikeyphw.studyflow.core.model.StudyCycleItem
import com.mikeyphw.studyflow.core.model.StudyCycleItemId
import com.mikeyphw.studyflow.core.model.StudyCycleSnapshot
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.SubjectId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StudyCyclePlannerTest {
    private val planId = StudyPlanId("plan")
    private val cycleId = StudyCycleId("cycle")

    @Test
    fun `completion advances after bound position and wraps deterministically`() {
        val snapshot = snapshot(current = "b")
        val eligible = setOf("a", "b", "c").map(::StudyCycleItemId).toSet()
        assertEquals("c", StudyCyclePlanner.nextAfterCompletion(snapshot, StudyCycleItemId("b"), eligible)?.id?.value)
        assertEquals("a", StudyCyclePlanner.nextAfterCompletion(snapshot, StudyCycleItemId("c"), eligible)?.id?.value)
    }

    @Test
    fun `archived or disabled items are excluded without skipping remaining order`() {
        val snapshot = snapshot(current = "b")
        val eligible = setOf(StudyCycleItemId("a"), StudyCycleItemId("c"))
        assertEquals("a", StudyCyclePlanner.currentEligibleItem(snapshot, eligible)?.id?.value)
        assertEquals("c", StudyCyclePlanner.nextAfterCompletion(snapshot, StudyCycleItemId("a"), eligible)?.id?.value)
    }

    @Test
    fun `empty eligibility has no current action`() {
        assertNull(StudyCyclePlanner.currentEligibleItem(snapshot(current = "a"), emptySet()))
    }

    private fun snapshot(current: String) = StudyCycleSnapshot(
        cycle = StudyCycle(
            id = cycleId,
            studyPlanId = planId,
            name = "Cycle",
            enabled = true,
            currentItemId = StudyCycleItemId(current),
            createdAtEpochMillis = 1,
            updatedAtEpochMillis = 1,
        ),
        items = listOf("a", "b", "c").mapIndexed { index, id ->
            StudyCycleItem(
                id = StudyCycleItemId(id),
                studyCycleId = cycleId,
                studyPlanId = planId,
                subjectId = SubjectId("subject-$id"),
                topicId = null,
                position = index,
                targetDurationMinutes = 45,
                enabled = true,
                activityType = StudyActivityType.THEORY,
                completionRule = "SESSION_COMPLETED",
            )
        },
    )
}
