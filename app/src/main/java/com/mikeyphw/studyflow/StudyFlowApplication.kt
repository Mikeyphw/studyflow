package com.mikeyphw.studyflow

import android.app.Application
import com.mikeyphw.studyflow.core.data.createStudyFlowDataLayer
import com.mikeyphw.studyflow.core.diagnostics.DiagnosticArea
import com.mikeyphw.studyflow.core.diagnostics.DiagnosticRecorderProvider
import com.mikeyphw.studyflow.core.diagnostics.DatabaseHealthLabReport
import com.mikeyphw.studyflow.core.diagnostics.DiagnosticStore
import com.mikeyphw.studyflow.core.domain.AnnexFileStorageRepository
import com.mikeyphw.studyflow.core.domain.CreateStudyPlan
import com.mikeyphw.studyflow.core.domain.ManageAdaptiveReschedule
import com.mikeyphw.studyflow.core.domain.ManageExamReadiness
import com.mikeyphw.studyflow.core.domain.ManageAnnexFileStorage
import com.mikeyphw.studyflow.core.domain.ManageStudyAnalytics
import com.mikeyphw.studyflow.core.domain.ManageStudyCloudSync
import com.mikeyphw.studyflow.core.domain.ManageStudyContent
import com.mikeyphw.studyflow.core.domain.ManageStudyDiscovery
import com.mikeyphw.studyflow.core.domain.ManageStudyLibrary
import com.mikeyphw.studyflow.core.domain.ManageStudyPlan
import com.mikeyphw.studyflow.core.domain.ManageStudyPlanPortability
import com.mikeyphw.studyflow.core.domain.ManageStudyPractice
import com.mikeyphw.studyflow.core.domain.ManageStudyProgress
import com.mikeyphw.studyflow.core.domain.ManageStudyReview
import com.mikeyphw.studyflow.core.domain.ManageStudyResourceViewer
import com.mikeyphw.studyflow.core.domain.ManageStudySchedule
import com.mikeyphw.studyflow.core.domain.ManageStudySession
import com.mikeyphw.studyflow.core.domain.StudyAnalyticsRepository
import com.mikeyphw.studyflow.core.domain.StudyCloudSyncRepository
import com.mikeyphw.studyflow.core.domain.StudyContentRepository
import com.mikeyphw.studyflow.core.domain.StudyDiscoveryRepository
import com.mikeyphw.studyflow.core.domain.StudyLibraryRepository
import com.mikeyphw.studyflow.core.domain.StudyPlanRepository
import com.mikeyphw.studyflow.core.domain.StudyPracticeRepository
import com.mikeyphw.studyflow.core.domain.StudyProgressRepository
import com.mikeyphw.studyflow.core.domain.StudyReviewRepository
import com.mikeyphw.studyflow.core.domain.StudyScheduleRepository
import com.mikeyphw.studyflow.core.domain.StudySessionRepository
import com.mikeyphw.studyflow.core.i18n.AppPreferencesRepository
import com.mikeyphw.studyflow.core.i18n.DataStoreAppPreferencesRepository
import com.mikeyphw.studyflow.reminders.StudyFlowWidgetUpdateCoordinator
import com.mikeyphw.studyflow.reminders.StudyReminderDeliveryCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class StudyFlowApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        val diagnosticRecorder = createStudyFlowDiagnosticRecorder(this)
        container = diagnosticRecorder.recordStep(
            area = DiagnosticArea.APP_LIFECYCLE,
            action = "application-container-create",
            safeDetails = mapOf(
                "version" to BuildConfig.VERSION_NAME,
                "buildType" to BuildConfig.BUILD_TYPE,
            ),
        ) {
            DefaultAppContainer(
                application = this,
                diagnosticRecorder = diagnosticRecorder,
            )
        }
    }
}

interface AppContainer : DiagnosticRecorderProvider {
    val studyPlanRepository: StudyPlanRepository
    val studyContentRepository: StudyContentRepository
    val studyDiscoveryRepository: StudyDiscoveryRepository
    val studyCloudSyncRepository: StudyCloudSyncRepository
    val studyScheduleRepository: StudyScheduleRepository
    val studySessionRepository: StudySessionRepository
    val studyPracticeRepository: StudyPracticeRepository
    val studyLibraryRepository: StudyLibraryRepository
    val studyReviewRepository: StudyReviewRepository
    val studyProgressRepository: StudyProgressRepository
    val studyAnalyticsRepository: StudyAnalyticsRepository
    val annexFileStorageRepository: AnnexFileStorageRepository
    val appPreferencesRepository: AppPreferencesRepository
    val reminderDeliveryCoordinator: StudyReminderDeliveryCoordinator
    val createStudyPlan: CreateStudyPlan
    val manageStudyPlan: ManageStudyPlan
    val manageStudyContent: ManageStudyContent
    val manageStudyDiscovery: ManageStudyDiscovery
    val manageStudyCloudSync: ManageStudyCloudSync
    val manageStudySchedule: ManageStudySchedule
    val manageStudySession: ManageStudySession
    val manageStudyPractice: ManageStudyPractice
    val manageStudyLibrary: ManageStudyLibrary
    val manageStudyReview: ManageStudyReview
    val manageStudyResourceViewer: ManageStudyResourceViewer
    val manageStudyProgress: ManageStudyProgress
    val manageStudyAnalytics: ManageStudyAnalytics
    val manageExamReadiness: ManageExamReadiness
    val manageAdaptiveReschedule: ManageAdaptiveReschedule
    val manageAnnexFileStorage: ManageAnnexFileStorage
    val manageStudyPlanPortability: ManageStudyPlanPortability
    suspend fun inspectDatabaseHealth(): DatabaseHealthLabReport
}

