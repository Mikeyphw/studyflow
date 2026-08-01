package com.mikeyphw.studyflow.core.data

import android.content.Context
import com.mikeyphw.studyflow.core.database.StudyFlowDatabase
import com.mikeyphw.studyflow.core.domain.StudyContentRepository
import com.mikeyphw.studyflow.core.domain.StudyLibraryRepository
import com.mikeyphw.studyflow.core.domain.StudyPlanPortabilityRepository
import com.mikeyphw.studyflow.core.domain.StudyPracticeRepository
import com.mikeyphw.studyflow.core.domain.StudyPlanRepository
import com.mikeyphw.studyflow.core.domain.StudyReviewRepository
import com.mikeyphw.studyflow.core.domain.StudyScheduleRepository
import com.mikeyphw.studyflow.core.domain.StudySessionRepository

/**
 * Public composition boundary for the data layer.
 *
 * Consumers receive domain-facing repository interfaces and never need the Room database type on
 * their compile classpath. Room remains an implementation detail of :core:data.
 */
class StudyFlowDataLayer internal constructor(
    val studyPlanRepository: StudyPlanRepository,
    val studyPlanPortabilityRepository: StudyPlanPortabilityRepository,
    val studyContentRepository: StudyContentRepository,
    val studyScheduleRepository: StudyScheduleRepository,
    val studyPracticeRepository: StudyPracticeRepository,
    val studyLibraryRepository: StudyLibraryRepository,
    val studySessionRepository: StudySessionRepository,
    val studyReviewRepository: StudyReviewRepository,
    private val initializeData: suspend () -> Unit,
) {
    suspend fun initialize() = initializeData()
}

fun createStudyFlowDataLayer(
    context: Context,
    applicationVersion: String,
    clock: () -> Long = System::currentTimeMillis,
): StudyFlowDataLayer {
    val database = StudyFlowDatabase.create(context)
    val studyPlanRepository = RoomStudyPlanRepository(database, clock)
    val studyContentRepository = RoomStudyContentRepository(database, clock)
    val studyScheduleRepository = RoomStudyScheduleRepository(database)
    val studySessionRepository = RoomStudySessionRepository(database)
    val studyPracticeRepository = RoomStudyPracticeRepository(database)
    val studyLibraryRepository = RoomStudyLibraryRepository(database)
    val studyReviewRepository = RoomStudyReviewRepository(database)
    val portabilityRepository = RoomStudyPlanPortabilityRepository(
        context = context,
        database = database,
        clock = clock,
        applicationVersion = applicationVersion,
    )
    return StudyFlowDataLayer(
        studyPlanRepository = studyPlanRepository,
        studyPlanPortabilityRepository = portabilityRepository,
        studyContentRepository = studyContentRepository,
        studyScheduleRepository = studyScheduleRepository,
        studyPracticeRepository = studyPracticeRepository,
        studyLibraryRepository = studyLibraryRepository,
        studySessionRepository = studySessionRepository,
        studyReviewRepository = studyReviewRepository,
        initializeData = studyPlanRepository::seedFoundationSamplesOnce,
    )
}
