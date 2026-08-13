package com.mikeyphw.studyflow.core.data

import kotlinx.coroutines.sync.Mutex

/**
 * Process-wide serialization point for StudyFlow-owned annex/snapshot file mutations.
 *
 * Room transactions protect database rows, but orphan cleanup, duplication, import, migration,
 * and plan-level deletion also mutate app-owned bytes. They share this mutex so a pre-commit file
 * cannot be mistaken for an orphan and a delete cannot race another physical-storage operation.
 */
internal object PhysicalStorageMutationCoordinator {
    val mutex = Mutex()
}
