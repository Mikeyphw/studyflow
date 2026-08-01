package com.mikeyphw.studyflow.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LocalDataRetentionPolicyTest {
    @Test
    fun policyDoesNotPromiseUnimplementedAutomaticDeletion() {
        val policy = StudyFlowLocalDataRetentionPolicy.current()

        assertFalse(policy.automaticDeletionEnabled)
        assertEquals(LocalRetentionRuleType.entries.toSet(), policy.rules.map { it.type }.toSet())
    }
}
