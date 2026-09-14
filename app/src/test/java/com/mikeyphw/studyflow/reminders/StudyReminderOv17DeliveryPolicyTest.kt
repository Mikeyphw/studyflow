package com.mikeyphw.studyflow.reminders

import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class StudyReminderOv17DeliveryPolicyTest {
    @Test
    fun stableNotificationIdsUseSha256NotJavaHashCode() {
        val occurrence = "remote:plan-alpha:review:123"
        assertNotEquals(occurrence.hashCode() and 0x7fffffff, stableNotificationId(occurrence))
        assertTrue(stableNotificationId(occurrence) > 0)
        assertTrue(stableNotificationId(occurrence) == stableNotificationId(occurrence))
    }
}
