package com.mikeyphw.studyflow.reminders

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class AttentionRoadmapPostsealAndroidContractTest {
    @Test
    fun `android closes remote ingress mirror compact and calendar reachability gaps`() {
        val coordinator = readText(
            Path.of("src/main/java/com/mikeyphw/studyflow/reminders/StudyReminderDeliveryCoordinator.kt"),
        )
        val application = readText(Path.of("src/main/java/com/mikeyphw/studyflow/StudyFlowApplication.kt"))
        val app = readText(Path.of("src/main/java/com/mikeyphw/studyflow/StudyFlowApp.kt"))
        val paths = readText(Path.of("src/main/res/xml/file_provider_paths.xml"))

        assertContains(
            application,
            "setNotificationDeliveryHandler(reminderDeliveryCoordinator::receiveRemoteNotification)",
        )
        assertContains(application, "StudyFlowAttentionWorkspace.forPlanId(plan.id.value)")
        assertFalse(application.contains("deterministic(\"local-plan\", plan.id.value)"))

        assertContains(coordinator, "fun receiveRemoteNotification(delivery: StudyFlowNotificationDelivery)")
        assertContains(coordinator, "StudyFlowNotificationScheduleReducer(")
        assertContains(coordinator, "localPeer.deviceId !in route.targetDeviceIds")
        assertContains(coordinator, "if (!compactOnly && !postNotification(")
        assertContains(coordinator, "localSurface == StudyFlowAttentionDeliverySurface.WIDGET_OR_BADGE")
        assertContains(coordinator, "deliveryRoute = route")
        assertContains(coordinator, "transportedRoute?.deliverSilently == true")
        assertContains(coordinator, "AndroidCompactCompanionStateStore(appContext).update(projection)")
        assertFalse(coordinator.contains("decision.deliverSilently || compactOnly"))

        assertContains(app, "Intent.EXTRA_STREAM")
        assertContains(app, "FileProvider.getUriForFile")
        assertContains(app, "Intent.FLAG_GRANT_READ_URI_PERMISSION")
        assertContains(paths, "studyflow_calendar_exports")
    }

    private fun readText(path: Path): String = String(Files.readAllBytes(path), Charsets.UTF_8)
}
