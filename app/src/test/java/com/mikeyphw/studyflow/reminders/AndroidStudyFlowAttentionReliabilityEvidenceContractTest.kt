package com.mikeyphw.studyflow.reminders

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains

class AndroidStudyFlowAttentionReliabilityEvidenceContractTest {
    @Test
    fun androidDeliveryRecordsRouteAuditAndProjectsAtt11Evidence() {
        val coordinator = Files.readString(Path.of("src/main/java/com/mikeyphw/studyflow/reminders/StudyReminderDeliveryCoordinator.kt"))
        val app = Files.readString(Path.of("src/main/java/com/mikeyphw/studyflow/StudyFlowApp.kt"))
        val screen = Files.readString(Path.of("../feature/reminders/src/main/java/com/mikeyphw/studyflow/feature/reminders/StudyRemindersScreen.kt"))

        assertContains(coordinator, "attentionReliabilityEvidence")
        assertContains(coordinator, "recordRouteDecision(route)")
        assertContains(coordinator, "StudyFlowAttentionReliabilityAnalyzer.analyze")
        assertContains(app, "StudyFlowAttentionReliabilityExport.toJson")
        assertContains(app, "StudyFlowAttentionReliabilityExport.toCsv")
        assertContains(screen, "ATT11 notification reliability evidence and delivery-audit projection")
        assertContains(screen, "att11_delivery_audit_title")
    }
}
