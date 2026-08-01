package com.mikeyphw.studyflow.feature.privacy

import com.mikeyphw.studyflow.core.domain.StudyFlowLocalDataRetentionPolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class PrivacyFeatureContractTest {
    @Test
    fun retentionScreenHasFourTruthfulPolicySections() {
        assertEquals(4, StudyFlowLocalDataRetentionPolicy.current().rules.size)
    }
}
