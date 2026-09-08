package com.mikeyphw.studyflow.tvcontrol

import com.mikeyphw.studyflow.core.domain.StudyAnalyticsRepository
import com.mikeyphw.studyflow.core.domain.StudyContentRepository
import com.mikeyphw.studyflow.core.domain.StudyLibraryRepository
import com.mikeyphw.studyflow.core.domain.StudyPlanRepository
import com.mikeyphw.studyflow.core.domain.StudyPracticeRepository
import com.mikeyphw.studyflow.core.domain.StudyReviewRepository
import com.mikeyphw.studyflow.core.domain.StudyScheduleRepository
import com.mikeyphw.studyflow.core.domain.StudySessionRepository
import com.mikeyphw.studyflow.core.model.ErrorEntry
import com.mikeyphw.studyflow.core.model.Flashcard
import com.mikeyphw.studyflow.core.model.MockExam
import com.mikeyphw.studyflow.core.model.Question
import com.mikeyphw.studyflow.core.model.ReviewItem
import com.mikeyphw.studyflow.core.model.StudyMaterial
import com.mikeyphw.studyflow.core.model.StudyPlan
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudySchedule
import com.mikeyphw.studyflow.core.model.StudySession
import com.mikeyphw.studyflow.core.model.StudySyllabus
import com.mikeyphw.studyflow.core.model.StudyTest
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvPresentationSnapshot
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Production source for TV presentation snapshots in the data-owning phone process.
 *
 * This is intentionally transport-free. TVOV22+ can subscribe to this source and deliver the
 * serialized snapshot over an approved paired session without moving Room/repository ownership
 * into the TV APK.
 */
class StudyFlowTvPresentationSource(
    private val planRepository: StudyPlanRepository,
    private val contentRepository: StudyContentRepository,
    private val scheduleRepository: StudyScheduleRepository,
    private val sessionRepository: StudySessionRepository,
    private val practiceRepository: StudyPracticeRepository,
    private val reviewRepository: StudyReviewRepository,
    private val analyticsRepository: StudyAnalyticsRepository,
    private val libraryRepository: StudyLibraryRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) : StudyFlowTvPresentationFeed {
    private val sequence = AtomicLong(0L)

    override fun observe(planId: StudyPlanId): Flow<StudyFlowTvPresentationSnapshot> {
        val primary = combine(
            planRepository.observePlan(planId),
            contentRepository.observeSyllabus(planId),
            scheduleRepository.observeSchedule(planId),
            sessionRepository.observeSessions(planId),
            practiceRepository.observeQuestions(planId),
        ) { plan, syllabus, schedule, sessions, questions ->
            Primary(plan, syllabus, schedule, sessions, questions)
        }
        val reviewAndPractice = combine(
            practiceRepository.observeTests(planId),
            practiceRepository.observeMockExams(planId),
            reviewRepository.observeReviewItems(planId),
            reviewRepository.observeFlashcards(planId),
            reviewRepository.observeErrorEntries(planId),
        ) { tests, mockExams, reviewItems, flashcards, errors ->
            ReviewAndPractice(tests, mockExams, reviewItems, flashcards, errors)
        }
        val analyticsAndLibrary = combine(
            analyticsRepository.observeSnapshot(planId),
            libraryRepository.observeMaterials(planId),
        ) { analytics, materials -> AnalyticsAndLibrary(analytics, materials) }

        return combine(primary, reviewAndPractice, analyticsAndLibrary) { first, second, third ->
            val now = clock()
            StudyFlowTvPresentationProjector.project(
                StudyFlowTvPresentationProjectionInput(
                    snapshotId = "${planId.value}-${sequence.incrementAndGet()}",
                    generatedAtEpochMillis = now,
                    requestedPlanId = planId.value,
                    plan = first.plan,
                    syllabus = first.syllabus,
                    schedule = first.schedule,
                    sessions = first.sessions,
                    questions = first.questions,
                    tests = second.tests,
                    mockExams = second.mockExams,
                    reviewItems = second.reviewItems,
                    flashcards = second.flashcards,
                    errorEntries = second.errors,
                    analytics = third.analytics,
                    materials = third.materials,
                ),
            )
        }
    }

    private data class Primary(
        val plan: StudyPlan?,
        val syllabus: StudySyllabus,
        val schedule: StudySchedule,
        val sessions: List<StudySession>,
        val questions: List<Question>,
    )

    private data class ReviewAndPractice(
        val tests: List<StudyTest>,
        val mockExams: List<MockExam>,
        val reviewItems: List<ReviewItem>,
        val flashcards: List<Flashcard>,
        val errors: List<ErrorEntry>,
    )

    private data class AnalyticsAndLibrary(
        val analytics: com.mikeyphw.studyflow.core.domain.StudyAnalyticsSnapshot,
        val materials: List<StudyMaterial>,
    )
}
