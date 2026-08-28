package com.mikeyphw.studyflow.core.data

import android.content.Context
import com.mikeyphw.studyflow.core.database.StudyFlowDatabase
import com.mikeyphw.studyflow.core.diagnostics.DatabaseHealthLabReport
import com.mikeyphw.studyflow.core.diagnostics.DiagnosticRecorder
import com.mikeyphw.studyflow.core.diagnostics.NoOpDiagnosticRecorder
import com.mikeyphw.studyflow.core.domain.AnnexFileStorageRepository
import com.mikeyphw.studyflow.core.domain.PublicExamRepository
import com.mikeyphw.studyflow.core.domain.SamplePlanProvisioningRepository
import com.mikeyphw.studyflow.core.domain.StudyAnalyticsRepository
import com.mikeyphw.studyflow.core.domain.StudyContentRepository
import com.mikeyphw.studyflow.core.domain.StudyCloudSyncRepository
import com.mikeyphw.studyflow.core.domain.StudyDiscoveryRepository
import com.mikeyphw.studyflow.core.domain.StudyLibraryRepository
import com.mikeyphw.studyflow.core.domain.StudyPlanPortabilityRepository
import com.mikeyphw.studyflow.core.domain.StudyPracticeRepository
import com.mikeyphw.studyflow.core.domain.StudyProgressRepository
import com.mikeyphw.studyflow.core.domain.StudyPlanRepository
import com.mikeyphw.studyflow.core.domain.StudyReviewRepository
import com.mikeyphw.studyflow.core.domain.StudyScheduleRepository
import com.mikeyphw.studyflow.core.domain.StudySessionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Public composition boundary for the data layer.
 *
 * Consumers receive domain-facing repository interfaces and never need the Room database type on
 * their compile classpath. Room remains an implementation detail of :core:data.
 */
class StudyFlowDataLayer internal constructor(
    val studyPlanRepository: StudyPlanRepository,
    val samplePlanProvisioningRepository: SamplePlanProvisioningRepository,
    val studyPlanPortabilityRepository: StudyPlanPortabilityRepository,
    val studyContentRepository: StudyContentRepository,
    val studyDiscoveryRepository: StudyDiscoveryRepository,
    val studyCloudSyncRepository: StudyCloudSyncRepository,
    val studyScheduleRepository: StudyScheduleRepository,
    val studyPracticeRepository: StudyPracticeRepository,
    val studyLibraryRepository: StudyLibraryRepository,
    val studySessionRepository: StudySessionRepository,
    val studyReviewRepository: StudyReviewRepository,
    val studyProgressRepository: StudyProgressRepository,
    val studyAnalyticsRepository: StudyAnalyticsRepository,
    val publicExamRepository: PublicExamRepository,
    val annexFileStorageRepository: AnnexFileStorageRepository,
    private val initializeData: suspend () -> Unit,
    private val inspectDatabase: suspend () -> DatabaseHealthLabReport,
) {
    suspend fun initialize() = initializeData()

    suspend fun inspectDatabaseHealth(): DatabaseHealthLabReport = inspectDatabase()
}

fun createStudyFlowDataLayer(
    context: Context,
    applicationVersion: String,
    clock: () -> Long = System::currentTimeMillis,
    diagnosticRecorder: DiagnosticRecorder = NoOpDiagnosticRecorder,
): StudyFlowDataLayer {
    val database = StudyFlowDatabase.create(context)
    val dataSharingScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val studyPlanRepository = RoomStudyPlanRepository(
        context = context.applicationContext,
        database = database,
        clock = clock,
        diagnosticRecorder = diagnosticRecorder,
    )
    val samplePlanProvisioningRepository = RoomSamplePlanProvisioningRepository(database, clock)
    val studyContentRepository = RoomStudyContentRepository(database, clock)
    val studyDiscoveryRepository = RoomStudyDiscoveryRepository(database)
    val studyCloudSyncRepository = LocalStudyCloudSyncRepository(
        context = context,
        database = database,
        clock = clock,
    )
    val studyScheduleRepository = RoomStudyScheduleRepository(database, diagnosticRecorder)
    val studySessionRepository = RoomStudySessionRepository(database, diagnosticRecorder)
    val studyPracticeRepository = RoomStudyPracticeRepository(database, diagnosticRecorder)
    val studyLibraryRepository = RoomStudyLibraryRepository(database)
    val studyReviewRepository = RoomStudyReviewRepository(database, diagnosticRecorder)
    val studyProgressRepository = RoomStudyProgressRepository(database)
    val studyAnalyticsRepository = RoomStudyAnalyticsRepository(database, dataSharingScope, clock)
    val publicExamRepository = RoomPublicExamRepository(database, clock)
    val annexFileStorageRepository = RoomAnnexFileStorageRepository(
        context = context.applicationContext,
        database = database,
        clock = clock,
    )
    val portabilityRepository = RoomStudyPlanPortabilityRepository(
        context = context,
        database = database,
        clock = clock,
        applicationVersion = applicationVersion,
        diagnosticRecorder = diagnosticRecorder,
    )
    return StudyFlowDataLayer(
        studyPlanRepository = studyPlanRepository,
        samplePlanProvisioningRepository = samplePlanProvisioningRepository,
        studyPlanPortabilityRepository = portabilityRepository,
        studyContentRepository = studyContentRepository,
        studyDiscoveryRepository = studyDiscoveryRepository,
        studyCloudSyncRepository = studyCloudSyncRepository,
        studyScheduleRepository = studyScheduleRepository,
        studyPracticeRepository = studyPracticeRepository,
        studyLibraryRepository = studyLibraryRepository,
        studySessionRepository = studySessionRepository,
        studyReviewRepository = studyReviewRepository,
        studyProgressRepository = studyProgressRepository,
        studyAnalyticsRepository = studyAnalyticsRepository,
        publicExamRepository = publicExamRepository,
        annexFileStorageRepository = annexFileStorageRepository,
        initializeData = {
            studyPlanRepository.seedFoundationSamplesOnce()
            annexFileStorageRepository.migrateOwnedAnnexesToShareableRoot()
        },
        inspectDatabase = { inspectStudyFlowDatabase(database) },
    )
}