private class DefaultAppContainer(
    application: Application,
    override val diagnosticRecorder: DiagnosticStore,
) : AppContainer {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dataLayer = createStudyFlowDataLayer(
        context = application,
        applicationVersion = BuildConfig.VERSION_NAME,
    )

    override val studyPlanRepository: StudyPlanRepository = dataLayer.studyPlanRepository
    override val studyContentRepository: StudyContentRepository = dataLayer.studyContentRepository
    override val studyDiscoveryRepository: StudyDiscoveryRepository = dataLayer.studyDiscoveryRepository
    override val studyCloudSyncRepository: StudyCloudSyncRepository = dataLayer.studyCloudSyncRepository
    override val studyScheduleRepository: StudyScheduleRepository = dataLayer.studyScheduleRepository
    override val studySessionRepository: StudySessionRepository = dataLayer.studySessionRepository
    override val studyPracticeRepository: StudyPracticeRepository = dataLayer.studyPracticeRepository
    override val studyLibraryRepository: StudyLibraryRepository = dataLayer.studyLibraryRepository
    override val studyReviewRepository: StudyReviewRepository = dataLayer.studyReviewRepository
    override val studyProgressRepository: StudyProgressRepository = dataLayer.studyProgressRepository
    override val studyAnalyticsRepository: StudyAnalyticsRepository = dataLayer.studyAnalyticsRepository
    override val annexFileStorageRepository: AnnexFileStorageRepository = dataLayer.annexFileStorageRepository
    override val appPreferencesRepository: AppPreferencesRepository =
        DataStoreAppPreferencesRepository(application)
    override val reminderDeliveryCoordinator = StudyReminderDeliveryCoordinator(
        context = application,
        studyPlanRepository = studyPlanRepository,
        studyScheduleRepository = studyScheduleRepository,
        studyReviewRepository = studyReviewRepository,
        studyPracticeRepository = studyPracticeRepository,
    )
    override val createStudyPlan = CreateStudyPlan(studyPlanRepository)
    override val manageStudyPlan = ManageStudyPlan(studyPlanRepository)
    override val manageStudyContent = ManageStudyContent(studyContentRepository)
    override val manageStudyDiscovery = ManageStudyDiscovery(studyDiscoveryRepository)
    override val manageStudyCloudSync = ManageStudyCloudSync(studyCloudSyncRepository)
    override val manageStudySchedule = ManageStudySchedule(studyScheduleRepository)
    override val manageStudySession = ManageStudySession(studySessionRepository)
    override val manageStudyPractice = ManageStudyPractice(studyPracticeRepository)
    override val manageStudyLibrary = ManageStudyLibrary(studyLibraryRepository)
    override val manageStudyReview = ManageStudyReview(studyReviewRepository, studyPracticeRepository)
    override val manageStudyResourceViewer = ManageStudyResourceViewer(studyLibraryRepository)
    override val manageStudyProgress = ManageStudyProgress(studyProgressRepository)
    override val manageStudyAnalytics = ManageStudyAnalytics(studyAnalyticsRepository)
    override val manageExamReadiness = ManageExamReadiness(
        analyticsRepository = studyAnalyticsRepository,
        scheduleRepository = studyScheduleRepository,
        practiceRepository = studyPracticeRepository,
    )
    override val manageAdaptiveReschedule = ManageAdaptiveReschedule(
        analyticsRepository = studyAnalyticsRepository,
        scheduleRepository = studyScheduleRepository,
        readinessManager = manageExamReadiness,
    )
    override val manageAnnexFileStorage = ManageAnnexFileStorage(annexFileStorageRepository)
    override val manageStudyPlanPortability =
        ManageStudyPlanPortability(dataLayer.studyPlanPortabilityRepository)

    override suspend fun inspectDatabaseHealth(): DatabaseHealthLabReport =
        dataLayer.inspectDatabaseHealth()

    init {
        diagnosticRecorder.recordStep(
            area = DiagnosticArea.NOTIFICATIONS,
            action = "notification-channel-bootstrap",
        ) {
            reminderDeliveryCoordinator.createNotificationChannels()
        }
        applicationScope.launch {
            diagnosticRecorder.recordSuspendStep(
                area = DiagnosticArea.INITIALIZATION,
                action = "data-layer-initialize",
            ) {
                dataLayer.initialize()
            }
            diagnosticRecorder.recordSuspendStep(
                area = DiagnosticArea.REMINDERS,
                action = "startup-reminder-reconciliation",
            ) {
                reminderDeliveryCoordinator.reconcileAll()
            }
            diagnosticRecorder.recordSuspendStep(
                area = DiagnosticArea.WIDGET,
                action = "startup-widget-refresh",
            ) {
                StudyFlowWidgetUpdateCoordinator.refreshAll(application)
            }
        }
    }
}
