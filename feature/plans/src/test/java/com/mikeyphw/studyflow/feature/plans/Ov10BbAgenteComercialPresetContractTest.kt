package com.mikeyphw.studyflow.feature.plans

import java.nio.file.Path
import kotlin.io.path.exists
import org.junit.Assert.assertTrue
import org.junit.Test

class Ov10BbAgenteComercialPresetContractTest {
    private fun repoRoot(): Path {
        var current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize()
        repeat(8) {
            if (current.resolve("settings.gradle.kts").exists()) return current
            current = current.parent ?: return@repeat
        }
        error("Could not locate StudyFlow repository root")
    }

    private fun source(relative: String): String = repoRoot().resolve(relative).toFile().readText()

    @Test
    fun `BB preset is a first-class onboarding choice with bilingual labels`() {
        val wizard = source(
            "feature/plans/src/main/java/com/mikeyphw/studyflow/feature/plans/OnboardingSamplePlanWizardScreen.kt",
        )
        val en = source("feature/plans/src/main/res/values/strings.xml")
        val pt = source("feature/plans/src/main/res/values-pt-rBR/strings.xml")
        assertTrue("BB_AGENTE_COMERCIAL_PRE_EDITAL" in wizard)
        assertTrue("BB Agente Comercial — pre-edital" in en)
        assertTrue("BB Agente Comercial — pré-edital" in pt)
        assertTrue("no invented 2026 exam date or topic incidence" in en)
        assertTrue("sem inventar data de prova de 2026 nem incidência por tópico" in pt)
    }

    @Test
    fun `atomic sample provisioning includes exam stages before subjects`() {
        val repository = source(
            "core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/RoomSamplePlanProvisioningRepository.kt",
        )
        val request = source(
            "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/SamplePlanProvisioning.kt",
        )
        assertTrue("val examStages: List<ExamStage>" in request)
        assertTrue("upsertExamStages(request.examStages" in repository)
        assertTrue(repository.indexOf("upsertExamStages") < repository.indexOf("upsertSubjects"))
    }
}
