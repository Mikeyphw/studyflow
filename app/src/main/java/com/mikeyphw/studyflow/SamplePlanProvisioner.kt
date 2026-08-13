package com.mikeyphw.studyflow

import com.mikeyphw.studyflow.core.domain.OnboardingSamplePlanBlueprint
import com.mikeyphw.studyflow.core.domain.OnboardingSamplePlanTemplate
import com.mikeyphw.studyflow.core.domain.OnboardingSampleSubjectKind
import com.mikeyphw.studyflow.core.domain.OnboardingSampleTopicKind
import com.mikeyphw.studyflow.core.domain.SamplePlanProvisioningRepository
import com.mikeyphw.studyflow.core.domain.SamplePlanProvisioningRequest
import com.mikeyphw.studyflow.core.domain.StudyOnboardingSamplePlanWizard
import com.mikeyphw.studyflow.core.i18n.AppPreferencesRepository
import com.mikeyphw.studyflow.core.model.ContentProgressStatus
import com.mikeyphw.studyflow.core.model.StudyPlan
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudyPlanStatus
import com.mikeyphw.studyflow.core.model.Subject
import com.mikeyphw.studyflow.core.model.SubjectId
import com.mikeyphw.studyflow.core.model.Topic
import com.mikeyphw.studyflow.core.model.TopicId
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface SamplePlanProvisioningState {
    data object Idle : SamplePlanProvisioningState
    data class InFlight(val template: OnboardingSamplePlanTemplate) : SamplePlanProvisioningState
    data class Completed(
        val template: OnboardingSamplePlanTemplate,
        val planId: StudyPlanId,
    ) : SamplePlanProvisioningState
    data class Failed(
        val template: OnboardingSamplePlanTemplate,
        val message: String,
    ) : SamplePlanProvisioningState
}

/**
 * Process-scoped onboarding provisioner.
 *
 * Creation is owned by the application scope rather than a Composable scope. Concurrent taps for
 * the same template join the same in-flight job, while the Room repository commits the database
 * graph in one transaction. The default-plan write uses DataStore CAS semantics afterward.
 */
