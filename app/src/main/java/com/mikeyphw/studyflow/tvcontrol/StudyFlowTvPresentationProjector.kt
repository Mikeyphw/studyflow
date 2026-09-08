package com.mikeyphw.studyflow.tvcontrol

import com.mikeyphw.studyflow.core.domain.StudyAnalyticsSnapshot
import com.mikeyphw.studyflow.core.model.ErrorEntry
import com.mikeyphw.studyflow.core.model.Flashcard
import com.mikeyphw.studyflow.core.model.MockExam
import com.mikeyphw.studyflow.core.model.PlannedActivity
import com.mikeyphw.studyflow.core.model.PlannedActivityStatus
import com.mikeyphw.studyflow.core.model.Question
import com.mikeyphw.studyflow.core.model.ReviewItem
import com.mikeyphw.studyflow.core.model.StudyActivityType
import com.mikeyphw.studyflow.core.model.StudyMaterial
import com.mikeyphw.studyflow.core.model.StudyPlan
import com.mikeyphw.studyflow.core.model.StudySchedule
import com.mikeyphw.studyflow.core.model.StudySession
import com.mikeyphw.studyflow.core.model.StudySessionStatus
import com.mikeyphw.studyflow.core.model.StudySyllabus
import com.mikeyphw.studyflow.core.model.StudyTest
import com.mikeyphw.studyflow.core.model.SubjectId
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvActivityPresentation
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvLibraryPresentation
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvLibraryReferencePresentation
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvMockExamPresentation
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvPresentationAvailability
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvPresentationProtocol
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvPresentationSnapshot
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvProgressPresentation
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvQuestionChoicePresentation
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvQuestionPresentation
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvQuestionSetPresentation
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvReviewItemPresentation
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvReviewPresentation
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvScheduleItemPresentation
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvSchedulePresentation
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvStudySessionPresentation
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvSurface
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvTodayPresentation
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvWeakAreaPresentation

/** Canonical phone/data-owner input for one presentation projection. */
data class StudyFlowTvPresentationProjectionInput(
    val snapshotId: String,
    val generatedAtEpochMillis: Long,
    val requestedPlanId: String,
    val plan: StudyPlan?,
    val syllabus: StudySyllabus,
    val schedule: StudySchedule,
    val sessions: List<StudySession>,
    val questions: List<Question>,
    val tests: List<StudyTest>,
    val mockExams: List<MockExam>,
    val reviewItems: List<ReviewItem>,
    val flashcards: List<Flashcard>,
    val errorEntries: List<ErrorEntry>,
    val analytics: StudyAnalyticsSnapshot,
    val materials: List<StudyMaterial>,
)

/**
 * TVOV21 real-data projection boundary.
 *
 * This code lives in the phone application because that process owns the repositories. It maps
 * canonical domain models into the data-only :core:tvcontrol presentation schema. It does not
 * perform transport, pairing, database reads, or TV rendering.
 */
object StudyFlowTvPresentationProjector {
    const val overlay = "TVOV21"
    const val projectsCanonicalDomainModels = true
    const val databaseRowsNeverCrossBoundary = true
    const val arbitraryUiTreesNeverCrossBoundary = true
    const val transportDeferred = true

