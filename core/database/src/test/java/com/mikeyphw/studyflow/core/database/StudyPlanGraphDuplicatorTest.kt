package com.mikeyphw.studyflow.core.database

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyPlanGraphDuplicatorTest {
    @Test
    fun `every plan-owned table is included exactly once`() {
        val names = StudyPlanGraphDuplicator.TableSpecs.map { it.tableName }

        val expectedTables = setOf(
            "study_targets",
            "exam_stages",
            "exam_notice_revisions",
            "subjects",
            "topics",
            "legislation_documents",
            "legislation_provisions",
            "study_cycles",
            "study_cycle_items",
            "study_strategies",
            "schedule_rules",
            "study_materials",
            "video_resources",
            "planned_activities",
            "study_sessions",
            "study_goals",
            "questions",
            "study_tests",
            "mock_exams",
            "flashcards",
            "error_entries",
            "review_items",
            "legislation_evidence_links",
            "study_notes",
            "study_plan_settings",
            "programming_course_profiles",
            "code_snippets",
            "coding_exercises",
            "mini_projects",
            "termux_runtime_preferences",
            "termux_command_requests",
            "termux_command_results",
            "language_deck_profiles",
            "language_expressions",
            "idiom_cards",
            "vocabulary_cards",
            "language_review_attempts",
        )

        assertEquals(expectedTables, names.toSet())
        assertEquals(expectedTables.size, names.size)
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
        val requests = StudyPlanGraphDuplicator.TableSpecs.single { it.tableName == "termux_command_requests" }
        val results = StudyPlanGraphDuplicator.TableSpecs.single { it.tableName == "termux_command_results" }
        val expressions = StudyPlanGraphDuplicator.TableSpecs.single { it.tableName == "language_expressions" }
        val idioms = StudyPlanGraphDuplicator.TableSpecs.single { it.tableName == "idiom_cards" }
        val attempts = StudyPlanGraphDuplicator.TableSpecs.single { it.tableName == "language_review_attempts" }

        assertTrue(snippets.remappedIdentifierColumns.containsAll(setOf("id", "subjectId", "topicId")))
        assertTrue(exercises.remappedIdentifierColumns.containsAll(setOf("id", "subjectId", "topicId", "snippetId")))
        assertTrue(termux.remappedIdentifierColumns.isEmpty())
        assertTrue(requests.remappedIdentifierColumns.containsAll(setOf("id", "exerciseId")))
        assertTrue(results.remappedIdentifierColumns.containsAll(setOf("id", "requestId")))
        assertTrue(results.sourcePlanPredicate.contains("termux_command_requests"))
        assertTrue(expressions.remappedIdentifierColumns.containsAll(setOf("id", "deckId")))
        assertTrue(idioms.sourcePlanPredicate.contains("language_expressions"))
        assertTrue(attempts.remappedIdentifierColumns.containsAll(setOf("id", "expressionId", "idiomCardId", "reviewItemId")))
    }
}
