package com.mikeyphw.studyflow.desktop

import com.mikeyphw.studyflow.core.model.CodingExerciseStatus
import com.mikeyphw.studyflow.core.model.LanguageReviewOutcome
import com.mikeyphw.studyflow.core.model.StudyContentMode
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
                val programmingState = first.contentModeController.state()
                assertEquals(1, programmingState.codeSnippets.size)
                assertEquals(1, programmingState.miniProjects.size)
                assertTrue(programmingState.termuxRuntimePreference != null)
                val run = first.contentModeController.runFirstProgrammingExercise()
                assertTrue(run.commandLabel == "python3 main.py")
                assertTrue(run.expectedOutputMatched)

                val reopened = createDesktopStudyFlowServices(
                    workspacePersistence = DesktopWorkspacePersistence.open(root, seed),
                )
                val reopenedProgramming = reopened.contentModeController.state()
                assertEquals(StudyContentMode.PROGRAMMING_COURSE, reopenedProgramming.mode)
                assertEquals(1, reopenedProgramming.codeSnippets.size)
                assertEquals(1, reopenedProgramming.miniProjects.size)
                assertTrue(reopenedProgramming.termuxRuntimePreference != null)
                assertTrue(reopenedProgramming.codingExercises.first().lastResultJson != null)
                assertTrue(reopenedProgramming.codingExercises.first().lastResultJson!!.contains("expectedOutputMatched=true"))

                reopened.contentModeController.ensureLanguageSample()
                val languageState = reopened.contentModeController.state()
                assertEquals(1, languageState.idiomCards.size)
                assertEquals(1, languageState.vocabularyCards.size)
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

    @Test
    fun desktopProgrammingExecutionUsesExerciseTemplatesAndExpectedOutput() {
        runBlocking {
            val root = Files.createTempDirectory("studyflow-dov28-")
            try {
                val seed = DesktopWorkspaceSnapshot.seed(DesktopStudyFlowSampleData.create())
                val services = createDesktopStudyFlowServices(
                    workspacePersistence = DesktopWorkspacePersistence.open(root, seed),
                )
                val exercise = services.contentModeController.ensureProgrammingSample()
                services.contentModeRepository.updateExercise(
                    exercise.copy(
                        expectedOutput = "Different expected output",
                        testCommandTemplate = "python3 {mainFile}",
                    ),
                )

                val run = services.contentModeController.runFirstProgrammingExercise()
                val state = services.contentModeController.state()

                assertEquals("python3 main.py", run.commandLabel)
                assertFalse(run.expectedOutputMatched)
                assertEquals(CodingExerciseStatus.FAILED_TESTS, state.codingExercises.first().status)
                assertTrue(state.codingExercises.first().lastResultJson!!.contains("expectedOutputMatched=false"))
            } finally {
                root.toFile().deleteRecursively()
            }
        }
    }

}
