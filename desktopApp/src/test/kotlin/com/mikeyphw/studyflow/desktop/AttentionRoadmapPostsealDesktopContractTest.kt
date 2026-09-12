package com.mikeyphw.studyflow.desktop

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains

class AttentionRoadmapPostsealDesktopContractTest {
    @Test
    fun `desktop owns a live inbound notification path without suppressing selected mirrors`() {
        val runtime = String(
            Files.readAllBytes(Path.of("src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopAttentionRuntime.kt")),
            Charsets.UTF_8,
        )
        assertContains(runtime, "setNotificationDeliveryHandler(::receiveRemoteNotification)")
        assertContains(runtime, "fun receiveRemoteNotification(delivery: StudyFlowNotificationDelivery)")
        assertContains(runtime, "StudyFlowNotificationScheduleReducer(")
        assertContains(runtime, "localPeer.deviceId !in route.targetDeviceIds")
        assertContains(runtime, "setNotificationDeliveryHandler(null)")
        assertContains(runtime, "deliveryRoute = route")
        assertContains(runtime, "StudyFlowAttentionDeliverySurface.WIDGET_OR_BADGE")
        assertContains(runtime, "DesktopCompactCompanionAttentionBridge.projectMiniDashboard")
    }
}
