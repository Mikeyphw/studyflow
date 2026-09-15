package com.mikeyphw.studyflow.core.diagnostics

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugWorkbenchLabsOv29BehaviorTest {
    @Test(expected = IllegalArgumentException::class)
    fun `database health lab cannot mark current schema corruption as verified`() {
        DatabaseHealthLabReport(
            status = DebugLabStatus.PASSED,
            schemaVersion = 29,
            expectedSchemaVersion = 29,
            quickCheckPassed = true,
            foreignKeyViolationCount = 0,
            logicalIntegrityViolationCount = 1,
            metadataPresent = true,
            migrationPathComplete = true,
            currentSchemaIntegrityVerified = true,
            currentSchemaCorruptionDetected = true,
        )
    }

    @Test
    fun `database health lab distinguishes clean current schema from corrupted current schema`() {
        val clean = DatabaseHealthLabReport(
            status = DebugLabStatus.PASSED,
            schemaVersion = 29,
            expectedSchemaVersion = 29,
            quickCheckPassed = true,
            metadataPresent = true,
            migrationPathComplete = true,
            currentSchemaIntegrityVerified = true,
        )
        val corrupted = clean.copy(
            status = DebugLabStatus.FAILED,
            logicalIntegrityViolationCount = 1,
            currentSchemaIntegrityVerified = false,
            currentSchemaCorruptionDetected = true,
        )

        assertTrue(clean.currentSchemaIntegrityVerified)
        assertFalse(corrupted.currentSchemaIntegrityVerified)
        assertTrue(corrupted.currentSchemaCorruptionDetected)
    }
}
