package com.mikeyphw.studyflow.core.model

import org.junit.Assert.assertThrows
import org.junit.Test

class CoreModelInvariantTest {
    @Test
    fun identifiersRejectBlankValues() {
        assertThrows(IllegalArgumentException::class.java) {
            StudyPlanId("   ")
        }
        assertThrows(IllegalArgumentException::class.java) {
            QuestionId("")
        }
    }

    @Test
    fun studyPlanRejectsImpossibleExamDate() {
        assertThrows(IllegalArgumentException::class.java) {
            validPlan(examDateIso = "2026-02-31")
        }
    }

    @Test
    fun strategyRequiresValidSessionBoundsAndOneHundredPercentRatios() {
        assertThrows(IllegalArgumentException::class.java) {
            StudyStrategy(
                id = StudyStrategyId("strategy"),
                studyPlanId = StudyPlanId("plan"),
                type = StudyStrategyType.ADAPTIVE,
                minimumSessionMinutes = 25,
                maximumSessionMinutes = 120,
                theoryRatioPercent = 50,
                questionRatioPercent = 30,
                reviewRatioPercent = 30,
                adaptivePlanningEnabled = true,
                configurationJson = "{}",
            )
        }
    }

    @Test
    fun studyMaterialCompletedUnitsCannotExceedTotalUnits() {
        assertThrows(IllegalArgumentException::class.java) {
            StudyMaterial(
                id = StudyMaterialId("material"),
                studyPlanId = StudyPlanId("plan"),
                subjectId = null,
                topicId = null,
                type = StudyMaterialType.BOOK,
                title = "Book",
                description = "",
                sourceUri = null,
                author = null,
                totalUnits = 10,
                completedUnits = 12,
                primary = false,
                favorite = false,
                archived = false,
                tags = emptyList(),
                createdAtEpochMillis = 1L,
                updatedAtEpochMillis = 1L,
            )
        }
    }

    @Test
    fun multipleChoiceCorrectAnswerMustBeOneOfTheOptions() {
        assertThrows(IllegalArgumentException::class.java) {
            Question(
                id = QuestionId("question"),
                studyPlanId = StudyPlanId("plan"),
                subjectId = null,
                topicId = null,
                type = QuestionType.MULTIPLE_CHOICE,
                statement = "Capital of France?",
                options = listOf("London", "Rome"),
                correctAnswer = "Paris",
                explanation = "",
                examBoard = null,
                source = null,
                year = 2026,
                difficulty = 3,
                favorite = false,
                disputed = false,
                tags = emptyList(),
                createdAtEpochMillis = 1L,
                updatedAtEpochMillis = 1L,
            )
        }
    }

    private fun validPlan(examDateIso: String?): StudyPlan = StudyPlan(
        id = StudyPlanId("plan"),
        name = "Plan",
        description = "",
        type = StudyType.FREE_STUDY,
        status = StudyPlanStatus.ACTIVE,
        examDateIso = examDateIso,
        weeklyGoalHours = 10,
        progressPercent = 0,
        studiedMinutesThisWeek = 0,
        questionsAnswered = 0,
        averageAccuracyPercent = 0,
    )
}
