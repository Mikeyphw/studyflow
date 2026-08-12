package com.mikeyphw.studyflow.core.database.mapper

import com.mikeyphw.studyflow.core.model.ExamStage
import com.mikeyphw.studyflow.core.model.ExamStageId
import com.mikeyphw.studyflow.core.model.ExamStageType
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudyTarget
import com.mikeyphw.studyflow.core.model.StudyTargetId
import org.junit.Assert.assertEquals
import org.junit.Test

class StudyStructureEntityMapperTest {
    @Test
    fun studyTargetRoundTripsThroughEntity() {
        val target = StudyTarget(
            id = StudyTargetId("target"),
            studyPlanId = StudyPlanId("plan"),
            organization = "Organization",
            role = "Role",
            examBoard = "Board",
            noticeUrl = "https://example.invalid/notice",
            confirmedExamDateIso = "2026-09-10",
            expectedExamDateIso = null,
            openings = 10,
            estimatedCutoffPercent = 75,
            createdAtEpochMillis = 1L,
            updatedAtEpochMillis = 2L,
        )

        assertEquals(target, target.asEntity().asExternalModel())
    }

    @Test
    fun examStageRoundTripsThroughEntity() {
        val stage = ExamStage(
            id = ExamStageId("stage"),
            studyPlanId = StudyPlanId("plan"),
            studyTargetId = StudyTargetId("target"),
            name = "Objective test",
            type = ExamStageType.OBJECTIVE,
            scheduledDateIso = "2026-09-10",
            weight = 1.0,
            minimumScorePercent = 60,
            position = 0,
        )

        assertEquals(stage, stage.asEntity().asExternalModel())
    }
}
