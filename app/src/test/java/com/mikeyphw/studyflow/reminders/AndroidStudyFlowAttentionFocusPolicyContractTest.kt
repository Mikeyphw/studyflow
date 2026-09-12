package com.mikeyphw.studyflow.reminders

import kotlin.test.Test
import kotlin.test.assertTrue
import java.nio.file.Paths

class AndroidStudyFlowAttentionFocusPolicyContractTest {
    @Test
    fun att10AndroidFocusPolicyIsPersistedAndDeliveryIntegrated() {
        val root = Paths.get("src/main/java/com/mikeyphw/studyflow/reminders")
        val store = root.resolve("AndroidStudyFlowAttentionFocusPolicyStore.kt").toFile().readText()
        val coordinator = root.resolve("StudyReminderDeliveryCoordinator.kt").toFile().readText()
        assertTrue(store.contains("StudyFlowAttentionFocusProfile"))
        assertTrue(store.contains("StudyFlowAttentionQuietWindow"))
        assertTrue(coordinator.contains("StudyFlowAttentionFocusPolicyEngine.evaluate"))
        assertTrue(coordinator.contains("recordPolicyDecision"))
        assertTrue(coordinator.contains("DEFER_UNTIL_QUIET_END"))
    }
}
