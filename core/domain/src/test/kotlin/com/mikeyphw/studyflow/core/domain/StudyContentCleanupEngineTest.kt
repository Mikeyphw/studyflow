package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyContentCleanupEngineTest {
    @Test
    fun duplicateQuestionsUseNormalizedContentAndStayPreviewOnly() {
        val first = question(
            id = "q1",
            statement = "  Kirchhoff   current law ",
            answer = "Sum is zero",
            source = "Board A",
            tags = listOf("Circuits", "KCL"),
            createdAt = NOW - 2_000L,
        )
        val duplicate = question(
            id = "q2",
            statement = "kirchhoff current law",
            answer = "sum is zero",
            source = " board a ",
            tags = listOf("kcl", "circuits"),
            createdAt = NOW - 1_000L,
        )
        val differentSource = question(
            id = "q3",
            statement = "kirchhoff current law",
            answer = "sum is zero",
            source = "Board B",
            tags = listOf("kcl", "circuits"),
        )

        val report = StudyContentCleanupEngine.inspect(
            input(questions = listOf(first, duplicate, differentSource)),
        )

        val issue = report.issues.single { it.kind == StudyContentCleanupIssueKind.DUPLICATE_QUESTION }
        assertEquals(1, issue.count)
        assertEquals(listOf("q1", "q2"), issue.affectedIds)
        assertTrue(report.previewActions.all { it.requiresConfirmation })
        assertTrue(report.previewActions.none { it.destructive })
    }

    @Test
    fun detectsOrphansStaleLinksAndBrokenScheduleWithoutMutatingData() {
        val orphan = topic(id = "orphan")
        val staleReview = ReviewItem(
            id = ReviewItemId("review"),
            studyPlanId = PLAN_ID,
            subjectId = SubjectId("missing-subject"),
            topicId = null,
            questionId = null,
            flashcardId = null,
            errorEntryId = null,
            method = ReviewMethod.SPACED_REPETITION,
            status = ReviewStatus.DUE,
            dueAtEpochMillis = NOW - 5_000L,
            intervalDays = 7,
            repetitionCount = 2,
            easeFactor = 2.5,
            completedAtEpochMillis = null,
        )
        val brokenActivity = activity(
            id = "activity",
            topicId = TopicId("missing-topic"),
            status = PlannedActivityStatus.PLANNED,
        )

        val report = StudyContentCleanupEngine.inspect(
            input(
                syllabus = StudySyllabus(PLAN_ID, listOf(subject()), listOf(orphan)),
                reviewItems = listOf(staleReview),
                plannedActivities = listOf(brokenActivity),
            ),
        )

        assertTrue(report.issues.any { it.kind == StudyContentCleanupIssueKind.ORPHAN_TOPIC })
        assertTrue(report.issues.any { it.kind == StudyContentCleanupIssueKind.STALE_REVIEW })
        assertTrue(report.issues.any { it.kind == StudyContentCleanupIssueKind.BROKEN_SCHEDULE_ALIGNMENT })
        assertTrue(report.actionCount >= 2)
        assertTrue(report.healthScorePercent < 100)
        assertFalse(report.previewActions.any(StudyContentCleanupPreviewAction::destructive))
    }


    @Test
    fun scheduledReviewsAgainstArchivedContentAreFlaggedForReview() {
        val archivedTopic = topic(id = "archived-topic").copy(
            status = ContentProgressStatus.ARCHIVED,
            archived = true,
        )
        val staleReview = ReviewItem(
            id = ReviewItemId("archived-review"),
            studyPlanId = PLAN_ID,
            subjectId = null,
            topicId = archivedTopic.id,
            questionId = null,
            flashcardId = null,
            errorEntryId = null,
            method = ReviewMethod.SPACED_REPETITION,
            status = ReviewStatus.SCHEDULED,
            dueAtEpochMillis = NOW + DAY_MILLIS,
            intervalDays = 7,
            repetitionCount = 1,
            easeFactor = 2.5,
            completedAtEpochMillis = null,
        )

        val report = StudyContentCleanupEngine.inspect(
            input(
                syllabus = StudySyllabus(PLAN_ID, listOf(subject()), listOf(archivedTopic)),
                reviewItems = listOf(staleReview),
            ),
        )

        assertTrue(report.issues.any { issue ->
            issue.kind == StudyContentCleanupIssueKind.STALE_REVIEW && "archived-review" in issue.affectedIds
        })
    }

    @Test
    fun detectsTagVariantsAndStaleDetachedResources() {
        val taggedQuestion = question(id = "q1", tags = listOf("Circuits"))
        val staleMaterial = material(
            id = "material",
            archived = true,
            tags = listOf(" circuits "),
            updatedAt = NOW - 100L * DAY_MILLIS,
        )
        val staleVideo = video(
            id = "video",
            completed = true,
            updatedAt = NOW - 100L * DAY_MILLIS,
        )

        val report = StudyContentCleanupEngine.inspect(
            input(
                questions = listOf(taggedQuestion),
                materials = listOf(staleMaterial),
                videos = listOf(staleVideo),
            ),
        )

        val tagIssue = report.issues.single { it.kind == StudyContentCleanupIssueKind.INCONSISTENT_TAG }
        assertEquals("circuits", tagIssue.canonicalValue)
        assertEquals(2, tagIssue.count)
        assertEquals(
            2,
            report.issues.single { it.kind == StudyContentCleanupIssueKind.STALE_RESOURCE }.count,
        )
    }

    private fun input(
        syllabus: StudySyllabus = StudySyllabus(PLAN_ID, listOf(subject()), emptyList()),
        questions: List<Question> = emptyList(),
        materials: List<StudyMaterial> = emptyList(),
        videos: List<VideoResource> = emptyList(),
        reviewItems: List<ReviewItem> = emptyList(),
        plannedActivities: List<PlannedActivity> = emptyList(),
    ) = StudyContentCleanupInput(
        plan = plan(),
        syllabus = syllabus,
        questions = questions,
        materials = materials,
        videos = videos,
        annexes = emptyList(),
        reviewItems = reviewItems,
        flashcards = emptyList(),
        errorEntries = emptyList(),
        plannedActivities = plannedActivities,
        notes = emptyList(),
        nowEpochMillis = NOW,
    )

    private fun plan() = StudyPlan(
        id = PLAN_ID,
        name = "Exam",
        description = "",
        type = StudyType.PUBLIC_EXAMINATION,
        status = StudyPlanStatus.ACTIVE,
        examDateIso = null,
        weeklyGoalHours = 10,
        progressPercent = 20,
        studiedMinutesThisWeek = 60,
        questionsAnswered = 10,
        averageAccuracyPercent = 70,
        createdAtEpochMillis = NOW - DAY_MILLIS,
        updatedAtEpochMillis = NOW,
    )

    private fun subject() = Subject(
        id = SubjectId("subject"),
        studyPlanId = PLAN_ID,
        name = "Circuits",
        description = "",
        colorArgb = null,
        priority = 1,
        examWeight = 1.0,
        estimatedMinutes = 60,
        progressPercent = 20,
        accuracyPercent = 70,
        status = ContentProgressStatus.STUDYING,
        position = 0,
        archived = false,
    )

    private fun topic(id: String) = Topic(
        id = TopicId(id),
        studyPlanId = PLAN_ID,
        subjectId = SubjectId("subject"),
        parentTopicId = null,
        name = id,
        description = "",
        priority = 1,
        difficulty = 2,
        examIncidencePercent = null,
        estimatedMinutes = 60,
        progressPercent = 0,
        masteryPercent = 0,
        status = ContentProgressStatus.NOT_STARTED,
        position = 0,
        archived = false,
    )

    private fun question(
        id: String,
        statement: String = "Question $id",
        answer: String = "Answer",
        source: String? = null,
        tags: List<String> = emptyList(),
        createdAt: Long = NOW,
    ) = Question(
        id = QuestionId(id),
        studyPlanId = PLAN_ID,
        subjectId = SubjectId("subject"),
        topicId = null,
        type = QuestionType.MULTIPLE_CHOICE,
        statement = statement,
        options = listOf(answer, "Distractor"),
        correctAnswer = answer,
        explanation = "",
        examBoard = "Board",
        source = source,
        year = 2026,
        difficulty = 2,
        favorite = false,
        disputed = false,
        tags = tags,
        createdAtEpochMillis = createdAt,
        updatedAtEpochMillis = createdAt,
    )

    private fun material(
        id: String,
        archived: Boolean,
        tags: List<String>,
        updatedAt: Long,
    ) = StudyMaterial(
        id = StudyMaterialId(id),
        studyPlanId = PLAN_ID,
        subjectId = null,
        topicId = null,
        type = StudyMaterialType.PDF,
        title = id,
        description = "",
        sourceUri = null,
        author = null,
        totalUnits = null,
        completedUnits = 0,
        primary = false,
        favorite = false,
        archived = archived,
        tags = tags,
        createdAtEpochMillis = updatedAt,
        updatedAtEpochMillis = updatedAt,
    )

    private fun video(id: String, completed: Boolean, updatedAt: Long) = VideoResource(
        id = VideoResourceId(id),
        studyPlanId = PLAN_ID,
        subjectId = null,
        topicId = null,
        title = id,
        sourceType = VideoSourceType.LOCAL_FILE,
        sourceUri = "file:///$id",
        canonicalSourceId = null,
        thumbnailUri = null,
        durationMillis = null,
        playbackPositionMillis = 0L,
        completed = completed,
        createdAtEpochMillis = updatedAt,
        updatedAtEpochMillis = updatedAt,
    )

    private fun activity(
        id: String,
        topicId: TopicId?,
        status: PlannedActivityStatus,
    ) = PlannedActivity(
        id = PlannedActivityId(id),
        studyPlanId = PLAN_ID,
        subjectId = SubjectId("subject"),
        topicId = topicId,
        materialId = null,
        videoResourceId = null,
        type = StudyActivityType.THEORY,
        title = id,
        scheduledStartEpochMillis = NOW,
        plannedDurationMinutes = 30,
        status = status,
        locked = false,
        completedAtEpochMillis = null,
    )

    private companion object {
        val PLAN_ID = StudyPlanId("plan")
        const val DAY_MILLIS = 86_400_000L
        const val NOW = 200L * DAY_MILLIS
    }
}
