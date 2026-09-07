package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.ExamNoticeRevision
import com.mikeyphw.studyflow.core.model.ExamNoticeRevisionId
import com.mikeyphw.studyflow.core.model.StudyContentMode
import com.mikeyphw.studyflow.core.model.StudyPlan
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudyPlanStatus
import com.mikeyphw.studyflow.core.model.StudyTarget
import com.mikeyphw.studyflow.core.model.StudyTargetId
import com.mikeyphw.studyflow.core.model.StudyType
import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PublicExamPreEditalTest {
    private val planId = StudyPlanId("plan")
    private val targetId = StudyTargetId("target")
    private val zone = ZoneId.of("UTC")
    private val now = Instant.parse("2026-09-06T12:00:00Z").toEpochMilli()

    @Test
    fun `no official exam date produces pre-edital state without countdown`() {
        val snapshot = requireNotNull(
            PublicExamPreEditalBuilder.build(plan(), target(), emptyList(), now, zone),
        )

        assertTrue(snapshot.preEdital)
        assertEquals(PublicExamDateSource.NONE, snapshot.resolvedDate.source)
        assertNull(snapshot.officialCountdownDays)
        assertTrue(PublicExamPreEditalIssue.NO_CONFIRMED_EXAM_DATE in snapshot.issues)
        assertTrue(PublicExamPreEditalIssue.NO_REFERENCE_NOTICE in snapshot.issues)
    }

    @Test
    fun `expected date stays an assumption rather than becoming confirmed countdown`() {
        val snapshot = requireNotNull(
            PublicExamPreEditalBuilder.build(
                plan(),
                target(expectedDate = "2026-12-20"),
                emptyList(),
                now,
                zone,
            ),
        )

        assertTrue(snapshot.preEdital)
        assertEquals(PublicExamDateSource.EXPECTED_TARGET, snapshot.resolvedDate.source)
        assertFalse(snapshot.resolvedDate.isConfirmed)
        assertNull(snapshot.officialCountdownDays)
    }

    @Test
    fun `canonical official countdown ignores expected target and uses confirmed target`() {
        assertNull(
            PublicExamDateResolver.daysUntilOfficial(
                plan(), target(expectedDate = "2026-12-20"), now, zone,
            ),
        )
        assertEquals(
            105,
            PublicExamDateResolver.daysUntilOfficial(
                plan(), target(confirmedDate = "2026-12-20"), now, zone,
            ),
        )
    }

    @Test
    fun `confirmed target leaves pre-edital mode`() {
        val snapshot = requireNotNull(
            PublicExamPreEditalBuilder.build(
                plan(),
                target(confirmedDate = "2026-12-20"),
                emptyList(),
                now,
                zone,
            ),
        )

        assertFalse(snapshot.preEdital)
        assertTrue(snapshot.resolvedDate.isConfirmed)
        assertTrue(snapshot.issues.isEmpty())
    }

    @Test
    fun `old revision is visibly stale and latest revision wins`() {
        val old = revision("old", 100L, "2022-12-23")
        val newer = revision("reference-2022-001", 200L, "2022-12-23")
        val snapshot = requireNotNull(
            PublicExamPreEditalBuilder.build(plan(), target(), listOf(old, newer), now, zone),
        )

        assertEquals("reference-2022-001", snapshot.referenceLabel)
        assertTrue((snapshot.referenceAgeDays ?: 0L) > 365L)
        assertTrue(PublicExamPreEditalIssue.STALE_REFERENCE_NOTICE in snapshot.issues)
    }

    private fun plan() = StudyPlan(
        id = planId,
        name = "Concurso",
        description = "",
        type = StudyType.PUBLIC_EXAMINATION,
        contentMode = StudyContentMode.EXAM_PLAN,
        status = StudyPlanStatus.ACTIVE,
        examDateIso = null,
        weeklyGoalHours = 19,
        progressPercent = 0,
        studiedMinutesThisWeek = 0,
        questionsAnswered = 0,
        averageAccuracyPercent = 0,
        createdAtEpochMillis = 1,
        updatedAtEpochMillis = 1,
        trashedAtEpochMillis = null,
    )

    private fun target(
        confirmedDate: String? = null,
        expectedDate: String? = null,
    ) = StudyTarget(
        id = targetId,
        studyPlanId = planId,
        organization = "Banco do Brasil",
        role = "Agente Comercial",
        examBoard = "Cesgranrio",
        noticeUrl = null,
        confirmedExamDateIso = confirmedDate,
        expectedExamDateIso = expectedDate,
        openings = null,
        estimatedCutoffPercent = null,
        createdAtEpochMillis = 1,
        updatedAtEpochMillis = 1,
    )

    private fun revision(id: String, importedAt: Long, publicationDateIso: String?) = ExamNoticeRevision(
        id = ExamNoticeRevisionId(id),
        studyPlanId = planId,
        studyTargetId = targetId,
        label = id,
        sourceReference = null,
        importedAtEpochMillis = importedAt,
        normalizedContentSha256 = "a".repeat(64),
        parserVersion = "test",
        publicationDateIso = publicationDateIso,
        addedCount = 1,
        removedCount = 0,
        changedCount = 0,
        unchangedCount = 0,
    )
}
