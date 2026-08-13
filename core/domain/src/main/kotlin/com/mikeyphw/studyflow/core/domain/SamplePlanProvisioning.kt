package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.ScheduleRule
import com.mikeyphw.studyflow.core.model.StudyPlan
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudyPlanSettings
import com.mikeyphw.studyflow.core.model.Subject
import com.mikeyphw.studyflow.core.model.Topic

/**
 * One atomic database graph for onboarding/sample-plan provisioning.
 *
 * The request is fully materialized before persistence so Room can either commit the whole graph or
 * leave no partial plan behind after cancellation/process death during the transaction.
 */
data class SamplePlanProvisioningRequest(
    val provisioningKey: String,
    val plan: StudyPlan,
    val settings: StudyPlanSettings,
    val subjects: List<Subject>,
    val topics: List<Topic>,
    val scheduleRules: List<ScheduleRule>,
) {
    init {
        require(provisioningKey.isNotBlank()) { "Sample provisioning key cannot be blank." }
        val planId = plan.id
        require(settings.studyPlanId == planId) { "Sample settings must belong to the sample Study Plan." }
        require(subjects.all { it.studyPlanId == planId }) { "Sample subjects must belong to the sample Study Plan." }
        require(topics.all { it.studyPlanId == planId }) { "Sample topics must belong to the sample Study Plan." }
        require(scheduleRules.all { it.studyPlanId == planId }) {
            "Sample schedule rules must belong to the sample Study Plan."
        }
        val subjectIds = subjects.map { it.id }.toSet()
        require(topics.all { it.subjectId in subjectIds }) { "Every sample topic must reference a sample subject." }
        require(scheduleRules.all { it.subjectId == null || it.subjectId in subjectIds }) {
            "Every subject-scoped sample rule must reference a sample subject."
        }
        val topicIds = topics.map { it.id }.toSet()
        require(topics.all { it.parentTopicId == null || it.parentTopicId in topicIds }) {
            "Every sample topic parent must belong to the same provisioning graph."
        }
    }
}

interface SamplePlanProvisioningRepository {
    suspend fun provision(request: SamplePlanProvisioningRequest): StudyPlanId
}
