package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.StudyPlanId
import kotlinx.coroutines.flow.Flow

interface StudyAnalyticsRepository {
    fun observeSnapshot(planId: StudyPlanId): Flow<StudyAnalyticsSnapshot>
}
