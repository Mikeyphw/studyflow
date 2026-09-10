package com.mikeyphw.studyflow.desktop

import com.mikeyphw.studyflow.core.model.ThemeMode
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class DesktopWorkspacePersistenceTest {
    @Test
    fun productionWorkspacePersistsPlanSettingsAndPreferencesAcrossRestart() = runBlocking {
        val root = Files.createTempDirectory("studyflow-dov08-")
        try {
            val seed = DesktopWorkspaceSnapshot.seed(DesktopStudyFlowSampleData.create())
            val firstPersistence = DesktopWorkspacePersistence.open(root, seed)
            val first = createDesktopStudyFlowServices(workspacePersistence = firstPersistence)
            val plan = first.planRepository.currentPlans().single()

            first.manageStudyPlan.update(plan.copy(name = "Durable Desktop Plan"))
            first.setTheme(ThemeMode.DARK)

            val reopenedPersistence = DesktopWorkspacePersistence.open(root, seed)
            val reopened = createDesktopStudyFlowServices(workspacePersistence = reopenedPersistence)

            assertEquals("Durable Desktop Plan", reopened.planRepository.currentPlans().single().name)
            assertEquals(ThemeMode.DARK, reopened.preferencesStore.current().themeMode)
            assertEquals(plan.id, reopened.defaultPlanId)
            assertTrue(Files.exists(root.resolve("workspace-v1.properties")))
            assertFalse(Files.exists(root.resolve("workspace-v1.properties.tmp")))
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun ordinaryDesktopFactoryRemainsSideEffectFreeAndInMemory() {
        val services = createDesktopStudyFlowServices()
        assertEquals(null, services.workspacePersistence)
        assertTrue(services.planRepository.currentPlans().isNotEmpty())
    }
}