class SamplePlanProvisioner internal constructor(
    private val applicationScope: CoroutineScope,
    private val repository: SamplePlanProvisioningRepository,
    private val preferencesRepository: AppPreferencesRepository,
) {
    private val mutex = Mutex()
    private var inFlight: InFlightProvisioning? = null
    private val _state = MutableStateFlow<SamplePlanProvisioningState>(SamplePlanProvisioningState.Idle)
    val state: StateFlow<SamplePlanProvisioningState> = _state.asStateFlow()

    fun startProvisioning(
        blueprint: OnboardingSamplePlanBlueprint,
        planName: String,
        planDescription: String,
        subjectNames: Map<OnboardingSampleSubjectKind, String>,
        topicNames: Map<OnboardingSampleTopicKind, String>,
    ) {
        applicationScope.launch {
            try {
                provision(
                    blueprint = blueprint,
                    planName = planName,
                    planDescription = planDescription,
                    subjectNames = subjectNames,
                    topicNames = topicNames,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                // The process-scoped state already exposes the failure to the onboarding UI.
            }
        }
    }

    fun consumeCompletion(planId: StudyPlanId) {
        val current = _state.value
        if (current is SamplePlanProvisioningState.Completed && current.planId == planId) {
            _state.value = SamplePlanProvisioningState.Idle
        }
    }

    fun clearFailure() {
        if (_state.value is SamplePlanProvisioningState.Failed) {
            _state.value = SamplePlanProvisioningState.Idle
        }
    }

    suspend fun provision(
        blueprint: OnboardingSamplePlanBlueprint,
        planName: String,
        planDescription: String,
        subjectNames: Map<OnboardingSampleSubjectKind, String>,
        topicNames: Map<OnboardingSampleTopicKind, String>,
    ): StudyPlanId {
        val job = mutex.withLock {
            inFlight?.let { active ->
                require(active.template == blueprint.template) {
                    "Another sample Study Plan is already being provisioned."
                }
                return@withLock active.job
            }

            applicationScope.async {
                _state.value = SamplePlanProvisioningState.InFlight(blueprint.template)
                try {
                    buildAndPersist(
                        blueprint = blueprint,
                        planName = planName,
                        planDescription = planDescription,
                        subjectNames = subjectNames,
                        topicNames = topicNames,
                    ).also { planId ->
                        _state.value = SamplePlanProvisioningState.Completed(blueprint.template, planId)
                    }
                } catch (cancelled: CancellationException) {
                    _state.value = SamplePlanProvisioningState.Idle
                    throw cancelled
                } catch (error: Throwable) {
                    _state.value = SamplePlanProvisioningState.Failed(
                        blueprint.template,
                        error.message ?: "Sample Study Plan provisioning failed.",
                    )
                    throw error
                }
            }.also { created ->
                inFlight = InFlightProvisioning(blueprint.template, created)
                created.invokeOnCompletion {
                    applicationScope.launch {
                        mutex.withLock {
                            if (inFlight?.job === created) {
                                inFlight = null
                            }
                        }
                    }
                }
            }
        }
        return job.await()
    }

    private data class InFlightProvisioning(
        val template: OnboardingSamplePlanTemplate,
        val job: Deferred<StudyPlanId>,
    )

    private suspend fun buildAndPersist(
        blueprint: OnboardingSamplePlanBlueprint,
        planName: String,
        planDescription: String,
        subjectNames: Map<OnboardingSampleSubjectKind, String>,
        topicNames: Map<OnboardingSampleTopicKind, String>,
    ): StudyPlanId {
        val planId = StudyPlanId(UUID.randomUUID().toString())
        val plan = StudyPlan(
            id = planId,
            name = planName,
            description = planDescription,
            type = blueprint.studyType,
            status = StudyPlanStatus.ACTIVE,
            examDateIso = null,
            weeklyGoalHours = blueprint.weeklyGoalHours,
            progressPercent = 0,
            studiedMinutesThisWeek = 0,
            questionsAnswered = 0,
            averageAccuracyPercent = 0,
        )
        val subjectIds = blueprint.subjects.associate { subject ->
            subject.kind to SubjectId(UUID.randomUUID().toString())
        }
        val subjects = blueprint.subjects.mapIndexed { index, subject ->
            Subject(
                id = subjectIds.getValue(subject.kind),
                studyPlanId = planId,
                name = subjectNames.getValue(subject.kind),
                description = "",
                colorArgb = null,
                priority = subject.priority,
                examWeight = subject.examWeight,
                estimatedMinutes = subject.estimatedMinutes,
                progressPercent = 0,
                accuracyPercent = 0,
                status = ContentProgressStatus.NOT_STARTED,
                position = index,
                archived = false,
            )
        }
        val topics = blueprint.subjects.flatMap { subject ->
            subject.topics.mapIndexed { index, topic ->
                Topic(
                    id = TopicId(UUID.randomUUID().toString()),
                    studyPlanId = planId,
                    subjectId = subjectIds.getValue(subject.kind),
                    parentTopicId = null,
                    name = topicNames.getValue(topic.kind),
                    description = "",
                    priority = topic.priority,
                    difficulty = topic.difficulty,
                    examIncidencePercent = null,
                    estimatedMinutes = topic.estimatedMinutes,
                    progressPercent = 0,
                    masteryPercent = 0,
                    status = ContentProgressStatus.NOT_STARTED,
                    position = index,
                    archived = false,
                )
            }
        }
        val request = SamplePlanProvisioningRequest(
            provisioningKey = "onboarding-sample:${blueprint.template.name}",
            plan = plan,
            settings = StudyOnboardingSamplePlanWizard.buildSettings(planId, blueprint),
            subjects = subjects,
            topics = topics,
            scheduleRules = StudyOnboardingSamplePlanWizard.buildScheduleRules(
                planId = planId,
                blueprint = blueprint,
                subjectIdsByKind = subjectIds,
                idFactory = { UUID.randomUUID().toString() },
            ),
        )
        val provisionedPlanId = repository.provision(request)
        preferencesRepository.setDefaultStudyPlanIfAbsent(provisionedPlanId)
        return provisionedPlanId
    }
}
