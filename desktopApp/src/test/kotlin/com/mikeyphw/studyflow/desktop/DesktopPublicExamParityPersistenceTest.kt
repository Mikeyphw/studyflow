package com.mikeyphw.studyflow.desktop

import com.mikeyphw.studyflow.core.model.StudySessionStatus
import com.mikeyphw.studyflow.core.model.StudyType
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class DesktopPublicExamParityPersistenceTest {
    @Test
    fun editalLegislationCycleAndTerminalAdvancePersistAcrossRestart() {
        runBlocking {
            val root = Files.createTempDirectory("studyflow-dov18-")
            try {
                val seed = DesktopWorkspaceSnapshot.seed(DesktopStudyFlowSampleData.create())
                val first = createDesktopStudyFlowServices(
                    workspacePersistence = DesktopWorkspacePersistence.open(root, seed),
                )
                val target = first.publicExamController.saveTarget(
                    organization = "Banco do Brasil",
                    role = "Agente Comercial",
                    examBoard = "Cesgranrio",
                    noticeUrl = "",
                    examDateIso = "2027-03-14",
                )
                assertEquals(StudyType.PUBLIC_EXAMINATION, first.planRepository.currentPlans().first().type)
                assertEquals("Cesgranrio", target.examBoard)

                first.publicExamController.applyEdital(
                    displayName = "edital-2027.txt",
                    rawText = buildString {
                        appendLine("Disciplina: Língua Portuguesa")
                        appendLine("1 Interpretação de textos")
                        appendLine("Disciplina: Matemática")
                        append("1 Porcentagem")
                    },
                )
                val legislation = first.publicExamController.importLegislation(
                    title = "Lei de referência",
                    rawText = "Art. 1 Direitos fundamentais\n§ 1 Aplicação imediata",
                )
                assertEquals(2, legislation.provisions.size)
                first.publicExamController.toggleFirstProvisionRead()

                val cycle = first.publicExamController.buildCycle(30)
                assertTrue(cycle.items.size >= 2)
                val firstItem = cycle.cycle.currentItemId
                val session = assertNotNull(first.publicExamController.startCurrentCycleItem())
                first.manageStudySession.complete(
                    planId = session.studyPlanId,
                    sessionId = session.id,
                    pagesCompleted = 0,
                    questionsAnswered = 0,
                    correctAnswers = 0,
                    concentrationRating = 4,
                    difficultyRating = 3,
                    notes = "cycle complete",
                )
                assertEquals(
                    StudySessionStatus.COMPLETED,
                    first.sessionRepository.currentSessions(session.studyPlanId).first().status,
                )
                assertTrue(first.publicExamRepository.current().sessionBindings.isEmpty())
                assertTrue(first.publicExamRepository.current().studyCycles.first().currentItemId != firstItem)

                val reopened = createDesktopStudyFlowServices(
                    workspacePersistence = DesktopWorkspacePersistence.open(root, seed),
                )
                val state = reopened.publicExamController.state()
                assertEquals("Banco do Brasil", state.target?.organization)
                assertEquals(1, state.revisions.size)
                assertEquals(1, state.legislation?.documents?.size)
                assertTrue(state.legislation?.provisions?.first()?.read == true)
                assertTrue(state.cycle?.items?.size ?: 0 >= 2)
                assertTrue(Files.isRegularFile(root.resolve("public-exam-v1.properties")))
            } finally {
                root.toFile().deleteRecursively()
            }
        }
    }
}
