package com.mikeyphw.studyflow.core.model

enum class RuntimeSmokeProbeKind {
    REMINDER_RECONCILIATION,
    WIDGET_REFRESH,
    BACKUP_DRY_RUN,
    SEARCH_QUERY,
    RECOMMENDATION_QUERY,
    SYNC_FOUNDATION,
}

enum class RuntimeSmokeProbeStatus {
    NOT_RUN,
    PASS,
    BLOCKED,
}

data class RuntimeSmokeProbe(
    val kind: RuntimeSmokeProbeKind,
    val status: RuntimeSmokeProbeStatus,
    val title: String,
    val detail: String,
    val destructive: Boolean = false,
) {
    val safeForManualRun: Boolean = !destructive
}

data class RuntimeSmokeReport(
    val generatedAtEpochMillis: Long,
    val probes: List<RuntimeSmokeProbe>,
) {
    val passedCount: Int = probes.count { probe -> probe.status == RuntimeSmokeProbeStatus.PASS }
    val blockedCount: Int = probes.count { probe -> probe.status == RuntimeSmokeProbeStatus.BLOCKED }
    val readyForReleaseSeal: Boolean = probes.isNotEmpty() &&
        blockedCount == 0 &&
        probes.all(RuntimeSmokeProbe::safeForManualRun)
}