    fun project(input: StudyFlowTvPresentationProjectionInput): StudyFlowTvPresentationSnapshot {
        val plan = input.plan ?: return StudyFlowTvPresentationSnapshot.waitingForPlan(
            studyPlanId = input.requestedPlanId,
            generatedAtEpochMillis = input.generatedAtEpochMillis,
        )
        val subjectNames = input.syllabus.subjects.associate { it.id to it.name }
        val pendingActivities = input.schedule.plannedActivities
            .filter { it.status == PlannedActivityStatus.PLANNED || it.status == PlannedActivityStatus.IN_PROGRESS }
            .sortedWith(compareBy<PlannedActivity> { it.scheduledStartEpochMillis ?: Long.MAX_VALUE }.thenBy { it.title })
        val nextActivity = pendingActivities.firstOrNull()
        val weeklyGoalMinutes = plan.weeklyGoalHours * 60
        val weeklyProgress = percent(input.analytics.studiedMinutesLastSevenDays, weeklyGoalMinutes)

        return StudyFlowTvPresentationSnapshot(
            snapshotId = input.snapshotId,
            studyPlanId = plan.id.value,
            planTitle = plan.name,
            generatedAtEpochMillis = input.generatedAtEpochMillis,
            availability = StudyFlowTvPresentationAvailability.READY,
            today = StudyFlowTvTodayPresentation(
                planTitle = plan.name,
                nextActivity = nextActivity?.toPresentation(subjectNames),
                dueReviewCount = input.analytics.dueReviewCount,
                availableQuestionCount = input.questions.size,
                weeklyProgressPercent = weeklyProgress,
                mockExamScheduled = pendingActivities.any { it.type == StudyActivityType.MOCK_EXAM },
            ),
            studySession = projectSession(input.sessions, input.schedule.plannedActivities, subjectNames),
            questionSet = projectQuestionSet(plan, input.tests, input.questions, subjectNames),
            mockExam = projectMockExam(input.mockExams),
            review = projectReview(
                plan = plan,
                reviewItems = input.reviewItems,
                flashcards = input.flashcards,
                errors = input.errorEntries,
                questions = input.questions,
                subjectNames = subjectNames,
                nowEpochMillis = input.generatedAtEpochMillis,
            ),
            progress = StudyFlowTvProgressPresentation(
                planTitle = plan.name,
                studiedMinutesLastSevenDays = input.analytics.studiedMinutesLastSevenDays,
                weeklyGoalMinutes = weeklyGoalMinutes,
                weeklyProgressPercent = weeklyProgress,
                questionAccuracyPercent = input.analytics.questionAccuracyPercent,
                answeredQuestions = input.analytics.answeredQuestions,
                dueReviewCount = input.analytics.dueReviewCount,
                contentProgressPercent = input.analytics.contentProgressPercent,
                consistencyScorePercent = input.analytics.consistencyScorePercent,
                weakAreas = input.analytics.weakAreas.mapIndexed { index, area ->
                    StudyFlowTvWeakAreaPresentation(
                        itemId = "weak-area-$index-${area.label.stableSlug()}",
                        label = area.label,
                        progressPercent = area.progressPercent,
                        masteryPercent = area.masteryPercent,
                        priority = area.priority,
                    )
                },
            ),
            schedule = StudyFlowTvSchedulePresentation(
                scheduleId = "schedule-${plan.id.value}",
                title = "${plan.name} schedule",
                items = input.schedule.plannedActivities
                    .sortedWith(compareBy<PlannedActivity> { it.scheduledStartEpochMillis ?: Long.MAX_VALUE }.thenBy { it.title })
                    .take(StudyFlowTvPresentationProtocol.maxTvScheduleItems)
                    .map { activity -> activity.toSchedulePresentation(subjectNames) },
            ),
            library = StudyFlowTvLibraryPresentation(
                libraryId = "library-${plan.id.value}",
                title = "${plan.name} library",
                references = input.materials
                    .asSequence()
                    .filterNot(StudyMaterial::archived)
                    .sortedWith(compareByDescending<StudyMaterial> { it.favorite }.thenByDescending { it.primary }.thenBy { it.title })
                    .take(StudyFlowTvPresentationProtocol.maxTvLibraryReferences)
                    .map { material -> material.toPresentation(subjectNames) }
                    .toList(),
            ),
        )
    }

    private fun projectSession(
        sessions: List<StudySession>,
        activities: List<PlannedActivity>,
        subjectNames: Map<SubjectId, String>,
    ): StudyFlowTvStudySessionPresentation? {
        val session = sessions.firstOrNull { it.status == StudySessionStatus.RUNNING || it.status == StudySessionStatus.PAUSED }
            ?: sessions.maxByOrNull { it.startedAtEpochMillis }
            ?: return null
        val activity = session.plannedActivityId?.let { id -> activities.firstOrNull { it.id == id } }
        val elapsedMinutes = ((session.grossDurationSeconds - session.pauseDurationSeconds).coerceAtLeast(0L) / 60L).toInt()
        val targetMinutes = session.targetDurationMinutes
            ?: activity?.plannedDurationMinutes
            ?: elapsedMinutes.coerceAtLeast(1)
        return StudyFlowTvStudySessionPresentation(
            sessionId = session.id.value,
            title = activity?.title ?: session.type.displayLabel(),
            subjectLabel = subjectLabel(session.subjectId, subjectNames),
            status = session.status.name,
            elapsedMinutes = elapsedMinutes.coerceAtMost(targetMinutes),
            targetMinutes = targetMinutes,
            questionsAnswered = session.questionsAnswered,
            correctAnswers = session.correctAnswers,
        )
    }

