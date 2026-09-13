package com.mikeyphw.studyflow.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class StudyGoalAndTopicIntegrityTest {
    private val planId = StudyPlanId("plan")
    private val subjectId = SubjectId("subject")

    @Test
    fun goalDatesAreStrictAndCustomRangesMustBeBoundedAndOrdered() {
        assertThrows(IllegalArgumentException::class.java) {
            goal(starts = "2026-02-31", ends = null, period = GoalPeriod.WEEKLY)
        }
        assertThrows(IllegalArgumentException::class.java) {
            goal(starts = "2026-09-01", ends = null, period = GoalPeriod.CUSTOM)
        }
        assertThrows(IllegalArgumentException::class.java) {
            goal(starts = "2026-09-10", ends = "2026-09-09", period = GoalPeriod.CUSTOM)
        }

        val valid = goal(starts = "2026-09-10", ends = "2026-09-10", period = GoalPeriod.CUSTOM)
        assertEquals("2026-09-10", valid.endsOnIso)
    }

    @Test
    fun topicTreeRejectsOrphansInsteadOfDroppingThem() {
        val orphan = topic("orphan", parent = TopicId("missing"))
        assertThrows(IllegalArgumentException::class.java) { buildTopicTree(listOf(orphan)) }
    }

    @Test
    fun topicTreeRejectsCyclesInsteadOfReturningAnIncompleteProjection() {
        val first = topic("first", parent = TopicId("second"))
        val second = topic("second", parent = TopicId("first"))
        assertThrows(IllegalStateException::class.java) { buildTopicTree(listOf(first, second)) }
    }

    @Test
    fun topicTreeRejectsDuplicateSelfParentAndCrossOwnerGraphs() {
        val duplicateA = topic("duplicate", parent = null)
        val duplicateB = topic("duplicate", parent = null)
        assertThrows(IllegalArgumentException::class.java) {
            buildTopicTree(listOf(duplicateA, duplicateB))
        }

        val self = topic("self", parent = TopicId("self"))
        assertThrows(IllegalArgumentException::class.java) { buildTopicTree(listOf(self)) }

        val foreignParent = topic("foreign-parent", parent = null).copy(studyPlanId = StudyPlanId("foreign"))
        val child = topic("local-child", parent = foreignParent.id)
        assertThrows(IllegalArgumentException::class.java) {
            buildTopicTree(listOf(foreignParent, child))
        }
    }

    @Test
    fun validTopicTreeProjectsEveryTopicExactlyOnce() {
        val root = topic("root", parent = null)
        val child = topic("child", parent = root.id)
        val grandchild = topic("grandchild", parent = child.id)

        val tree = buildTopicTree(listOf(grandchild, root, child))

        assertEquals(1, tree.size)
        assertEquals(root.id, tree.single().topic.id)
        assertEquals(child.id, tree.single().children.single().topic.id)
        assertEquals(grandchild.id, tree.single().children.single().children.single().topic.id)
    }

    private fun goal(starts: String, ends: String?, period: GoalPeriod) = StudyGoal(
        id = StudyGoalId("goal-$starts-${ends ?: "none"}"),
        studyPlanId = planId,
        subjectId = null,
        topicId = null,
        type = GoalType.STUDY_TIME,
        period = period,
        title = "Goal",
        targetValue = 60.0,
        currentValue = 0.0,
        startsOnIso = starts,
        endsOnIso = ends,
        completedAtEpochMillis = null,
        archived = false,
    )

    private fun topic(id: String, parent: TopicId?) = Topic(
        id = TopicId(id),
        studyPlanId = planId,
        subjectId = subjectId,
        parentTopicId = parent,
        name = id,
        description = "",
        priority = 3,
        difficulty = 3,
        examIncidencePercent = null,
        estimatedMinutes = 30,
        progressPercent = 0,
        masteryPercent = 0,
        status = ContentProgressStatus.NOT_STARTED,
        position = 0,
        archived = false,
    )
}
