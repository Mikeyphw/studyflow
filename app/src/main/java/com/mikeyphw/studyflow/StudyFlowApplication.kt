package com.mikeyphw.studyflow

import android.app.Application
import com.mikeyphw.studyflow.core.data.createStudyFlowDataLayer
import com.mikeyphw.studyflow.core.domain.AnnexFileStorageRepository
import com.mikeyphw.studyflow.core.domain.CreateStudyPlan
import com.mikeyphw.studyflow.core.domain.ManageAnnexFileStorage
import com.mikeyphw.studyflow.core.domain.ManageStudyAnalytics
import com.mikeyphw.studyflow.core.domain.ManageStudyContent
import com.mikeyphw.studyflow.core.domain.ManageStudyLibrary
import com.mikeyphw.studyflow.core.domain.ManageStudyPlan
import com.mikeyphw.studyflow.core.domain.ManageStudyPlanPortability
import com.mikeyphw.studyflow.core.domain.ManageStudyPractice
import com.mikeyphw.studyflow.core.domain.ManageStudyProgress
import com.mikeyphw.studyflow.core.domain.ManageStudyReview
import com.mikeyphw.studyflow.core.domain.ManageStudySchedule
import com.mikeyphw.studyflow.core.domain.ManageStudySession
import com.mikeyphw.studyflow.core.domain.StudyAnalyticsRepository
import com.mikeyphw.studyflow.core.domain.StudyContentRepository
import com.mikeyphw.studyflow.core.domain.StudyLibraryRepository
import com.mikeyphw.studyflow.core.domain.StudyPlanRepository
import com.mikeyphw.studyflow.core.domain.StudyPracticeRepository
import com.mikeyphw.studyflow.core.domain.StudyProgressRepository
import com.mikeyphw.studyflow.core.domain.StudyReviewRepository
import com.mikeyphw.studyflow.core.domain.StudyScheduleRepository
import com.mikeyphw.studyflow.core.domain.StudySessionRepository
import com.mikeyphw.studyflow.core.i18n.AppPreferencesRepository
import com.mikeyphw.studyflow.core.i18n.DataStoreAppPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class StudyFlowApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
    }
}

interface AppContainer {
    val studyPlanRepository: StudyPlanRepository
    val studyContentRepository: StudyContentRepository
    val studyScheduleRepository: StudyScheduleRepository
    val studySessionRepository: StudySessionRepository
    val studyPracticeRepository: StudyPracticeRepository
    val studyLibraryRepository: StudyLibraryRepository
    val studyReviewRepository: StudyReviewRepository
    val studyProgressRepository: StudyProgressRepository
    val studyAnalyticsRepository: StudyAnalyticsRepository
    val annexFileStorageRepository: AnnexFileStorageRepository
    val appPreferencesRepository: AppPreferencesRepository
    val createStudyPlan: CreateStudyPlan
    val manageStudyPlan: ManageStudyPlan
    val manageStudyContent: ManageStudyContent
    val manageStudySchedule: ManageStudySchedule
    val manageStudySession: ManageStudySession
    val manageStudyPractice: ManageStudyPractice
    val manageStudyLibrary: ManageStudyLibrary
    val manageStudyReview: ManageStudyReview
    val manageStudyProgress: ManageStudyProgress
    val manageStudyAnalytics: ManageStudyAnalytics
    val manageAnnexFileStorage: ManageAnnexFileStorage
    val manageStudyPlanPortability: ManageStudyPlanPortability
}

private class DefaultAppContainer(
    application: Application,
) : AppContainer {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dataLayer = createStudyFlowDataLayer(
        context = application,
        applicationVersion = BuildConfig.VERSION_NAME,
    )

    override val studyPlanRepository: StudyPlanRepository = dataLayer.studyPlanRepository
    override val studyContentRepository: StudyContentRepository = dataLayer.studyContentRepository
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
    override val createStudyPlan = CreateStudyPlan(studyPlanRepository)
    override val manageStudyPlan = ManageStudyPlan(studyPlanRepository)
    override val manageStudyContent = ManageStudyContent(studyContentRepository)
    override val manageStudySchedule = ManageStudySchedule(studyScheduleRepository)
    override val manageStudySession = ManageStudySession(studySessionRepository)
    override val manageStudyPractice = ManageStudyPractice(studyPracticeRepository)
    override val manageStudyLibrary = ManageStudyLibrary(studyLibraryRepository)
    override val manageStudyReview = ManageStudyReview(studyReviewRepository, studyPracticeRepository)
    override val manageStudyProgress = ManageStudyProgress(studyProgressRepository)
    override val manageStudyAnalytics = ManageStudyAnalytics(studyAnalyticsRepository)
    override val manageAnnexFileStorage = ManageAnnexFileStorage(annexFileStorageRepository)
    override val manageStudyPlanPortability =
        ManageStudyPlanPortability(dataLayer.studyPlanPortabilityRepository)

    init {
        applicationScope.launch {
            dataLayer.initialize()
        }
    }
}
