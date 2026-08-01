package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.StudyPlanId
import kotlinx.coroutines.flow.Flow

class ManageStudyAnalytics(
    private val repository: StudyAnalyticsRepository,
) {
    fun observeSnapshot(planId: StudyPlanId): Flow<StudyAnalyticsSnapshot> = repository.observeSnapshot(planId)
}
