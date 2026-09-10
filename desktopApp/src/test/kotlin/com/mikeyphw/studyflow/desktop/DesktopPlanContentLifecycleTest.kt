package com.mikeyphw.studyflow.desktop

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class DesktopPlanContentLifecycleTest {
    @Test
    fun planSubjectTopicLifecyclePersistsAcrossRestart() = runBlocking {
        val root = Files.createTempDirectory("studyflow-dov09-")
        try {
            val seed = DesktopWorkspaceSnapshot.seed(DesktopStudyFlowSampleData.create())
            val firstPersistence = DesktopWorkspacePersistence.open(root, seed)
            val first = createDesktopStudyFlowServices(workspacePersistence = firstPersistence)
            val controller = first.planContentController

            val plan = controller.createPlan("Real desktop plan")
            val subject = controller.createSubject("Algorithms")
            val topic = controller.createTopic(subject.id, "Graphs")
            controller.archiveTopic(topic.id)

            val reopenedPersistence = DesktopWorkspacePersistence.open(root, seed)
            val reopened = createDesktopStudyFlowServices(workspacePersistence = reopenedPersistence)
            reopened.planContentController.selectPlan(plan.id)
            val state = reopened.planContentController.state()

            assertEquals("Real desktop plan", state.selectedPlan?.name)
            assertEquals("Algorithms", state.subjects.single().name)
            assertEquals("Graphs", state.topics.single().name)
            assertTrue(state.topics.single().archived)
            assertNotNull(reopened.manageStudyContent)
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun deletingSubjectCascadesItsTopicTreeWithoutTouchingOtherSubjects() = runBlocking {
        val services = createDesktopStudyFlowServices()
        val controller = services.planContentController
        val plan = controller.state().selectedPlan!!
        val subject = controller.createSubject("Databases")
        controller.createTopic(subject.id, "Indexes")
        val originalSubjects = services.contentRepository.currentSyllabus(plan.id).subjects.size

        controller.deleteSubject(subject.id)
        val syllabus = services.contentRepository.currentSyllabus(plan.id)
        assertFalse(syllabus.subjects.any { it.id == subject.id })
        assertFalse(syllabus.topics.any { it.subjectId == subject.id })
        assertEquals(originalSubjects - 1, syllabus.subjects.size)
    }
    @Test
    fun archivedPlanRejectsContentMutationAndPermanentDeletePurgesOwnedRows() = runBlocking {
        val root = Files.createTempDirectory("studyflow-dov09-delete-")
        try {
            val seed = DesktopWorkspaceSnapshot.seed(DesktopStudyFlowSampleData.create())
            val services = createDesktopStudyFlowServices(
                workspacePersistence = DesktopWorkspacePersistence.open(root, seed),
            )
            val controller = services.planContentController
            val plan = controller.createPlan("Disposable")
            val subject = controller.createSubject("Owned subject")
            controller.createTopic(subject.id, "Owned topic")

            controller.archiveSelected()
            assertFailsWith<IllegalArgumentException> { controller.createSubject("Must fail") }
            controller.restoreSelected()
            controller.trashSelected()
            controller.deleteSelectedPermanently()

            val reopened = createDesktopStudyFlowServices(
                workspacePersistence = DesktopWorkspacePersistence.open(root, seed),
            )
            assertFalse(reopened.planRepository.currentPlans().any { it.id == plan.id })
            val orphaned = reopened.contentRepository.currentState()
            assertFalse(orphaned.subjects.any { it.studyPlanId == plan.id })
            assertFalse(orphaned.topics.any { it.studyPlanId == plan.id })
        } finally {
            root.toFile().deleteRecursively()
        }
    }

}
