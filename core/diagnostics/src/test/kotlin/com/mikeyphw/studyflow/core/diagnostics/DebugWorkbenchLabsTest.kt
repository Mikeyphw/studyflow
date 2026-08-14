package com.mikeyphw.studyflow.core.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugWorkbenchLabsTest {
    @Test
    fun `reminder report counts only unsuppressed candidates`() {
        val report = ReminderNotificationLabReport(
            status = DebugLabStatus.PASSED,
            candidates = listOf(
                candidate(ReminderCandidateKind.DUE_REVIEWS, suppressed = false),
                candidate(ReminderCandidateKind.DAILY_SUMMARY, suppressed = true),
            ),
        )

        assertEquals(1, report.activeCandidateCount)
        assertFalse(report.exactAlarmUsed)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `duplicate reminder candidate kinds are rejected`() {
        ReminderNotificationLabReport(
            status = DebugLabStatus.PASSED,
            candidates = listOf(
                candidate(ReminderCandidateKind.DUE_REVIEWS, suppressed = false),
                candidate(ReminderCandidateKind.DUE_REVIEWS, suppressed = true),
            ),
        )
    }

    @Test
    fun `candidate enforces bounded inexact alarm evidence`() {
        val candidate = candidate(ReminderCandidateKind.PLANNED_ACTIVITY, suppressed = false)

        assertTrue(candidate.deliveryWindowMillis > 0L)
        assertTrue(candidate.lateDeliveryGraceMillis >= candidate.deliveryWindowMillis)
        assertTrue(candidate.requestCode >= 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `portability report rejects destructive drills`() {
        DatabasePortabilityLabReport(
            database = DatabaseHealthLabReport(status = DebugLabStatus.PASSED),
            portabilityStatus = DebugLabStatus.PASSED,
            drills = listOf(
                PortabilityDrillDiagnostic(
                    kind = PortabilityDrillKind.RESTORE_DRY_RUN,
                    status = DebugLabStatus.PASSED,
                    destructive = true,
                ),
            ),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `portability report rejects duplicate drill kinds`() {
        DatabasePortabilityLabReport(
            database = DatabaseHealthLabReport(status = DebugLabStatus.PASSED),
            drills = listOf(
                PortabilityDrillDiagnostic(PortabilityDrillKind.WRONG_PASSWORD, DebugLabStatus.PASSED, false),
                PortabilityDrillDiagnostic(PortabilityDrillKind.WRONG_PASSWORD, DebugLabStatus.FAILED, false),
            ),
        )
    }

    @Test
    fun `database health report keeps aggregate evidence only`() {
        val report = DatabaseHealthLabReport(
            status = DebugLabStatus.PASSED,
            schemaVersion = 2,
            expectedSchemaVersion = 2,
            quickCheckPassed = true,
            foreignKeyViolationCount = 0,
            userTableCount = 12,
            totalEntityRows = 120,
            metadataPresent = true,
            migrationPathComplete = true,
        )

        assertEquals(120L, report.totalEntityRows)
        assertTrue(report.metadataPresent)
        assertTrue(report.migrationPathComplete)
    }


    @Test
    fun `study engine report counts statuses and keeps aggregate evidence only`() {
        val report = StudyEngineLabReport(
            status = DebugLabStatus.PASSED,
            inspectedAtEpochMillis = 100L,
            diagnostics = listOf(
                StudyEngineDiagnostic(
                    kind = StudyEngineDiagnosticKind.SCHEDULE_EXPANSION,
                    status = DebugLabStatus.PASSED,
                    primaryCount = 3,
                    secondaryCount = 7,
                ),
                StudyEngineDiagnostic(
                    kind = StudyEngineDiagnosticKind.PRACTICE_DECK,
                    status = DebugLabStatus.WARNING,
                    primaryCount = 0,
                    percent = 0,
                ),
            ),
        )

        assertEquals(1, report.passedCount)
        assertEquals(1, report.warningCount)
        assertEquals(0, report.blockedCount)
    }

    @Test
    fun `empty and not applicable diagnostics stay distinct from warnings`() {
        val report = StudyEngineLabReport(
            status = DebugLabStatus.PASSED,
            diagnostics = listOf(
                StudyEngineDiagnostic(StudyEngineDiagnosticKind.SCHEDULE_EXPANSION, DebugLabStatus.EMPTY),
                StudyEngineDiagnostic(StudyEngineDiagnosticKind.SEARCH_QUERY, DebugLabStatus.NOT_APPLICABLE),
            ),
        )

        assertEquals(1, report.emptyCount)
        assertEquals(1, report.notApplicableCount)
        assertEquals(0, report.warningCount)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `study engine report rejects duplicate diagnostic kinds`() {
        StudyEngineLabReport(
            status = DebugLabStatus.PASSED,
            diagnostics = listOf(
                StudyEngineDiagnostic(StudyEngineDiagnosticKind.SEARCH_QUERY, DebugLabStatus.PASSED),
                StudyEngineDiagnostic(StudyEngineDiagnosticKind.SEARCH_QUERY, DebugLabStatus.WARNING),
            ),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `study engine diagnostics reject unbounded percentages`() {
        StudyEngineDiagnostic(
            kind = StudyEngineDiagnosticKind.ANALYTICS_SNAPSHOT,
            status = DebugLabStatus.PASSED,
            percent = 101,
        )
    }

    private fun candidate(
        kind: ReminderCandidateKind,
        suppressed: Boolean,
    ): ReminderCandidateDiagnostic = ReminderCandidateDiagnostic(
        kind = kind,
        scheduledAtEpochMillis = 1_000L,
        effectiveTriggerAtEpochMillis = 2_000L,
        deliveryWindowMillis = 900_000L,
        lateDeliveryGraceMillis = 7_200_000L,
        suppressedByQuietHours = suppressed,
        pendingIntentIdentityExists = false,
        requestCode = 24_001 + kind.ordinal,
    )
}
