package com.mikeyphw.studyflow.core.database

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyPlanGraphDuplicatorTest {
    @Test
    fun `every plan-owned table is included exactly once`() {
        val names = StudyPlanGraphDuplicator.TableSpecs.map { it.tableName }

        assertEquals(21, names.size)
        assertEquals(names.size, names.toSet().size)
        assertTrue("study_plan_settings" in names)
        assertTrue("review_items" in names)
        assertTrue("annexes" in names)
    }

    @Test
    fun `relationship-heavy tables remap all internal references`() {
        val sessions = StudyPlanGraphDuplicator.TableSpecs.single { it.tableName == "study_sessions" }
        val reviews = StudyPlanGraphDuplicator.TableSpecs.single { it.tableName == "review_items" }

        assertTrue(
            sessions.remappedIdentifierColumns.containsAll(
                setOf("id", "plannedActivityId", "subjectId", "topicId", "materialId", "videoResourceId"),
            ),
        )
        assertTrue(
            reviews.remappedIdentifierColumns.containsAll(
                setOf("id", "subjectId", "topicId", "questionId", "flashcardId", "errorEntryId"),
            ),
        )
    }
}
