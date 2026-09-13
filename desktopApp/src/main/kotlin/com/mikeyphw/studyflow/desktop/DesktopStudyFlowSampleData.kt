package com.mikeyphw.studyflow.desktop

import com.mikeyphw.studyflow.core.domain.PracticeAttemptCodec
import com.mikeyphw.studyflow.core.model.Annex
import com.mikeyphw.studyflow.core.model.AnnexId
import com.mikeyphw.studyflow.core.model.AnnexStorageMode
import com.mikeyphw.studyflow.core.model.ContentProgressStatus
import com.mikeyphw.studyflow.core.model.MockExam
import com.mikeyphw.studyflow.core.model.MockExamId
import com.mikeyphw.studyflow.core.model.PlannedActivity
import com.mikeyphw.studyflow.core.model.PlannedActivityId
import com.mikeyphw.studyflow.core.model.PlannedActivityStatus
import com.mikeyphw.studyflow.core.model.Question
import com.mikeyphw.studyflow.core.model.QuestionId
import com.mikeyphw.studyflow.core.model.QuestionType
import com.mikeyphw.studyflow.core.model.ScheduleRule
import com.mikeyphw.studyflow.core.model.ScheduleRuleId
import com.mikeyphw.studyflow.core.model.ScheduleRuleType
import com.mikeyphw.studyflow.core.model.StudyActivityType
import com.mikeyphw.studyflow.core.model.StudyMaterial
import com.mikeyphw.studyflow.core.model.StudyMaterialId
import com.mikeyphw.studyflow.core.model.StudyMaterialType
import com.mikeyphw.studyflow.core.model.StudyPlan
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudyPlanStatus
import com.mikeyphw.studyflow.core.model.StudySession
import com.mikeyphw.studyflow.core.model.StudySessionId
import com.mikeyphw.studyflow.core.model.StudySessionStatus
import com.mikeyphw.studyflow.core.model.StudyStrategy
import com.mikeyphw.studyflow.core.model.StudyStrategyId
import com.mikeyphw.studyflow.core.model.StudyStrategyType
import com.mikeyphw.studyflow.core.model.StudyTest
import com.mikeyphw.studyflow.core.model.StudyTestId
import com.mikeyphw.studyflow.core.model.StudyType
import com.mikeyphw.studyflow.core.model.Subject
import com.mikeyphw.studyflow.core.model.SubjectId
import com.mikeyphw.studyflow.core.model.Topic
import com.mikeyphw.studyflow.core.model.TopicId
import com.mikeyphw.studyflow.core.model.TestMode
import com.mikeyphw.studyflow.core.model.VideoResource
import com.mikeyphw.studyflow.core.model.VideoResourceId
import com.mikeyphw.studyflow.core.model.VideoSourceType

