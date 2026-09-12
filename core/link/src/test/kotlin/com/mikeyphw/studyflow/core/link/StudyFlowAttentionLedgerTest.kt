package com.mikeyphw.studyflow.core.link

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class StudyFlowAttentionLedgerTest {
    private val workspace = StudyFlowWorkspaceId("workspace-main")
    private val phone = StudyFlowDeviceId("phone")
    private val desktop = StudyFlowDeviceId("desktop")

    @Test
    fun acceptedDeliveryAndSourceSequenceSurviveLedgerRecreation() {
        val storage = StudyFlowInMemoryAttentionLedgerStorage()
        val occurrence = occurrence("planned:physics:5000")
        val first = StudyFlowAttentionLedger(storage)

        assertEquals(
            StudyFlowAttentionLedgerAdmission.ACCEPTED,
            first.admitInboundDelivery(occurrence, phone, desktop, sequence = 7, deliveredAtEpochMillis = 6_000L),
        )

        val restored = StudyFlowAttentionLedger(storage)
        assertEquals(7L, restored.lastSequenceFor(phone))
        assertEquals(
            StudyFlowAttentionLedgerAdmission.DUPLICATE,
            restored.admitInboundDelivery(occurrence, phone, desktop, sequence = 8, deliveredAtEpochMillis = 7_000L),
        )
        assertEquals(
            StudyFlowAttentionLedgerAdmission.STALE_SEQUENCE,
            restored.admitInboundDelivery(
                occurrence("planned:chemistry:5000"),
                phone,
                desktop,
                sequence = 7,
                deliveredAtEpochMillis = 7_000L,
            ),
        )
    }

    @Test
    fun lifecycleRejectsInvalidTransitionsAndPersistsValidOnes() {
        val storage = StudyFlowInMemoryAttentionLedgerStorage()
        val ledger = StudyFlowAttentionLedger(storage)
        val occurrence = occurrence("planned:biology:9000")
        ledger.upsertScheduled(occurrence, atEpochMillis = 1_000L)

        assertFailsWith<IllegalArgumentException> {
            ledger.transition(occurrence.occurrenceId, StudyFlowAttentionDeliveryState.COMPLETED, 2_000L)
        }

        assertEquals(
            StudyFlowAttentionDeliveryState.DELIVERY_PENDING,
            ledger.transition(
                occurrence.occurrenceId,
                StudyFlowAttentionDeliveryState.DELIVERY_PENDING,
                2_000L,
            ).state,
        )
        assertEquals(
            StudyFlowAttentionDeliveryState.DELIVERED,
            ledger.transition(occurrence.occurrenceId, StudyFlowAttentionDeliveryState.DELIVERED, 3_000L).state,
        )
        assertEquals(
            StudyFlowAttentionDeliveryState.ACKNOWLEDGED,
            ledger.transition(occurrence.occurrenceId, StudyFlowAttentionDeliveryState.ACKNOWLEDGED, 4_000L).state,
        )
        assertEquals(
            StudyFlowAttentionDeliveryState.ACKNOWLEDGED,
            StudyFlowAttentionLedger(storage).entry(occurrence.occurrenceId)?.state,
        )
    }

    @Test
    fun legacyDeliveredAliasesPreventReplayWithoutInventingWorkspaceMappings() {
        val storage = StudyFlowInMemoryAttentionLedgerStorage()
        val ledger = StudyFlowAttentionLedger(storage)
        assertEquals(2, ledger.migrateLegacyDeliveredAliases(listOf("daily:2026-254", "due:5000:review-1:2")))
        assertEquals(0, ledger.migrateLegacyDeliveredAliases(listOf("daily:2026-254")))

        val restored = StudyFlowAttentionLedger(storage)
        assertEquals(
            StudyFlowAttentionLedgerAdmission.DUPLICATE,
            restored.admitInboundDelivery(
                occurrence("daily:2026-254"),
                phone,
                desktop,
                sequence = 1,
                deliveredAtEpochMillis = 10_000L,
            ),
        )
        assertEquals(2, restored.diagnostics().legacyDeliveredAliasCount)
    }

    @Test
    fun invalidPersistedSnapshotFailsClosedToAnEmptyRecoverableLedger() {
        val storage = StudyFlowInMemoryAttentionLedgerStorage("{not-json")
        val ledger = StudyFlowAttentionLedger(storage)
        assertTrue(ledger.diagnostics().loadRecoveredFromInvalidState)
        assertEquals(0, ledger.snapshot().entries.size)
    }

    @Test
    fun deterministicOccurrenceIdUsesSameReadableFormForNormalCrossPlatformIds() {
        assertEquals(
            "schedule:activity-1:5000",
            StudyFlowAttentionOccurrenceId.deterministic("schedule", "activity-1", "5000").value,
        )
        val long = StudyFlowAttentionOccurrenceId.deterministic("schedule", "x".repeat(200), "5000")
        assertEquals("attn-3c4fd677", long.value)
    }

    private fun occurrence(id: String) = StudyFlowAttentionOccurrence(
        occurrenceId = StudyFlowAttentionOccurrenceId.fromLegacy(id),
        workspaceId = workspace,
        category = StudyFlowAttentionCategory.PLANNED_STUDY,
        scheduledAtEpochMillis = 5_000L,
        expiresAtEpochMillis = 50_000L,
        sourceDeviceId = phone,
        targetDeviceIds = listOf(desktop),
    )
}
