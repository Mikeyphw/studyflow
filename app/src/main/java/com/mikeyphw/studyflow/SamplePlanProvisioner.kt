package com.mikeyphw.studyflow

import com.mikeyphw.studyflow.core.domain.OnboardingSamplePlanBlueprint
import com.mikeyphw.studyflow.core.domain.OnboardingSampleExamStageKind
import com.mikeyphw.studyflow.core.domain.OnboardingSamplePlanTemplate
import com.mikeyphw.studyflow.core.domain.OnboardingSampleSubjectKind
import com.mikeyphw.studyflow.core.domain.OnboardingSampleTopicKind
import com.mikeyphw.studyflow.core.domain.PublicExamGoalPreset
import com.mikeyphw.studyflow.core.domain.PublicExamEditalImportChoice
import com.mikeyphw.studyflow.core.domain.PublicExamOnboardingBuilder
import com.mikeyphw.studyflow.core.domain.SamplePlanProvisioningRepository
import com.mikeyphw.studyflow.core.domain.SamplePlanProvisioningRequest
import com.mikeyphw.studyflow.core.domain.StudyOnboardingSamplePlanWizard
import com.mikeyphw.studyflow.core.i18n.AppPreferencesRepository
import com.mikeyphw.studyflow.core.model.ContentProgressStatus
import com.mikeyphw.studyflow.core.model.ExamStageId
import com.mikeyphw.studyflow.core.model.StudyPlan
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudyPlanStatus
import com.mikeyphw.studyflow.core.model.Subject
import com.mikeyphw.studyflow.core.model.SubjectId
import com.mikeyphw.studyflow.core.model.Topic
import com.mikeyphw.studyflow.core.model.TopicId
import java.time.LocalDate
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

private fun defaultGoalNames(): Map<PublicExamGoalPreset, String> =
    PublicExamGoalPreset.entries.associateWith { preset -> preset.name }

private fun defaultExamStageNames(): Map<OnboardingSampleExamStageKind, String> =
    OnboardingSampleExamStageKind.entries.associateWith { stage -> stage.name }

