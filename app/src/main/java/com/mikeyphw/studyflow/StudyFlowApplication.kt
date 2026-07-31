package com.mikeyphw.studyflow

import android.app.Application
import com.mikeyphw.studyflow.core.data.RoomStudyPlanRepository
import com.mikeyphw.studyflow.core.domain.CreateStudyPlan
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
    val appPreferencesRepository: AppPreferencesRepository
    val createStudyPlan: CreateStudyPlan
}

private class DefaultAppContainer(
    application: Application,
) : AppContainer {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val roomStudyPlanRepository = RoomStudyPlanRepository.create(application)

    override val studyPlanRepository: StudyPlanRepository = roomStudyPlanRepository
    override val appPreferencesRepository: AppPreferencesRepository =
        DataStoreAppPreferencesRepository(application)
    override val createStudyPlan = CreateStudyPlan(studyPlanRepository)

    init {
        applicationScope.launch {
            roomStudyPlanRepository.seedFoundationSamplesOnce()
        }
    }
}
