package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.RuntimeSmokeProbe
import com.mikeyphw.studyflow.core.model.RuntimeSmokeProbeKind
import com.mikeyphw.studyflow.core.model.RuntimeSmokeProbeStatus
import com.mikeyphw.studyflow.core.model.RuntimeSmokeReport

object RuntimeSmokeWorkbench {
    fun initialReport(generatedAtEpochMillis: Long): RuntimeSmokeReport = RuntimeSmokeReport(
        generatedAtEpochMillis = generatedAtEpochMillis,
        probes = RuntimeSmokeProbeKind.values().map { kind ->
            RuntimeSmokeProbe(
                kind = kind,
                status = RuntimeSmokeProbeStatus.NOT_RUN,
                title = kind.defaultTitle(),
                detail = kind.defaultDetail(),
            )
        },
    )

    fun markPassed(
        report: RuntimeSmokeReport,
        kind: RuntimeSmokeProbeKind,
        detail: String,
    ): RuntimeSmokeReport = report.copy(
        probes = report.probes.map { probe ->
            if (probe.kind == kind) probe.copy(status = RuntimeSmokeProbeStatus.PASS, detail = detail) else probe
        },
    )

    fun markBlocked(
        report: RuntimeSmokeReport,
        kind: RuntimeSmokeProbeKind,
        detail: String,
    ): RuntimeSmokeReport = report.copy(
        probes = report.probes.map { probe ->
            if (probe.kind == kind) probe.copy(status = RuntimeSmokeProbeStatus.BLOCKED, detail = detail) else probe
        },
    )

    fun completedReport(generatedAtEpochMillis: Long): RuntimeSmokeReport = RuntimeSmokeProbeKind.values().fold(
        initialReport(generatedAtEpochMillis),
    ) { report, kind -> markPassed(report, kind, kind.defaultPassedDetail()) }

    private fun RuntimeSmokeProbeKind.defaultTitle(): String = when (this) {
        RuntimeSmokeProbeKind.REMINDER_RECONCILIATION -> "Reminder reconciliation"
        RuntimeSmokeProbeKind.WIDGET_REFRESH -> "Today widget refresh"
        RuntimeSmokeProbeKind.BACKUP_DRY_RUN -> "Backup dry-run"
        RuntimeSmokeProbeKind.SEARCH_QUERY -> "Search query"
        RuntimeSmokeProbeKind.RECOMMENDATION_QUERY -> "Recommendation query"
        RuntimeSmokeProbeKind.SYNC_FOUNDATION -> "Local checkpoint"
    }

    private fun RuntimeSmokeProbeKind.defaultDetail(): String = when (this) {
        RuntimeSmokeProbeKind.REMINDER_RECONCILIATION -> "Run reminder reconciliation for the default Study Plan."
        RuntimeSmokeProbeKind.WIDGET_REFRESH -> "Refresh all installed Today widgets from repository state."
        RuntimeSmokeProbeKind.BACKUP_DRY_RUN -> "Use restore dry-run before any imported package writes data."
        RuntimeSmokeProbeKind.SEARCH_QUERY -> "Run a bounded global search query against local Room data."
        RuntimeSmokeProbeKind.RECOMMENDATION_QUERY -> "Load deterministic recommendations for the default Study Plan."
        RuntimeSmokeProbeKind.SYNC_FOUNDATION -> "Inspect local checkpoint readiness without network access."
    }

    private fun RuntimeSmokeProbeKind.defaultPassedDetail(): String = when (this) {
        RuntimeSmokeProbeKind.REMINDER_RECONCILIATION ->
            "Reminder reconciliation completed without posting a notification."
        RuntimeSmokeProbeKind.WIDGET_REFRESH -> "Today widgets were asked to refresh from repository-backed summaries."
        RuntimeSmokeProbeKind.BACKUP_DRY_RUN -> "Backup restore dry-run path is available and non-destructive."
        RuntimeSmokeProbeKind.SEARCH_QUERY -> "Global search repository query path is available."
        RuntimeSmokeProbeKind.RECOMMENDATION_QUERY -> "Recommendation repository query path is available."
        RuntimeSmokeProbeKind.SYNC_FOUNDATION -> "Local checkpoint inspection completed without network access."
    }
}
