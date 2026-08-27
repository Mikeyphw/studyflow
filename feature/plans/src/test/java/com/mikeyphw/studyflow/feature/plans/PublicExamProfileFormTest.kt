package com.mikeyphw.studyflow.feature.plans

import com.mikeyphw.studyflow.core.model.ExamStage
import com.mikeyphw.studyflow.core.model.ExamStageId
import com.mikeyphw.studyflow.core.model.ExamStageType
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudyTarget
import com.mikeyphw.studyflow.core.model.StudyTargetId
import com.mikeyphw.studyflow.core.model.StudyType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PublicExamProfileFormTest {
    @Test
    fun `contest profile is isolated to public examination plans`() {
        assertTrue(shouldShowPublicExamProfile(StudyType.PUBLIC_EXAMINATION))
        StudyType.entries.filterNot { it == StudyType.PUBLIC_EXAMINATION }.forEach { type ->
            assertFalse("Contest-only UX leaked into $type", shouldShowPublicExamProfile(type))
        }
    }

    @Test
    fun `repository backed target and stages hydrate without a duplicate model`() {
        val planId = StudyPlanId("plan")
        val targetId = StudyTargetId("target")
        val target = StudyTarget(
            id = targetId,
            studyPlanId = planId,
            organization = "TRT",
            role = "Analyst",
            examBoard = "FCC",
            noticeUrl = "edital-01",
            confirmedExamDateIso = null,
            expectedExamDateIso = "2027-10-06",
            openings = 12,
            estimatedCutoffPercent = 82,
            createdAtEpochMillis = 1L,
            updatedAtEpochMillis = 2L,
        )
        val stage = ExamStage(
            id = ExamStageId("objective"),
            studyPlanId = planId,
            studyTargetId = targetId,
            name = "Objective test",
            type = ExamStageType.OBJECTIVE,
            scheduledDateIso = "2027-10-06",
            weight = 2.0,
            minimumScorePercent = 60,
            position = 0,
        )

        val form = publicExamProfileForm(target, listOf(stage))

        assertEquals("TRT", form.organization)
        assertEquals("FCC", form.examBoard)
        assertEquals("2027-10-06", form.expectedExamDateIso)
        assertEquals("12", form.openings)
        assertEquals(ExamStageId("objective"), form.stages.single().id)
        assertTrue(isValidPublicExamProfileForm(form))
    }

    @Test
    fun `profile validation rejects fictional dates ranges and invalid stage weights`() {
        val base = PublicExamProfileForm(
            organization = "Agency",
            role = "Role",
            examBoard = "Board",
            noticeUrl = "",
            confirmedExamDateIso = "",
            expectedExamDateIso = "2027-10-06",
            openings = "10",
            estimatedCutoffPercent = "80",
            stages = listOf(
                PublicExamStageForm(
                    id = null,
                    name = "Objective",
                    type = ExamStageType.OBJECTIVE,
                    scheduledDateIso = "2027-10-06",
                    weight = "1.0",
                    minimumScorePercent = "50",
                ),
            ),
        )
        assertTrue(isValidPublicExamProfileForm(base))
        assertFalse(isValidPublicExamProfileForm(base.copy(expectedExamDateIso = "2027-02-30")))
        assertFalse(isValidPublicExamProfileForm(base.copy(estimatedCutoffPercent = "101")))
        assertFalse(isValidPublicExamProfileForm(base.copy(stages = base.stages.map { it.copy(weight = "NaN") })))
    }
}
