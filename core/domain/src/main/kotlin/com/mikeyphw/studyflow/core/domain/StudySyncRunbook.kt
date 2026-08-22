package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.StudyFlowSyncProviderIds
import com.mikeyphw.studyflow.core.model.StudySyncBackgroundMode
import com.mikeyphw.studyflow.core.model.StudySyncBackgroundSyncPlanner
import com.mikeyphw.studyflow.core.model.StudySyncChangeSummary
import com.mikeyphw.studyflow.core.model.StudySyncMergePlan
import com.mikeyphw.studyflow.core.model.StudySyncState

enum class StudySyncRunIntent {
    MANUAL_UPLOAD,
    MANUAL_REMOTE_CHECK,
    RESTORE_PREVIEW,
    BACKGROUND_UPLOAD_AFTER_CHANGES,
    BACKGROUND_CHECK_DAILY,
    BACKGROUND_FULL_AUTOMATIC,
}

enum class StudySyncRunStep {
    INSPECT_LOCAL_CHANGES,
    BUILD_ENCRYPTED_BUNDLE,
    UPLOAD_ENCRYPTED_BUNDLE,
    LIST_REMOTE_CHECKPOINTS,
    DOWNLOAD_REMOTE_BUNDLE,
    VERIFY_BUNDLE_INTEGRITY,
    DRY_RUN_RESTORE,
    BUILD_MERGE_PLAN,
    REQUIRE_CONFLICT_REVIEW,
    RECORD_LEDGER_EVENT,
}

enum class StudySyncRunBlocker {
    SYNC_DISABLED,
    PROVIDER_NOT_CONFIGURED,
    ENCRYPTION_DISABLED,
    NO_LOCAL_DATA,
    NO_REMOTE_CHECKPOINT,
    BACKGROUND_MODE_DISABLED,
    BACKGROUND_MODE_MISMATCH,
    CONFLICT_REVIEW_REQUIRED,
}

data class StudySyncRunPlan(
    val intent: StudySyncRunIntent,
    val steps: List<StudySyncRunStep>,
    val blockers: List<StudySyncRunBlocker> = emptyList(),
    val backgroundMode: StudySyncBackgroundMode? = null,
) {
    init {
        require(steps.isNotEmpty() || blockers.isNotEmpty()) { "Sync run plan cannot be empty." }
    }

    val canStart: Boolean = blockers.isEmpty()
    val requiresNetwork: Boolean = steps.any { step ->
        step in setOf(
            StudySyncRunStep.UPLOAD_ENCRYPTED_BUNDLE,
            StudySyncRunStep.LIST_REMOTE_CHECKPOINTS,
            StudySyncRunStep.DOWNLOAD_REMOTE_BUNDLE,
        )
    }
    val appliesLocalData: Boolean = false
    val requiresUserChoice: Boolean = StudySyncRunStep.REQUIRE_CONFLICT_REVIEW in steps
}

object StudySyncRunbookPlanner {
    fun plan(
        intent: StudySyncRunIntent,
        state: StudySyncState,
        summary: StudySyncChangeSummary? = null,
        restoreMergePlan: StudySyncMergePlan? = null,
    ): StudySyncRunPlan = when (intent) {
        StudySyncRunIntent.MANUAL_UPLOAD -> manualUploadPlan(intent, state, summary)
        StudySyncRunIntent.MANUAL_REMOTE_CHECK -> remoteCheckPlan(intent, state)
        StudySyncRunIntent.RESTORE_PREVIEW -> restorePreviewPlan(intent, state, restoreMergePlan)
        StudySyncRunIntent.BACKGROUND_UPLOAD_AFTER_CHANGES -> backgroundPlan(
            intent = intent,
            requiredMode = StudySyncBackgroundMode.UPLOAD_AFTER_CHANGES,
            state = state,
            summary = summary,
        )
        StudySyncRunIntent.BACKGROUND_CHECK_DAILY -> backgroundPlan(
            intent = intent,
            requiredMode = StudySyncBackgroundMode.CHECK_DAILY,
            state = state,
            summary = summary,
        )
        StudySyncRunIntent.BACKGROUND_FULL_AUTOMATIC -> backgroundPlan(
            intent = intent,
            requiredMode = StudySyncBackgroundMode.FULL_AUTOMATIC,
            state = state,
            summary = summary,
        )
    }

    private fun manualUploadPlan(
        intent: StudySyncRunIntent,
        state: StudySyncState,
        summary: StudySyncChangeSummary?,
    ): StudySyncRunPlan {
        val blockers = commonProviderBlockers(state) + if (summary?.hasLocalData == false) {
            listOf(StudySyncRunBlocker.NO_LOCAL_DATA)
        } else {
            emptyList()
        }
        return StudySyncRunPlan(
            intent = intent,
            steps = if (blockers.isEmpty()) {
                listOf(
                    StudySyncRunStep.INSPECT_LOCAL_CHANGES,
                    StudySyncRunStep.BUILD_ENCRYPTED_BUNDLE,
                    StudySyncRunStep.UPLOAD_ENCRYPTED_BUNDLE,
                    StudySyncRunStep.RECORD_LEDGER_EVENT,
                )
            } else {
                listOf(StudySyncRunStep.INSPECT_LOCAL_CHANGES)
            },
            blockers = blockers,
        )
    }

