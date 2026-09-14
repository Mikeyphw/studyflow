package com.mikeyphw.studyflow.core.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mikeyphw.studyflow.core.database.StudyFlowDatabase
import com.mikeyphw.studyflow.core.link.StudyFlowDeviceId
import com.mikeyphw.studyflow.core.link.StudyFlowEntityRevision
import com.mikeyphw.studyflow.core.link.StudyFlowProductStateContract
import com.mikeyphw.studyflow.core.link.StudyFlowDomainOperation
import org.junit.Assert.assertTrue
import org.junit.Test

class SflpProductStateBridgeOv08Test {
    @Test fun ordinaryRoomWriteBecomesJournalMutationAndRemoteProjectionIsIdempotent() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(StudyFlowDatabase.DATABASE_NAME)
        val db = StudyFlowDatabase.create(context)
        try {
            val bridge = RoomStudyFlowSflpProductStateBridge(db)
            val workspace = StudyFlowProductStateContract.WorkspaceId
            bridge.captureLocalMutations(workspace) // baseline
            db.openHelper.writableDatabase.execSQL(
                "INSERT OR REPLACE INTO database_metadata (`key`, value) VALUES (?, ?)",
                arrayOf("ov08_probe", "from-a"),
            )
            val mutations = bridge.captureLocalMutations(workspace)
            assertTrue(mutations.any { it.operationType == StudyFlowProductStateContract.RowUpsert })
            val m = mutations.first()
            val remote = StudyFlowDomainOperation(
                operationId = m.operationId,
                workspaceId = workspace,
                originDeviceId = StudyFlowDeviceId("device-a"),
                originSequence = 1,
                entityId = m.entityId,
                entityRevision = StudyFlowEntityRevision(1),
                logicalTimestamp = 1,
                operationType = m.operationType,
                occurredAtEpochMillis = 1,
                payload = m.payload,
            )
            assertTrue(bridge.projectResolved(workspace, listOf(remote)) >= 1)
            assertTrue(bridge.captureLocalMutations(workspace).isEmpty())
        } finally { db.close(); context.deleteDatabase(StudyFlowDatabase.DATABASE_NAME) }
    }
}
