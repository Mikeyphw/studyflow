package com.mikeyphw.studyflow.core.data

import com.mikeyphw.studyflow.core.database.StudyFlowDatabase
import org.junit.Assert.assertFalse
import com.mikeyphw.studyflow.core.diagnostics.DatabaseHealthLabReport
import com.mikeyphw.studyflow.core.diagnostics.DatabaseHealthMessage
import com.mikeyphw.studyflow.core.diagnostics.DebugLabStatus
import java.util.concurrent.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyFlowDatabaseHealthInspectorContractTest {
    @Test
    fun `declared migration chain reaches the current schema`() {
        assertTrue(migrationPathComplete(1, StudyFlowDatabase.SCHEMA_VERSION))
    }

    @Test
    fun `reverse migration is never reported as complete`() {
        assertFalse(migrationPathComplete(StudyFlowDatabase.SCHEMA_VERSION, 1))
    }

    @Test
    fun `unknown future schema is never reported as reachable`() {
        assertFalse(migrationPathComplete(1, StudyFlowDatabase.SCHEMA_VERSION + 1))
    }


    @Test
    fun `recoverable database inspector failures become unavailable reports`() {
        val report = databaseHealthReportOrUnavailable(expectedSchemaVersion = 7) {
            throw IllegalStateException("private study plan")
        }

        assertEquals(DebugLabStatus.FAILED, report.status)
        assertEquals(DatabaseHealthMessage.DATABASE_UNAVAILABLE, report.message)
        assertEquals(7, report.expectedSchemaVersion)
    }

    @Test(expected = CancellationException::class)
    fun `database inspector preserves cancellation`() {
        databaseHealthReportOrUnavailable(expectedSchemaVersion = 7) {
            throw CancellationException("cancelled")
        }
    }
}
