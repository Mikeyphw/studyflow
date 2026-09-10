package com.mikeyphw.studyflow.desktop

import com.mikeyphw.studyflow.core.model.LanguageReviewOutcome
import com.mikeyphw.studyflow.core.model.StudyContentMode
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class DesktopContentModePersistenceExecutionTest {
    @Test
    fun programmingAndLanguageModeEvidencePersistAcrossRestart() {
        runBlocking {
            val root = Files.createTempDirectory("studyflow-dov17-")
            try {
                val seed = DesktopWorkspaceSnapshot.seed(DesktopStudyFlowSampleData.create())
                val first = createDesktopStudyFlowServices(
                    workspacePersistence = DesktopWorkspacePersistence.open(root, seed),
                )
                first.contentModeController.ensureProgrammingSample()
                val run = first.contentModeController.runFirstProgrammingExercise()
                assertTrue(run.commandLabel == "python3 main.py")

                val reopened = createDesktopStudyFlowServices(
                    workspacePersistence = DesktopWorkspacePersistence.open(root, seed),
                )
                assertEquals(StudyContentMode.PROGRAMMING_COURSE, reopened.contentModeController.state().mode)
                assertTrue(reopened.contentModeController.state().codingExercises.first().lastResultJson != null)

                reopened.contentModeController.ensureLanguageSample()
                val attempt = reopened.contentModeController
                    .reviewFirstLanguageExpression("start a friendly conversation")
                assertEquals(LanguageReviewOutcome.CORRECT, attempt.outcome)

                val finalServices = createDesktopStudyFlowServices(
                    workspacePersistence = DesktopWorkspacePersistence.open(root, seed),
                )
                assertEquals(StudyContentMode.LANGUAGE_DECK, finalServices.contentModeController.state().mode)
                assertEquals(1, finalServices.contentModeController.state().languageAttempts.size)
                assertTrue(Files.isRegularFile(root.resolve("content-modes-v1.properties")))
            } finally {
                root.toFile().deleteRecursively()
            }
        }
    }
}
