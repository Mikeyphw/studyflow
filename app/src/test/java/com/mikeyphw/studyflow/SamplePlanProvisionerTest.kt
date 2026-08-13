package com.mikeyphw.studyflow

import com.mikeyphw.studyflow.core.domain.OnboardingSamplePlanBlueprint
import com.mikeyphw.studyflow.core.domain.OnboardingSamplePlanInput
import com.mikeyphw.studyflow.core.domain.OnboardingSamplePlanTemplate
import com.mikeyphw.studyflow.core.domain.OnboardingSampleSubjectKind
import com.mikeyphw.studyflow.core.domain.OnboardingSampleTopicKind
import com.mikeyphw.studyflow.core.domain.SamplePlanProvisioningRepository
import com.mikeyphw.studyflow.core.domain.SamplePlanProvisioningRequest
import com.mikeyphw.studyflow.core.domain.StudyOnboardingSamplePlanWizard
import com.mikeyphw.studyflow.core.i18n.AppPreferencesRepository
import com.mikeyphw.studyflow.core.model.AppLanguage
import com.mikeyphw.studyflow.core.model.AppPreferences
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.ThemeMode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SamplePlanProvisionerTest {
    @Test
    fun concurrentDoubleCreateForSameTemplateJoinsOneProvisioningJob() = runBlocking {
        val repository = BlockingProvisioningRepository()
        val provisioner = SamplePlanProvisioner(this, repository, FakeAppPreferencesRepository())
        val blueprint = blueprint(OnboardingSamplePlanTemplate.FREE_STUDY_HABIT)
        val names = names(blueprint)

        val first = async {
            provisioner.provision(blueprint, "Sample", "", names.subjects, names.topics)
        }
        repository.started.await()
        val second = async {
            provisioner.provision(blueprint, "Sample", "", names.subjects, names.topics)
        }
        yield()

        assertEquals(1, repository.calls)
        repository.release.complete(Unit)
        assertEquals(first.await(), second.await())
        assertEquals(1, repository.calls)
    }

    @Test
    fun differentTemplateCannotRaceAnExistingProvisioningJob() = runBlocking {
        val repository = BlockingProvisioningRepository()
        val provisioner = SamplePlanProvisioner(this, repository, FakeAppPreferencesRepository())
        val firstBlueprint = blueprint(OnboardingSamplePlanTemplate.FREE_STUDY_HABIT)
        val secondBlueprint = blueprint(OnboardingSamplePlanTemplate.CERTIFICATION_BOOTCAMP)
        val firstNames = names(firstBlueprint)
        val secondNames = names(secondBlueprint)

        val first = async {
            provisioner.provision(firstBlueprint, "First", "", firstNames.subjects, firstNames.topics)
        }
        repository.started.await()
        try {
            provisioner.provision(secondBlueprint, "Second", "", secondNames.subjects, secondNames.topics)
            fail("Expected a different sample template to be rejected while provisioning is active")
        } catch (_: IllegalArgumentException) {
            // One process-wide onboarding graph may be provisioned at a time.
        } finally {
            repository.release.complete(Unit)
            first.await()
        }
        assertEquals(1, repository.calls)
    }

    @Test
    fun applicationOwnedProvisioningStateRemainsObservableAcrossUiRecreation() = runBlocking {
        val repository = BlockingProvisioningRepository()
        val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val provisioner = SamplePlanProvisioner(
                applicationScope,
                repository,
                FakeAppPreferencesRepository(),
            )
            val blueprint = blueprint(OnboardingSamplePlanTemplate.FREE_STUDY_HABIT)
            val names = names(blueprint)

            provisioner.startProvisioning(blueprint, "Sample", "", names.subjects, names.topics)
            repository.started.await()
            assertTrue(provisioner.state.value is SamplePlanProvisioningState.InFlight)

            // A recreated Composable observes the same process-owned StateFlow instead of owning
            // the creation coroutine itself.
            repository.release.complete(Unit)
            val completed = withTimeout(1_000L) {
                provisioner.state.filterIsInstance<SamplePlanProvisioningState.Completed>().first()
            }
            assertEquals(blueprint.template, completed.template)
            assertEquals(1, repository.calls)
        } finally {
            applicationScope.cancel()
        }
    }

    private fun blueprint(template: OnboardingSamplePlanTemplate): OnboardingSamplePlanBlueprint =
        StudyOnboardingSamplePlanWizard.build(OnboardingSamplePlanInput(template))

    private fun names(blueprint: OnboardingSamplePlanBlueprint): LocalizedNames = LocalizedNames(
        subjects = blueprint.subjects.associate { subject -> subject.kind to subject.kind.name },
        topics = blueprint.subjects.flatMap { subject -> subject.topics }
            .associate { topic -> topic.kind to topic.kind.name },
    )

    private data class LocalizedNames(
        val subjects: Map<OnboardingSampleSubjectKind, String>,
        val topics: Map<OnboardingSampleTopicKind, String>,
    )
}

private class BlockingProvisioningRepository : SamplePlanProvisioningRepository {
    val started = CompletableDeferred<Unit>()
    val release = CompletableDeferred<Unit>()
    var calls: Int = 0
        private set

    override suspend fun provision(request: SamplePlanProvisioningRequest): StudyPlanId {
        calls++
        started.complete(Unit)
        release.await()
        return request.plan.id
    }
}

private class FakeAppPreferencesRepository : AppPreferencesRepository {
    private val state = MutableStateFlow(AppPreferences())
    override val preferences: Flow<AppPreferences> = state

    override suspend fun setLanguage(language: AppLanguage) {
        state.value = state.value.copy(language = language)
    }

    override suspend fun setThemeMode(themeMode: ThemeMode) {
        state.value = state.value.copy(themeMode = themeMode)
    }

    override suspend fun setDynamicColorEnabled(enabled: Boolean) {
        state.value = state.value.copy(dynamicColorEnabled = enabled)
    }

    override suspend fun setDefaultStudyPlan(planId: StudyPlanId?) {
        state.value = state.value.copy(defaultStudyPlanId = planId)
    }

    override suspend fun clearDefaultStudyPlanIf(planId: StudyPlanId): Boolean {
        if (state.value.defaultStudyPlanId != planId) return false
        state.value = state.value.copy(defaultStudyPlanId = null)
        return true
    }

    override suspend fun setDefaultStudyPlanIfAbsent(planId: StudyPlanId): Boolean {
        if (state.value.defaultStudyPlanId != null) return false
        state.value = state.value.copy(defaultStudyPlanId = planId)
        return true
    }

    override suspend fun recordRecentSearch(query: String) = Unit

    override suspend fun clearRecentSearches() = Unit
}