    private fun projectQuestionSet(
        plan: StudyPlan,
        tests: List<StudyTest>,
        questions: List<Question>,
        subjectNames: Map<SubjectId, String>,
    ): StudyFlowTvQuestionSetPresentation {
        val openTest = tests.firstOrNull { it.startedAtEpochMillis != null && it.submittedAtEpochMillis == null }
            ?: tests.firstOrNull { it.submittedAtEpochMillis == null }
        val projectedQuestions = questions.asSequence()
            .mapNotNull { question -> question.toPresentation(subjectNames) }
            .take(StudyFlowTvPresentationProtocol.maxTvQuestions)
            .toList()
        return StudyFlowTvQuestionSetPresentation(
            questionSetId = openTest?.id?.value ?: "questions-${plan.id.value}",
            title = openTest?.title ?: "Question practice",
            subjectLabel = projectedQuestions.map { it.subjectLabel }.distinct().singleOrNull() ?: "Mixed subjects",
            questions = projectedQuestions,
        )
    }

    private fun projectMockExam(mockExams: List<MockExam>): StudyFlowTvMockExamPresentation? {
        val mock = mockExams.firstOrNull { it.startedAtEpochMillis != null && it.submittedAtEpochMillis == null }
            ?: mockExams.maxByOrNull { it.startedAtEpochMillis ?: Long.MIN_VALUE }
            ?: return null
        return StudyFlowTvMockExamPresentation(
            mockExamId = mock.id.value,
            title = mock.title,
            status = when {
                mock.submittedAtEpochMillis != null -> "SUBMITTED"
                mock.startedAtEpochMillis != null -> "RUNNING"
                else -> "READY"
            },
            totalMinutes = (mock.timeLimitSeconds / 60L).toInt().coerceAtLeast(1),
            questionCount = mock.questionCount,
            answeredCount = mock.answeredCount,
            correctCount = mock.correctCount,
            scorePercent = mock.scorePercent?.toInt()?.coerceIn(0, 100),
        )
    }

    private fun projectReview(
        plan: StudyPlan,
        reviewItems: List<ReviewItem>,
        flashcards: List<Flashcard>,
        errors: List<ErrorEntry>,
        questions: List<Question>,
        subjectNames: Map<SubjectId, String>,
        nowEpochMillis: Long,
    ): StudyFlowTvReviewPresentation {
        val flashcardsById = flashcards.associateBy { it.id }
        val errorsById = errors.associateBy { it.id }
        val questionsById = questions.associateBy { it.id }
        val projected = reviewItems.asSequence()
            .filter { it.completedAtEpochMillis == null }
            .sortedBy { it.dueAtEpochMillis }
            .take(StudyFlowTvPresentationProtocol.maxTvReviewItems)
            .map { item ->
                val flashcard = item.flashcardId?.let(flashcardsById::get)
                val error = item.errorEntryId?.let(errorsById::get)
                val question = item.questionId?.let(questionsById::get)
                val prompt = flashcard?.front
                    ?: question?.statement
                    ?: error?.explanation?.takeIf(String::isNotBlank)
                    ?: "Scheduled review"
                val explanation = flashcard?.back
                    ?: question?.explanation
                    ?: error?.correctAnswer
                    ?: "Open this review on the phone for the full source context."
                StudyFlowTvReviewItemPresentation(
                    reviewItemId = item.id.value,
                    title = prompt.take(96),
                    subjectLabel = subjectLabel(item.subjectId, subjectNames),
                    prompt = prompt,
                    explanation = explanation,
                    reason = "${item.method.name.lowercase().replace('_', ' ')} · ${item.status.name.lowercase()}",
                    confidencePercent = ((item.easeFactor / 3.0) * 100.0).toInt().coerceIn(0, 100),
                    dueAtEpochMillis = item.dueAtEpochMillis.coerceAtLeast(0L),
                    status = if (item.dueAtEpochMillis <= nowEpochMillis) "DUE" else item.status.name,
                )
            }
            .toList()
        return StudyFlowTvReviewPresentation(
            queueId = "review-${plan.id.value}",
            title = "${plan.name} reviews",
            items = projected,
        )
    }

