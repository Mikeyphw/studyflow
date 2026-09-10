package com.mikeyphw.studyflow.desktop

import com.mikeyphw.studyflow.core.model.PlannedActivityStatus
import com.mikeyphw.studyflow.core.model.StudySessionStatus
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking

class DesktopSessionRuntimePersistenceTest {
    @Test
    fun linkedSessionOwnsActivityTransitionAndRecoversAcrossRestart() {
        runBlocking {
            val root = Files.createTempDirectory("studyflow-dov11-")
            try {
                val seed = DesktopWorkspaceSnapshot.seed(DesktopStudyFlowSampleData.create())
                val first = createDesktopStudyFlowServices(
                    workspacePersistence = DesktopWorkspacePersistence.open(root, seed),
                )
                val planId = first.defaultPlanId!!
                val activity = first.scheduleRepository.currentActivities(planId)
                    .first { it.status == PlannedActivityStatus.PLANNED }

                val started = first.manageStudySession.startFromActivity(
                    com.mikeyphw.studyflow.core.model.StudySessionId("dov11-running"),
                    activity,
                )
                assertEquals(StudySessionStatus.RUNNING, started.status)
                assertEquals(
                    PlannedActivityStatus.IN_PROGRESS,
                    first.scheduleRepository.currentActivities(planId).first { it.id == activity.id }.status,
                )

                val reopened = createDesktopStudyFlowServices(
                    workspacePersistence = DesktopWorkspacePersistence.open(root, seed),
                )
                val recovered = reopened.manageStudySession.activeSession(planId)
                assertNotNull(recovered)
                assertEquals(StudySessionStatus.RUNNING, recovered.status)

                reopened.manageStudySession.pause(planId, recovered.id)
                val resumedServices = createDesktopStudyFlowServices(
                    workspacePersistence = DesktopWorkspacePersistence.open(root, seed),
                )
                val paused = resumedServices.manageStudySession.activeSession(planId)
                assertNotNull(paused)
                assertEquals(StudySessionStatus.PAUSED, paused.status)
                resumedServices.manageStudySession.resume(planId, paused.id)
                resumedServices.manageStudySession.complete(planId, paused.id, 0, 0, 0, null, null, paused.notes)

                assertNull(resumedServices.manageStudySession.activeSession(planId))
                assertEquals(
                    PlannedActivityStatus.COMPLETED,
                    resumedServices.scheduleRepository.currentActivities(planId).first { it.id == activity.id }.status,
                )
            } finally {
                root.toFile().deleteRecursively()
            }
        }
    }
}
