package com.mikeyphw.studyflow

import com.mikeyphw.studyflow.core.diagnostics.Sfdt27DiagnosticTrace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/** Android-test-only read of the ACTUAL application's Room handle.
 * Does not create a database, write settings or expose IDs to host artifacts.
 * The production handler is installed only on a debuggable instance.
 */
internal object Sfdt27ProductionSettingsProbe {
    fun record(planId: String, point: String) {
        val checkpoint = when (point) {
            "after_subject" -> "PRODUCTION_AFTER_SUBJECT"
            "before_topic" -> "PRODUCTION_BEFORE_TOPIC"
            "after_topic_action" -> "PRODUCTION_AFTER_TOPIC_ACTION"
            "after_topic_saved" -> "PRODUCTION_AFTER_TOPIC_SAVED"
            else -> throw IllegalArgumentException("Unreviewed DIAG-02 checkpoint")
        }
        runBlocking(Dispatchers.IO) {
            Sfdt27DiagnosticTrace.probeProduction(planId, checkpoint)
        }
    }
}
