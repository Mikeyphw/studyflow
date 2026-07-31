package com.mikeyphw.studyflow

import android.app.Application
import com.mikeyphw.studyflow.core.data.createStudyFlowDataLayer
import com.mikeyphw.studyflow.core.domain.CreateStudyPlan
import com.mikeyphw.studyflow.core.domain.ManageStudyContent
import com.mikeyphw.studyflow.core.domain.ManageStudyPlan
import com.mikeyphw.studyflow.core.domain.ManageStudyPlanPortability
import com.mikeyphw.studyflow.core.domain.StudyContentRepository
import com.mikeyphw.studyflow.core.domain.StudyPlanRepository
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
    val appPreferencesRepository: AppPreferencesRepository
    val createStudyPlan: CreateStudyPlan
    val manageStudyPlan: ManageStudyPlan
    val manageStudyContent: ManageStudyContent
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
    override val appPreferencesRepository: AppPreferencesRepository =
        DataStoreAppPreferencesRepository(application)
    override val createStudyPlan = CreateStudyPlan(studyPlanRepository)
    override val manageStudyPlan = ManageStudyPlan(studyPlanRepository)
    override val manageStudyContent = ManageStudyContent(studyContentRepository)
    override val manageStudyPlanPortability =
        ManageStudyPlanPortability(dataLayer.studyPlanPortabilityRepository)

    init {
        applicationScope.launch {
            dataLayer.initialize()
        }
    }
}