sealed interface SamplePlanProvisioningState {
    data object Idle : SamplePlanProvisioningState
    data class InFlight(val template: OnboardingSamplePlanTemplate) : SamplePlanProvisioningState
    data class Completed(
        val template: OnboardingSamplePlanTemplate,
        val planId: StudyPlanId,
        val editalImportChoice: PublicExamEditalImportChoice = PublicExamEditalImportChoice.LATER,
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
    private val journal: SamplePlanProvisioningJournal = InMemorySamplePlanProvisioningJournal(),
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
        goalNames: Map<PublicExamGoalPreset, String> = defaultGoalNames(),
        examStageNames: Map<OnboardingSampleExamStageKind, String> = defaultExamStageNames(),
    ) {
        applicationScope.launch {
            try {
                provision(
                    blueprint = blueprint,
                    planName = planName,
                    planDescription = planDescription,
                    subjectNames = subjectNames,
                    topicNames = topicNames,
                    goalNames = goalNames,
                    examStageNames = examStageNames,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // The process-scoped state already exposes the recoverable failure to the onboarding UI.
            }
        }
    }

    fun consumeCompletion(planId: StudyPlanId) {
        val current = _state.value
        if (current is SamplePlanProvisioningState.Completed && current.planId == planId) {
            journal.consume(current.template, current.planId)
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
        goalNames: Map<PublicExamGoalPreset, String> = defaultGoalNames(),
        examStageNames: Map<OnboardingSampleExamStageKind, String> = defaultExamStageNames(),
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
                        goalNames = goalNames,
                        examStageNames = examStageNames,
                    ).also { planId ->
                        _state.value = SamplePlanProvisioningState.Completed(
                            blueprint.template,
                            planId,
                            blueprint.publicExam?.editalImportChoice ?: PublicExamEditalImportChoice.LATER,
                        )
                    }
                } catch (cancelled: CancellationException) {
                    _state.value = SamplePlanProvisioningState.Idle
                    throw cancelled
                } catch (error: Exception) {
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

    suspend fun recoverPendingCompletion() {
        val pending = journal.pending() ?: return
        val provisioningKey = "onboarding-sample:${pending.template.name}"
        val committedPlanId = repository.findProvisionedPlan(provisioningKey)
        if (committedPlanId == null) {
            journal.clearPending()
            if (_state.value is SamplePlanProvisioningState.InFlight) {
                _state.value = SamplePlanProvisioningState.Idle
            }
            return
        }
        journal.updateCommittedPlan(pending.template, committedPlanId)
        preferencesRepository.setDefaultStudyPlanIfAbsent(committedPlanId)
        _state.value = SamplePlanProvisioningState.Completed(
            pending.template, committedPlanId, pending.editalImportChoice,
        )
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
        goalNames: Map<PublicExamGoalPreset, String>,
        examStageNames: Map<OnboardingSampleExamStageKind, String>,
    ): StudyPlanId {
        val planId = StudyPlanId(UUID.randomUUID().toString())
        val editalImportChoice = blueprint.publicExam?.editalImportChoice ?: PublicExamEditalImportChoice.LATER
        journal.begin(blueprint.template, planId, editalImportChoice)
        val plan = StudyPlan(
            id = planId,
            name = planName,
            description = planDescription,
            type = blueprint.studyType,
            status = StudyPlanStatus.ACTIVE,
            examDateIso = blueprint.publicExam?.examDateIso,
            weeklyGoalHours = blueprint.weeklyGoalHours,
            progressPercent = 0,
            studiedMinutesThisWeek = 0,
            questionsAnswered = 0,
            averageAccuracyPercent = 0,
        )
        val target = blueprint.publicExam?.let { profile ->
            PublicExamOnboardingBuilder.buildTarget(
                planId = planId,
                blueprint = profile,
                idFactory = { UUID.randomUUID().toString() },
                nowEpochMillis = System.currentTimeMillis(),
            )
        }
        val (examStages, stageIds) = if (target != null && blueprint.examStages.isNotEmpty()) {
            StudyOnboardingSamplePlanWizard.buildExamStages(
                planId = planId,
                targetId = target.id,
                blueprint = blueprint,
                stageNames = examStageNames,
                idFactory = { UUID.randomUUID().toString() },
            )
        } else {
            emptyList<com.mikeyphw.studyflow.core.model.ExamStage>() to
                emptyMap<OnboardingSampleExamStageKind, ExamStageId>()
        }
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
                examStageId = subject.examStageKind?.let(stageIds::get),
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
            examStages = examStages,
            subjects = subjects,
            topics = topics,
            scheduleRules = StudyOnboardingSamplePlanWizard.buildScheduleRules(
                planId = planId,
                blueprint = blueprint,
                subjectIdsByKind = subjectIds,
                idFactory = { UUID.randomUUID().toString() },
            ),
            strategy = blueprint.publicExam?.let { profile ->
                PublicExamOnboardingBuilder.buildStrategy(
                    planId = planId,
                    blueprint = profile,
                    idFactory = { UUID.randomUUID().toString() },
                )
            },
            target = target,
            goals = blueprint.publicExam?.let { profile ->
                PublicExamOnboardingBuilder.buildGoals(
                    planId = planId,
                    blueprint = profile,
                    goalTitles = goalNames,
                    startsOnIso = LocalDate.now().toString(),
                    idFactory = { UUID.randomUUID().toString() },
                    accuracySubjectId = subjects.firstOrNull()?.id,
                    coverageTopicCount = topics.count { !it.archived },
                )
            }.orEmpty(),
        )
        val provisionedPlanId = repository.provision(request)
        journal.updateCommittedPlan(blueprint.template, provisionedPlanId, editalImportChoice)
        preferencesRepository.setDefaultStudyPlanIfAbsent(provisionedPlanId)
        return provisionedPlanId
    }
}
