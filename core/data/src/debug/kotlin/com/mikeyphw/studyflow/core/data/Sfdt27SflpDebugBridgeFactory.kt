package com.mikeyphw.studyflow.core.data

import com.mikeyphw.studyflow.core.database.StudyFlowDatabase
import com.mikeyphw.studyflow.core.link.StudyFlowProductStateBridge

/**
 * Debug-only test seam: the SAME SFLP production replay implementation,
 * bound to a caller-owned, disposable Room database. Not packaged in release.
 * Never constructs a second database handle or writes to studyflow.db.
 */
fun createSfdt27DebugSflpReplayBridge(database: StudyFlowDatabase): StudyFlowProductStateBridge =
    RoomStudyFlowSflpProductStateBridge(database)
