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
    fun canonicalSessionBoundsAcceptLongStudyDurationsAcrossModels() {
        val planId = StudyPlanId("plan")
        StudyStrategy(
            id = StudyStrategyId("long-strategy"),
            studyPlanId = planId,
            type = StudyStrategyType.ADAPTIVE,
            minimumSessionMinutes = 480,
            maximumSessionMinutes = 720,
            theoryRatioPercent = 50,
            questionRatioPercent = 30,
            reviewRatioPercent = 20,
            adaptivePlanningEnabled = true,
            configurationJson = "{}",
        )
        PlannedActivity(
            id = PlannedActivityId("long-activity"),
            studyPlanId = planId,
            subjectId = null,
            topicId = null,
            materialId = null,
            videoResourceId = null,
            type = StudyActivityType.THEORY,
            title = "Long block",
            scheduledStartEpochMillis = null,
            plannedDurationMinutes = 720,
            status = PlannedActivityStatus.PLANNED,
            locked = false,
            completedAtEpochMillis = null,
        )
        StudySession(
            id = StudySessionId("long-session"),
            studyPlanId = planId,
            plannedActivityId = null,
            subjectId = null,
            topicId = null,
            materialId = null,
            videoResourceId = null,
            type = StudyActivityType.THEORY,
            status = StudySessionStatus.RUNNING,
            startedAtEpochMillis = 1L,
            endedAtEpochMillis = null,
            grossDurationSeconds = 0L,
            pauseDurationSeconds = 0L,
            pagesCompleted = 0,
            questionsAnswered = 0,
            correctAnswers = 0,
            concentrationRating = null,
            difficultyRating = null,
            notes = "",
            targetDurationMinutes = 720,
        )
    }

    @Test
    fun materialTotalUnitsUsesSamePositiveContractAsManager() {
        assertThrows(IllegalArgumentException::class.java) {
            StudyMaterial(
                id = StudyMaterialId("empty-material"),
                studyPlanId = StudyPlanId("plan"),
                subjectId = null,
                topicId = null,
                type = StudyMaterialType.BOOK,
                title = "Book",
                description = "",
                sourceUri = null,
                author = null,
                totalUnits = 0,
                completedUnits = 0,
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
    fun mockExamRejectsNonFiniteNegativeMarking() {
        assertThrows(IllegalArgumentException::class.java) {
            MockExam(
                id = MockExamId("mock"),
                studyPlanId = StudyPlanId("plan"),
                title = "Mock",
                mode = TestMode.EXAM,
                timeLimitSeconds = 3_600L,
                questionCount = 10,
                answeredCount = 0,
                correctCount = 0,
                scorePercent = null,
                negativeMarking = Double.POSITIVE_INFINITY,
                minimumScorePercent = 60,
                startedAtEpochMillis = null,
                submittedAtEpochMillis = null,
                configurationJson = "questionIds=q1",
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
