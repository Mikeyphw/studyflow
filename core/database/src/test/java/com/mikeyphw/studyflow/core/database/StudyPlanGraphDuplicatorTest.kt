package com.mikeyphw.studyflow.core.database

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyPlanGraphDuplicatorTest {
    @Test
    fun `every plan-owned table is included exactly once`() {
        val names = StudyPlanGraphDuplicator.TableSpecs.map { it.tableName }

        assertEquals(24, names.size)
        assertEquals(names.size, names.toSet().size)
        assertTrue("study_plan_settings" in names)
        assertTrue("programming_course_profiles" in names)
        assertTrue("code_snippets" in names)
        assertTrue("coding_exercises" in names)
        assertTrue("mini_projects" in names)
        assertTrue("termux_runtime_preferences" in names)
        assertTrue("review_items" in names)
        assertTrue("annexes" !in names)
        assertTrue("plan_snapshots" !in names)
        assertTrue("annexes" in StudyPlanGraphDuplicator.PortabilityTableSpecs.map { it.tableName })
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

    @Test
    fun `programming tables remap internal references`() {
        val snippets = StudyPlanGraphDuplicator.TableSpecs.single { it.tableName == "code_snippets" }
        val exercises = StudyPlanGraphDuplicator.TableSpecs.single { it.tableName == "coding_exercises" }
        val termux = StudyPlanGraphDuplicator.TableSpecs.single { it.tableName == "termux_runtime_preferences" }

        assertTrue(snippets.remappedIdentifierColumns.containsAll(setOf("id", "subjectId", "topicId")))
        assertTrue(exercises.remappedIdentifierColumns.containsAll(setOf("id", "subjectId", "topicId", "snippetId")))
        assertTrue(termux.remappedIdentifierColumns.isEmpty())
    }
}
