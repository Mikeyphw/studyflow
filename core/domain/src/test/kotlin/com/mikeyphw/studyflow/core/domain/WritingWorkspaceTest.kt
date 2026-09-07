package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.StudyActivityType
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudySession
import com.mikeyphw.studyflow.core.model.StudySessionId
import com.mikeyphw.studyflow.core.model.StudySessionStatus
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WritingWorkspaceTest {
    @Test
    fun `writing payload round trips multiline draft rubric and checklist`() {
        val record = WritingSessionRecord(
            prompt = "Tema sobre inclusão financeira",
            draftText = "Primeiro parágrafo.\n\nSegundo parágrafo.",
            rubric = WritingRubric(18, 17, 16, 19, 15),
            checklist = WritingCompletionChecklist(true, true, true, true),
        )

        val encoded = WritingSessionNoteCodec.encode(record)
        val decoded = WritingSessionNoteCodec.decode(encoded)

        assertEquals(record, decoded)
        assertEquals(85, decoded?.scorePercent)
        assertTrue(decoded?.checklist?.complete == true)
    }

    @Test
    fun `codec does not reinterpret legacy session notes`() {
        assertNull(WritingSessionNoteCodec.decode("Redação semanal"))
    }

    @Test
    fun `rubric dimensions remain a truthful 100 point scale`() {
        assertEquals(100, WritingRubric(20, 20, 20, 20, 20).totalScore)
        assertTrue(runCatching { WritingRubric(21, 0, 0, 0, 0) }.isFailure)
    }

    @Test
    fun `weekly progress counts only completed writing sessions in the current week`() {
        val zone = ZoneId.of("America/Sao_Paulo")
        val now = LocalDateTime.of(2026, 9, 10, 12, 0).atZone(zone).toInstant().toEpochMilli()
        val monday = LocalDateTime.of(2026, 9, 7, 12, 0).atZone(zone).toInstant().toEpochMilli()
        val previousSunday = LocalDateTime.of(2026, 9, 6, 12, 0).atZone(zone).toInstant().toEpochMilli()
        val sessions = listOf(
            session("this-week", StudyActivityType.WRITING, StudySessionStatus.COMPLETED, monday),
            session("old", StudyActivityType.WRITING, StudySessionStatus.COMPLETED, previousSunday),
            session("questions", StudyActivityType.QUESTIONS, StudySessionStatus.COMPLETED, monday),
            session("cancelled", StudyActivityType.WRITING, StudySessionStatus.CANCELLED, monday),
        )

        val progress = WritingWorkspaceAnalytics.weeklyProgress(
            sessions = sessions,
            target = 1,
            nowEpochMillis = now,
            zoneId = zone,
        )

        assertEquals(1, progress.completed)
        assertEquals(1, progress.target)
        assertTrue(progress.reached)
        assertEquals(listOf("this-week", "old"), WritingWorkspaceAnalytics.history(sessions).map { it.session.id.value })
    }

    @Test
    fun `incomplete checklist cannot claim completion`() {
        val checklist = WritingCompletionChecklist(
            taskReviewed = true,
            structureReviewed = true,
            cohesionReviewed = false,
            languageReviewed = true,
        )

        assertFalse(checklist.complete)
    }

    private fun session(
        id: String,
        type: StudyActivityType,
        status: StudySessionStatus,
        endedAt: Long,
    ) = StudySession(
        id = StudySessionId(id),
        studyPlanId = StudyPlanId("plan"),
        plannedActivityId = null,
        subjectId = null,
        topicId = null,
        materialId = null,
        videoResourceId = null,
        type = type,
        status = status,
        startedAtEpochMillis = endedAt - 1_000,
        endedAtEpochMillis = endedAt,
        grossDurationSeconds = 1,
        pauseDurationSeconds = 0,
        pagesCompleted = 0,
        questionsAnswered = 0,
        correctAnswers = 0,
        concentrationRating = null,
        difficultyRating = null,
        notes = if (type == StudyActivityType.WRITING) {
            WritingSessionNoteCodec.encode(
                WritingSessionRecord(
                    prompt = id,
                    draftText = "draft",
                    rubric = WritingRubric(10, 10, 10, 10, 10),
                    checklist = WritingCompletionChecklist(true, true, true, true),
                ),
            )
        } else {
            id
        },
    )
}
