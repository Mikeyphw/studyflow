package com.mikeyphw.studyflow

import android.app.Application
import com.mikeyphw.studyflow.core.data.InMemoryStudyPlanRepository
import com.mikeyphw.studyflow.core.domain.CreateStudyPlan
import com.mikeyphw.studyflow.core.domain.StudyPlanRepository
import com.mikeyphw.studyflow.core.i18n.AppPreferencesRepository
import com.mikeyphw.studyflow.core.i18n.DataStoreAppPreferencesRepository

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
    val appPreferencesRepository: AppPreferencesRepository
    val createStudyPlan: CreateStudyPlan
}

private class DefaultAppContainer(
    application: Application,
) : AppContainer {
    override val studyPlanRepository: StudyPlanRepository = InMemoryStudyPlanRepository()
    override val appPreferencesRepository: AppPreferencesRepository =
        DataStoreAppPreferencesRepository(application)
    override val createStudyPlan = CreateStudyPlan(studyPlanRepository)
}