data class DesktopStudyFlowSampleData(
    val plans: List<StudyPlan>,
    val subjects: List<Subject>,
    val topics: List<Topic>,
    val materials: List<StudyMaterial>,
    val videos: List<VideoResource>,
    val annexes: List<Annex>,
    val strategies: List<StudyStrategy>,
    val scheduleRules: List<ScheduleRule>,
    val plannedActivities: List<PlannedActivity>,
    val sessions: List<StudySession>,
    val questions: List<Question>,
    val tests: List<StudyTest>,
    val mockExams: List<MockExam>,
) {
    companion object {
        fun create(): DesktopStudyFlowSampleData {
            val now = 1_786_925_000_000L
            val planId = StudyPlanId("desktop-sample-plan")
            val subjectId = SubjectId("desktop-sample-subject")
            val materialId = StudyMaterialId("desktop-material-kotlin")
            val questions = sampleQuestions(planId, subjectId, now)
            return DesktopStudyFlowSampleData(
                plans = listOf(samplePlan(planId, now)),
                subjects = sampleSubjects(planId, subjectId),
                topics = sampleTopics(planId, subjectId),
                materials = sampleMaterials(planId, subjectId, now),
                videos = sampleVideos(planId, subjectId, now),
                annexes = sampleAnnexes(planId, subjectId, now),
                strategies = listOf(sampleStrategy(planId)),
                scheduleRules = sampleRules(planId),
                plannedActivities = sampleActivities(planId, subjectId, materialId, now),
                sessions = sampleSessions(planId, subjectId, materialId, now),
                questions = questions,
                tests = sampleTests(planId, questions, now),
                mockExams = sampleMockExams(planId, questions, now),
            )
        }

        private fun samplePlan(planId: StudyPlanId, now: Long): StudyPlan = StudyPlan(
            id = planId,
            name = "Desktop Study Plan",
            description = "Sample JVM state rendered through the desktop bootstrap.",
            type = StudyType.FREE_STUDY,
            status = StudyPlanStatus.ACTIVE,
            examDateIso = null,
            weeklyGoalHours = 12,
            progressPercent = 42,
            studiedMinutesThisWeek = 285,
            questionsAnswered = 128,
            averageAccuracyPercent = 76,
            createdAtEpochMillis = now - ONE_WEEK_MILLIS,
            updatedAtEpochMillis = now,
        )

        private fun sampleSubjects(planId: StudyPlanId, subjectId: SubjectId): List<Subject> = listOf(
            Subject(
                id = subjectId,
                studyPlanId = planId,
                name = "Kotlin",
                description = "Desktop sample subject",
                colorArgb = null,
                priority = 3,
                examWeight = 1.0,
                estimatedMinutes = 600,
                progressPercent = 42,
                accuracyPercent = 76,
                status = ContentProgressStatus.STUDYING,
                position = 0,
                archived = false,
            ),
        )

        private fun sampleTopics(planId: StudyPlanId, subjectId: SubjectId): List<Topic> = listOf(
            Topic(
                id = TopicId("desktop-sample-topic"),
                studyPlanId = planId,
                subjectId = subjectId,
                parentTopicId = null,
                name = "Language foundations",
                description = "Core syntax and language semantics",
                priority = 3,
                difficulty = 2,
                examIncidencePercent = null,
                estimatedMinutes = 240,
                progressPercent = 42,
                masteryPercent = 55,
                status = ContentProgressStatus.STUDYING,
                position = 0,
                archived = false,
            ),
        )

        private fun sampleMaterials(
            planId: StudyPlanId,
            subjectId: SubjectId,
            now: Long,
        ): List<StudyMaterial> = listOf(
            StudyMaterial(
                id = StudyMaterialId("desktop-material-kotlin"),
                studyPlanId = planId,
                subjectId = subjectId,
                topicId = null,
                type = StudyMaterialType.NOTE,
                title = "Kotlin foundations notes",
                description = "Local notes used to verify the library list and detail shell.",
                sourceUri = null,
                author = "StudyFlow sample",
                totalUnits = 10,
                completedUnits = 6,
                primary = true,
                favorite = true,
                archived = false,
                tags = listOf("kotlin", "desktop"),
                createdAtEpochMillis = now - ONE_WEEK_MILLIS,
                updatedAtEpochMillis = now,
            ),
            StudyMaterial(
                id = StudyMaterialId("desktop-material-compose"),
                studyPlanId = planId,
                subjectId = subjectId,
                topicId = null,
                type = StudyMaterialType.WEBSITE,
                title = "Compose Multiplatform docs",
                description = "External reference reserved for the future desktop resource launcher.",
                sourceUri = "https://kotlinlang.org/docs/compose-multiplatform.html",
                author = "JetBrains",
                totalUnits = 8,
                completedUnits = 3,
                primary = false,
                favorite = false,
                archived = false,
                tags = listOf("compose", "ui"),
                createdAtEpochMillis = now - ONE_WEEK_MILLIS / 2,
                updatedAtEpochMillis = now,
            ),
            StudyMaterial(
                id = StudyMaterialId("desktop-material-archive"),
                studyPlanId = planId,
                subjectId = subjectId,
                topicId = null,
                type = StudyMaterialType.PDF,
                title = "Archived scratch PDF",
                description = "Archived item stays out of the active desktop library list.",
                sourceUri = "content://studyflow/archived-scratch.pdf",
                author = null,
                totalUnits = 4,
                completedUnits = 4,
                primary = false,
                favorite = false,
                archived = true,
                tags = listOf("archive"),
                createdAtEpochMillis = now - ONE_WEEK_MILLIS,
                updatedAtEpochMillis = now,
            ),
        )

        private fun sampleVideos(
            planId: StudyPlanId,
            subjectId: SubjectId,
            now: Long,
        ): List<VideoResource> = listOf(
            VideoResource(
                id = VideoResourceId("desktop-video-compose"),
                studyPlanId = planId,
                subjectId = subjectId,
                topicId = null,
                title = "Desktop layout walkthrough",
                sourceType = VideoSourceType.WEB_PAGE,
                sourceUri = "https://kotlinlang.org/docs/multiplatform/compose-desktop.html",
                canonicalSourceId = null,
                thumbnailUri = null,
                durationMillis = 1_800_000L,
                playbackPositionMillis = 420_000L,
                completed = false,
                createdAtEpochMillis = now - ONE_WEEK_MILLIS / 3,
                updatedAtEpochMillis = now,
            ),
        )

        private fun sampleAnnexes(
            planId: StudyPlanId,
            subjectId: SubjectId,
            now: Long,
        ): List<Annex> = listOf(
            Annex(
                id = AnnexId("desktop-annex-checklist"),
                studyPlanId = planId,
                subjectId = subjectId,
                topicId = null,
                materialId = StudyMaterialId("desktop-material-kotlin"),
                displayName = "studyflow-desktop-checklist.txt",
                mimeType = "text/plain",
                byteSize = 512L,
                checksumSha256 = null,
                storageMode = AnnexStorageMode.IMPORTED_COPY,
                storageReference = "desktop/sample/studyflow-desktop-checklist.txt",
                persistedPermission = false,
                createdAtEpochMillis = now - ONE_WEEK_MILLIS / 4,
                updatedAtEpochMillis = now,
            ),
        )

        private fun sampleStrategy(planId: StudyPlanId): StudyStrategy = StudyStrategy(
            id = StudyStrategyId("desktop-strategy-main"),
            studyPlanId = planId,
            type = StudyStrategyType.ADAPTIVE,
            minimumSessionMinutes = 25,
            maximumSessionMinutes = 90,
            theoryRatioPercent = 45,
            questionRatioPercent = 35,
            reviewRatioPercent = 20,
            adaptivePlanningEnabled = true,
            configurationJson = "desktop-preview=true",
        )

        private fun sampleRules(planId: StudyPlanId): List<ScheduleRule> = listOf(
            ScheduleRule(
                id = ScheduleRuleId("desktop-rule-weekday"),
                studyPlanId = planId,
                subjectId = null,
                type = ScheduleRuleType.RECURRING_BLOCK,
                dayOfWeek = 2,
                startMinuteOfDay = 19 * 60,
                durationMinutes = 90,
                recurrenceRule = "WEEKLY",
                locked = false,
                enabled = true,
                position = 0,
            ),
            ScheduleRule(
                id = ScheduleRuleId("desktop-rule-weekend"),
                studyPlanId = planId,
                subjectId = null,
                type = ScheduleRuleType.CYCLE_SLOT,
                dayOfWeek = 7,
                startMinuteOfDay = 10 * 60,
                durationMinutes = 120,
                recurrenceRule = "WEEKLY",
                locked = false,
                enabled = true,
                position = 1,
            ),
        )

        private fun sampleActivities(
            planId: StudyPlanId,
            subjectId: SubjectId,
            materialId: StudyMaterialId,
            now: Long,
        ): List<PlannedActivity> = listOf(
            PlannedActivity(
                id = PlannedActivityId("desktop-activity-compose-reading"),
                studyPlanId = planId,
                subjectId = subjectId,
                topicId = null,
                materialId = materialId,
                videoResourceId = null,
                type = StudyActivityType.READING,
                title = "Read Kotlin foundations notes",
                scheduledStartEpochMillis = now + 2 * HOUR_MILLIS,
                plannedDurationMinutes = 50,
                status = PlannedActivityStatus.PLANNED,
                locked = false,
                completedAtEpochMillis = null,
            ),
            PlannedActivity(
                id = PlannedActivityId("desktop-activity-practice"),
                studyPlanId = planId,
                subjectId = subjectId,
                topicId = null,
                materialId = null,
                videoResourceId = null,
                type = StudyActivityType.QUESTIONS,
                title = "Practice Compose state questions",
                scheduledStartEpochMillis = now + 4 * HOUR_MILLIS,
                plannedDurationMinutes = 40,
                status = PlannedActivityStatus.PLANNED,
                locked = false,
                completedAtEpochMillis = null,
            ),
            PlannedActivity(
                id = PlannedActivityId("desktop-activity-review"),
                studyPlanId = planId,
                subjectId = subjectId,
                topicId = null,
                materialId = null,
                videoResourceId = null,
                type = StudyActivityType.REVIEW,
                title = "Review weak JVM boundaries",
                scheduledStartEpochMillis = now - HOUR_MILLIS,
                plannedDurationMinutes = 30,
                status = PlannedActivityStatus.MISSED,
                locked = false,
                completedAtEpochMillis = null,
            ),
            PlannedActivity(
                id = PlannedActivityId("desktop-activity-complete"),
                studyPlanId = planId,
                subjectId = subjectId,
                topicId = null,
                materialId = materialId,
                videoResourceId = null,
                type = StudyActivityType.VIDEO,
                title = "Watch desktop layout walkthrough",
                scheduledStartEpochMillis = now - 2 * HOUR_MILLIS,
                plannedDurationMinutes = 35,
                status = PlannedActivityStatus.COMPLETED,
                locked = false,
                completedAtEpochMillis = now - HOUR_MILLIS,
            ),
        )

        private fun sampleSessions(
            planId: StudyPlanId,
            subjectId: SubjectId,
            materialId: StudyMaterialId,
            now: Long,
        ): List<StudySession> = listOf(
            StudySession(
                id = StudySessionId("desktop-session-video"),
                studyPlanId = planId,
                plannedActivityId = PlannedActivityId("desktop-activity-complete"),
                subjectId = subjectId,
                topicId = null,
                materialId = materialId,
                videoResourceId = null,
                type = StudyActivityType.VIDEO,
                status = StudySessionStatus.COMPLETED,
                startedAtEpochMillis = now - 2 * HOUR_MILLIS,
                endedAtEpochMillis = now - HOUR_MILLIS,
                grossDurationSeconds = 35 * 60L,
                pauseDurationSeconds = 0L,
                pagesCompleted = 0,
                questionsAnswered = 0,
                correctAnswers = 0,
                concentrationRating = 4,
                difficultyRating = 2,
                notes = "Watched desktop layout walkthrough",
                targetDurationMinutes = 35,
            ),
        )

        private fun sampleQuestions(
            planId: StudyPlanId,
            subjectId: SubjectId,
            now: Long,
        ): List<Question> = listOf(
            Question(
                id = QuestionId("desktop-question-state"),
                studyPlanId = planId,
                subjectId = subjectId,
                topicId = null,
                type = QuestionType.MULTIPLE_CHOICE,
                statement = "Which API keeps Compose Desktop route selection observable?",
                options = listOf("mutableStateOf", "SharedPreferences", "Room DAO", "Intent extras"),
                correctAnswer = "mutableStateOf",
                explanation = "DesktopAppState owns route selection with Compose runtime mutable state.",
                examBoard = null,
                source = "Desktop bootstrap",
                year = 2026,
                difficulty = 2,
                favorite = true,
                disputed = false,
                tags = listOf("compose", "state"),
                createdAtEpochMillis = now - ONE_WEEK_MILLIS,
                updatedAtEpochMillis = now,
            ),
            Question(
                id = QuestionId("desktop-question-boundary"),
                studyPlanId = planId,
                subjectId = subjectId,
                topicId = null,
                type = QuestionType.SHORT_ANSWER,
                statement = "Why should the desktop app avoid depending on :feature modules for now?",
                options = emptyList(),
                correctAnswer = "They are Android UI boundaries until shared ports exist.",
                explanation = "The desktop shell reuses JVM-safe domain/model code without copying Android screens.",
                examBoard = null,
                source = "DOV03 boundary",
                year = 2026,
                difficulty = 4,
                favorite = false,
                disputed = true,
                tags = listOf("architecture", "boundary"),
                createdAtEpochMillis = now - ONE_WEEK_MILLIS / 2,
                updatedAtEpochMillis = now,
            ),
            Question(
                id = QuestionId("desktop-question-search"),
                studyPlanId = planId,
                subjectId = subjectId,
                topicId = null,
                type = QuestionType.TRUE_FALSE,
                statement = "Desktop search can rank local model data before persistence is implemented.",
                options = listOf("True", "False"),
                correctAnswer = "True",
                explanation = "The DOV07 search adapter indexes the in-memory desktop repositories.",
                examBoard = null,
                source = "DOV07 search",
                year = 2026,
                difficulty = 3,
                favorite = false,
                disputed = false,
                tags = listOf("search", "desktop"),
                createdAtEpochMillis = now - ONE_WEEK_MILLIS / 3,
                updatedAtEpochMillis = now,
            ),
        )

        private fun sampleTests(planId: StudyPlanId, questions: List<Question>, now: Long): List<StudyTest> = listOf(
            StudyTest(
                id = StudyTestId("desktop-test-boundaries"),
                studyPlanId = planId,
                title = "Desktop boundary warm-up",
                mode = TestMode.PRACTICE,
                timeLimitSeconds = null,
                questionCount = questions.size,
                answeredCount = 2,
                correctCount = 1,
                startedAtEpochMillis = now - 5 * HOUR_MILLIS,
                submittedAtEpochMillis = now - 4 * HOUR_MILLIS,
                configurationJson = PracticeAttemptCodec.configurationForQuestions(questions),
            ),
        )

        private fun sampleMockExams(planId: StudyPlanId, questions: List<Question>, now: Long): List<MockExam> = listOf(
            MockExam(
                id = MockExamId("desktop-mock-compose"),
                studyPlanId = planId,
                title = "Compose desktop checkpoint",
                mode = TestMode.EXAM,
                timeLimitSeconds = 30 * 60L,
                questionCount = questions.size,
                answeredCount = questions.size,
                correctCount = 2,
                scorePercent = 67.0,
                negativeMarking = 0.0,
                minimumScorePercent = 60,
                startedAtEpochMillis = now - ONE_WEEK_MILLIS / 5,
                submittedAtEpochMillis = now - ONE_WEEK_MILLIS / 5 + 25 * 60_000L,
                configurationJson = PracticeAttemptCodec.configurationForQuestions(questions),
            ),
        )

        private const val ONE_WEEK_MILLIS: Long = 7L * 24L * 60L * 60L * 1000L
        private const val HOUR_MILLIS: Long = 60L * 60L * 1000L
    }
}