    private fun PlannedActivity.toPresentation(subjectNames: Map<SubjectId, String>): StudyFlowTvActivityPresentation =
        StudyFlowTvActivityPresentation(
            activityId = id.value,
            title = title,
            subjectLabel = subjectLabel(subjectId, subjectNames),
            activityType = type.name,
            plannedDurationMinutes = plannedDurationMinutes,
            scheduledStartEpochMillis = scheduledStartEpochMillis,
            status = status.name,
            targetSurface = type.targetSurface(),
        )

    private fun PlannedActivity.toSchedulePresentation(subjectNames: Map<SubjectId, String>): StudyFlowTvScheduleItemPresentation =
        StudyFlowTvScheduleItemPresentation(
            activityId = id.value,
            title = title,
            subjectLabel = subjectLabel(subjectId, subjectNames),
            activityType = type.name,
            status = status.name,
            plannedDurationMinutes = plannedDurationMinutes,
            scheduledStartEpochMillis = scheduledStartEpochMillis,
            targetSurface = type.targetSurface(),
        )

    private fun Question.toPresentation(subjectNames: Map<SubjectId, String>): StudyFlowTvQuestionPresentation? {
        if (options.size !in 2..StudyFlowTvPresentationProtocol.maxQuestionChoices) return null
        val choiceTexts = options.map(String::trim)
        val correctIndex = choiceTexts.indexOfFirst { it.equals(correctAnswer.trim(), ignoreCase = true) }
        if (correctIndex < 0) return null
        return StudyFlowTvQuestionPresentation(
            questionId = id.value,
            prompt = statement,
            choices = choiceTexts.mapIndexed { index, label ->
                StudyFlowTvQuestionChoicePresentation(optionId = index.toString(), label = label)
            },
            correctOptionId = correctIndex.toString(),
            explanation = explanation,
            subjectLabel = subjectLabel(subjectId, subjectNames),
            sourceLabel = listOfNotNull(examBoard, organization, year?.toString()).joinToString(" · ").ifBlank { null },
        )
    }

    private fun StudyMaterial.toPresentation(subjectNames: Map<SubjectId, String>): StudyFlowTvLibraryReferencePresentation {
        val source = author?.takeIf(String::isNotBlank) ?: type.name.lowercase().replace('_', ' ')
        val body = description.trim().ifBlank {
            tags.joinToString(", ").ifBlank { "Open on the phone for the complete source." }
        }
        return StudyFlowTvLibraryReferencePresentation(
            referenceId = id.value,
            title = title,
            subjectLabel = subjectLabel(subjectId, subjectNames),
            sourceLabel = source,
            excerpt = body.take(320),
            referenceKind = type.name,
            relatedSurface = StudyFlowTvSurface.LIBRARY,
        )
    }

    private fun StudyActivityType.targetSurface(): StudyFlowTvSurface = when (this) {
        StudyActivityType.QUESTIONS -> StudyFlowTvSurface.QUESTION_SET
        StudyActivityType.REVIEW, StudyActivityType.FLASHCARDS -> StudyFlowTvSurface.REVIEW
        StudyActivityType.MOCK_EXAM -> StudyFlowTvSurface.MOCK_EXAM
        else -> StudyFlowTvSurface.STUDY_SESSION
    }

    private fun StudyActivityType.displayLabel(): String = name.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase)

    private fun subjectLabel(subjectId: SubjectId?, subjectNames: Map<SubjectId, String>): String =
        subjectId?.let(subjectNames::get)?.takeIf(String::isNotBlank) ?: "General"

    private fun percent(numerator: Int, denominator: Int): Int =
        if (denominator <= 0) 0 else ((numerator.toDouble() / denominator.toDouble()) * 100.0).toInt().coerceIn(0, 100)

    private fun String.stableSlug(): String = lowercase()
        .map { character -> if (character.isLetterOrDigit()) character else '-' }
        .joinToString("")
        .replace(Regex("-+"), "-")
        .trim('-')
        .ifBlank { "item" }
}
