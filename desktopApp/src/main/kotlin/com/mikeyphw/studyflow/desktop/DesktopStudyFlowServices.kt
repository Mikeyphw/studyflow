package com.mikeyphw.studyflow.desktop

import com.mikeyphw.studyflow.core.domain.AppShellReadinessAuditor
import com.mikeyphw.studyflow.core.domain.AppShellReadinessInput
import com.mikeyphw.studyflow.core.domain.ManageStudyLibrary
import com.mikeyphw.studyflow.core.domain.ManageStudyPlan
import com.mikeyphw.studyflow.core.domain.StudyLibrarySnapshot
import com.mikeyphw.studyflow.core.model.AppLanguage
import com.mikeyphw.studyflow.core.model.StudyPlan
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.ThemeMode

class DesktopStudyFlowServices(
    val planRepository: InMemoryStudyPlanRepository,
    val libraryRepository: InMemoryStudyLibraryRepository,
    val platformPorts: DesktopPlatformPorts,
) {
    val manageStudyPlan: ManageStudyPlan = ManageStudyPlan(planRepository)
    val manageStudyLibrary: ManageStudyLibrary = ManageStudyLibrary(libraryRepository)
    val defaultPlanId: StudyPlanId? = planRepository.currentPlans().firstOrNull()?.id

    fun createDashboardState(): DesktopDashboardState {
        val activePlan = currentDefaultPlan()
        val librarySnapshot = activePlan?.id?.let(libraryRepository::currentSnapshot) ?: emptyLibrarySnapshot()
        val readiness = AppShellReadinessAuditor.evaluate(
            AppShellReadinessInput(
                language = AppLanguage.SYSTEM_DEFAULT,
                themeMode = ThemeMode.SYSTEM_DEFAULT,
                dynamicColorEnabled = false,
                hasDefaultPlan = activePlan != null,
            ),
        )
        return DesktopDashboardState(
            status = if (activePlan == null) DesktopScreenStatus.EMPTY else DesktopScreenStatus.CONTENT,
            activePlanName = activePlan?.name ?: "No active Study Plan",
            activePlanDescription = activePlan?.description.orEmpty(),
            progressPercent = activePlan?.progressPercent ?: 0,
            weeklyGoalHours = activePlan?.weeklyGoalHours ?: 0,
            studiedMinutesThisWeek = activePlan?.studiedMinutesThisWeek ?: 0,
            questionsAnswered = activePlan?.questionsAnswered ?: 0,
            averageAccuracyPercent = activePlan?.averageAccuracyPercent ?: 0,
            readinessPercent = readiness.readyPercent,
            libraryProgressPercent = librarySnapshot.progressPercent,
            materialCount = librarySnapshot.activeMaterials.size,
            favoriteCount = librarySnapshot.favoriteMaterials.size,
            videoCount = librarySnapshot.videos.size,
            domainManagers = listOf("ManageStudyPlan", "ManageStudyLibrary"),
            boundaryModules = StudyFlowDesktopBoundary.allowedSharedModules,
        )
    }

    fun createLibraryState(): DesktopLibraryScreenState {
        val activePlan = currentDefaultPlan()
        val snapshot = activePlan?.id?.let(libraryRepository::currentSnapshot) ?: emptyLibrarySnapshot()
        val materialRows = snapshot.activeMaterials.map(DesktopLibraryMaterialRow::fromMaterial)
        val videoRows = snapshot.videos.map(DesktopLibraryVideoRow::fromVideo)
        return DesktopLibraryScreenState(
            status = if (activePlan == null || materialRows.isEmpty() && videoRows.isEmpty()) {
                DesktopScreenStatus.EMPTY
            } else {
                DesktopScreenStatus.CONTENT
            },
            planName = activePlan?.name ?: "No active Study Plan",
            materials = materialRows,
            videos = videoRows,
            summary = DesktopLibrarySummary.fromSnapshot(snapshot),
        )
    }

    private fun currentDefaultPlan(): StudyPlan? {
        val plans = planRepository.currentPlans()
        return defaultPlanId?.let { planId -> plans.firstOrNull { plan -> plan.id == planId } }
            ?: plans.firstOrNull()
    }

    private fun emptyLibrarySnapshot(): StudyLibrarySnapshot = StudyLibrarySnapshot(
        materials = emptyList(),
        videos = emptyList(),
        annexes = emptyList(),
    )
}

fun createDesktopStudyFlowServices(
    platformPorts: DesktopPlatformPorts = NoOpDesktopPlatformPorts,
): DesktopStudyFlowServices {
    val sample = DesktopStudyFlowSampleData.create()
    return DesktopStudyFlowServices(
        planRepository = InMemoryStudyPlanRepository(sample.plans),
        libraryRepository = InMemoryStudyLibraryRepository(
            materials = sample.materials,
            videos = sample.videos,
            annexes = sample.annexes,
        ),
        platformPorts = platformPorts,
    )
}

fun InMemoryStudyLibraryRepository.currentSnapshot(planId: StudyPlanId): StudyLibrarySnapshot = StudyLibrarySnapshot(
    materials = currentMaterials(planId),
    videos = currentVideos(planId),
    annexes = currentAnnexes(planId),
)