    private fun remoteCheckPlan(intent: StudySyncRunIntent, state: StudySyncState): StudySyncRunPlan {
        val blockers = commonProviderBlockers(state, localDataRequired = false)
        return StudySyncRunPlan(
            intent = intent,
            steps = if (blockers.isEmpty()) {
                listOf(StudySyncRunStep.LIST_REMOTE_CHECKPOINTS, StudySyncRunStep.RECORD_LEDGER_EVENT)
            } else {
                listOf(StudySyncRunStep.LIST_REMOTE_CHECKPOINTS)
            },
            blockers = blockers,
        )
    }

    private fun restorePreviewPlan(
        intent: StudySyncRunIntent,
        state: StudySyncState,
        restoreMergePlan: StudySyncMergePlan?,
    ): StudySyncRunPlan {
        val providerBlockers = commonProviderBlockers(state, localDataRequired = false)
        val remoteBlockers = if (state.remoteCheckpointAvailable) {
            emptyList()
        } else {
            listOf(StudySyncRunBlocker.NO_REMOTE_CHECKPOINT)
        }
        val conflictBlockers = if (restoreMergePlan?.canApplyAutomatically == false) {
            listOf(StudySyncRunBlocker.CONFLICT_REVIEW_REQUIRED)
        } else {
            emptyList()
        }
        val blockers = providerBlockers + remoteBlockers + conflictBlockers
        val steps = buildList {
            add(StudySyncRunStep.LIST_REMOTE_CHECKPOINTS)
            if (providerBlockers.isEmpty() && remoteBlockers.isEmpty()) {
                add(StudySyncRunStep.DOWNLOAD_REMOTE_BUNDLE)
                add(StudySyncRunStep.VERIFY_BUNDLE_INTEGRITY)
                add(StudySyncRunStep.DRY_RUN_RESTORE)
                add(StudySyncRunStep.BUILD_MERGE_PLAN)
                if (conflictBlockers.isNotEmpty()) add(StudySyncRunStep.REQUIRE_CONFLICT_REVIEW)
                add(StudySyncRunStep.RECORD_LEDGER_EVENT)
            }
        }
        return StudySyncRunPlan(intent = intent, steps = steps, blockers = blockers)
    }

    private fun backgroundPlan(
        intent: StudySyncRunIntent,
        requiredMode: StudySyncBackgroundMode,
        state: StudySyncState,
        summary: StudySyncChangeSummary?,
    ): StudySyncRunPlan {
        val backgroundPlan = StudySyncBackgroundSyncPlanner.plan(state.settings)
        val blockers = buildList {
            if (!backgroundPlan.canSchedule) add(StudySyncRunBlocker.BACKGROUND_MODE_DISABLED)
            if (backgroundPlan.mode != requiredMode) add(StudySyncRunBlocker.BACKGROUND_MODE_MISMATCH)
            addAll(commonProviderBlockers(state, localDataRequired = false))
            if (requiredMode != StudySyncBackgroundMode.CHECK_DAILY && summary?.hasLocalData == false) {
                add(StudySyncRunBlocker.NO_LOCAL_DATA)
            }
        }.distinct()
        val steps = when (requiredMode) {
            StudySyncBackgroundMode.UPLOAD_AFTER_CHANGES -> listOf(
                StudySyncRunStep.INSPECT_LOCAL_CHANGES,
                StudySyncRunStep.BUILD_ENCRYPTED_BUNDLE,
                StudySyncRunStep.UPLOAD_ENCRYPTED_BUNDLE,
                StudySyncRunStep.RECORD_LEDGER_EVENT,
            )
            StudySyncBackgroundMode.CHECK_DAILY -> listOf(
                StudySyncRunStep.LIST_REMOTE_CHECKPOINTS,
                StudySyncRunStep.RECORD_LEDGER_EVENT,
            )
            StudySyncBackgroundMode.FULL_AUTOMATIC -> listOf(
                StudySyncRunStep.INSPECT_LOCAL_CHANGES,
                StudySyncRunStep.BUILD_ENCRYPTED_BUNDLE,
                StudySyncRunStep.UPLOAD_ENCRYPTED_BUNDLE,
                StudySyncRunStep.LIST_REMOTE_CHECKPOINTS,
                StudySyncRunStep.RECORD_LEDGER_EVENT,
            )
            else -> listOf(StudySyncRunStep.INSPECT_LOCAL_CHANGES)
        }
        return StudySyncRunPlan(
            intent = intent,
            steps = steps,
            blockers = blockers,
            backgroundMode = backgroundPlan.mode,
        )
    }

    private fun commonProviderBlockers(
        state: StudySyncState,
        localDataRequired: Boolean = true,
    ): List<StudySyncRunBlocker> = buildList {
        if (!state.enabled || !state.settings.syncEnabled) add(StudySyncRunBlocker.SYNC_DISABLED)
        val providerMissing = !state.settings.providerConfigured ||
            state.settings.configuredProviderId == StudyFlowSyncProviderIds.NONE
        if (providerMissing) add(StudySyncRunBlocker.PROVIDER_NOT_CONFIGURED)
        if (!state.settings.encryptRemoteBackups) add(StudySyncRunBlocker.ENCRYPTION_DISABLED)
        if (localDataRequired && state.blockedReason != null) add(StudySyncRunBlocker.NO_LOCAL_DATA)
    }.distinct()
}
