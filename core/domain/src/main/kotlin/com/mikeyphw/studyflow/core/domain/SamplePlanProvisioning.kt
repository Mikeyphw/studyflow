package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.ExamStage
import com.mikeyphw.studyflow.core.model.ScheduleRule
import com.mikeyphw.studyflow.core.model.StudyPlan
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudyGoal
import com.mikeyphw.studyflow.core.model.StudyPlanSettings
import com.mikeyphw.studyflow.core.model.StudyStrategy
import com.mikeyphw.studyflow.core.model.StudyTarget
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
    val examStages: List<ExamStage> = emptyList(),
    val subjects: List<Subject>,
    val topics: List<Topic>,
    val scheduleRules: List<ScheduleRule>,
    val strategy: StudyStrategy? = null,
    val target: StudyTarget? = null,
    val goals: List<StudyGoal> = emptyList(),
) {
    init {
        require(provisioningKey.isNotBlank()) { "Sample provisioning key cannot be blank." }
        val planId = plan.id
        require(settings.studyPlanId == planId) { "Sample settings must belong to the sample Study Plan." }
        require(examStages.all { it.studyPlanId == planId }) {
            "Sample exam stages must belong to the sample Study Plan."
        }
        require(subjects.all { it.studyPlanId == planId }) { "Sample subjects must belong to the sample Study Plan." }
        require(topics.all { it.studyPlanId == planId }) { "Sample topics must belong to the sample Study Plan." }
        require(scheduleRules.all { it.studyPlanId == planId }) {
            "Sample schedule rules must belong to the sample Study Plan."
        }
        require(strategy == null || strategy.studyPlanId == planId) {
            "Sample strategy must belong to the sample Study Plan."
        }
        require(target == null || target.studyPlanId == planId) {
            "Sample target must belong to the sample Study Plan."
        }
        require(examStages.isEmpty() || target != null) {
            "Sample exam stages require a Study Target."
        }
        require(examStages.all { stage -> stage.studyTargetId == target?.id }) {
            "Sample exam stages must belong to the sample Study Target."
        }
        require(goals.all { goal -> goal.studyPlanId == planId }) {
            "Sample goals must belong to the sample Study Plan."
        }
        val stageIds = examStages.map { it.id }.toSet()
        val subjectIds = subjects.map { it.id }.toSet()
        require(subjects.all { subject -> subject.examStageId == null || subject.examStageId in stageIds }) {
            "Every stage-scoped sample subject must reference a sample exam stage."
        }
        require(topics.all { it.subjectId in subjectIds }) { "Every sample topic must reference a sample subject." }
        require(topics.all { topic -> topic.examStageId == null || topic.examStageId in stageIds }) {
            "Every stage-scoped sample topic must reference a sample exam stage."
        }
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

    suspend fun findProvisionedPlan(provisioningKey: String): StudyPlanId?
}
