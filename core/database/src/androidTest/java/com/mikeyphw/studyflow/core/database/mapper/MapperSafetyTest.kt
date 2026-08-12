package com.mikeyphw.studyflow.core.database.mapper

import com.mikeyphw.studyflow.core.model.StudyPlan
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudyPlanStatus
import com.mikeyphw.studyflow.core.model.StudySessionStatus
import com.mikeyphw.studyflow.core.model.StudyType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MapperSafetyTest {
    @Test
    fun stableStringListRoundTripsEmptyStrings() {
        val encoded = listOf("", "alpha").encodeStringListStable()

        assertEquals(
            listOf("", "alpha"),
            encoded.decodeStringListStable("test.values"),
        )
    }

    @Test
    fun malformedEncodedElementFailsClosed() {
        assertThrows(StudyFlowDatabaseCorruptionException::class.java) {
            "b64:not@@base64".decodeStringListStable("test.values")
        }
    }

    @Test
    fun invalidPersistedModelFailsWithStructuredCorruption() {
        val entity = StudyPlan(
            id = StudyPlanId("plan"),
            name = "Plan",
            description = "",
            type = StudyType.FREE_STUDY,
            status = StudyPlanStatus.ACTIVE,
            examDateIso = null,
            weeklyGoalHours = 10,
            progressPercent = 0,
            studiedMinutesThisWeek = 0,
            questionsAnswered = 0,
            averageAccuracyPercent = 0,
            createdAtEpochMillis = 1,
            updatedAtEpochMillis = 1,
        ).asEntity(nowEpochMillis = 1).copy(weeklyGoalHours = 0)

        assertThrows(StudyFlowDatabaseCorruptionException::class.java) {
            entity.asExternalModel()
        }
    }

    @Test
    fun unknownEnumFailsClosedWithStructuredCorruption() {
        assertThrows(StudyFlowDatabaseCorruptionException::class.java) {
            "BROKEN".decodeEnum<StudySessionStatus>("study_sessions.status")
        }
    }
}
