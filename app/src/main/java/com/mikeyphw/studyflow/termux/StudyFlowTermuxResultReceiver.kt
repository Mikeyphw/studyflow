package com.mikeyphw.studyflow.termux

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.mikeyphw.studyflow.core.model.TermuxCommandResult
import com.mikeyphw.studyflow.core.model.TermuxExecutionState

/** Receives one-shot RUN_COMMAND result bundles returned by Termux. */
class StudyFlowTermuxResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = intent.toStudyFlowTermuxCommandResult(System.currentTimeMillis()) ?: return
        StudyFlowTermuxResultCache.record(result)
    }

    companion object {
        const val EXTRA_REQUEST_ID = "com.mikeyphw.studyflow.termux.REQUEST_ID"
        const val EXTRA_PLUGIN_RESULT_BUNDLE = "result"
        const val EXTRA_STDOUT = "stdout"
        const val EXTRA_STDERR = "stderr"
        const val EXTRA_EXIT_CODE = "exitCode"
        const val EXTRA_ERR = "err"
        const val EXTRA_ERRMSG = "errmsg"
    }
}

object StudyFlowTermuxResultCache {
    @Volatile
    private var latestResult: TermuxCommandResult? = null

    fun record(result: TermuxCommandResult) {
        latestResult = result
    }

    fun latest(): TermuxCommandResult? = latestResult
}

fun Intent.toStudyFlowTermuxCommandResult(completedAtEpochMillis: Long): TermuxCommandResult? {
    val requestId = getStringExtra(StudyFlowTermuxResultReceiver.EXTRA_REQUEST_ID).orEmpty()
    if (requestId.isBlank()) return null
    val bundle = getBundleExtra(StudyFlowTermuxResultReceiver.EXTRA_PLUGIN_RESULT_BUNDLE) ?: Bundle.EMPTY
    val err = bundle.getInt(StudyFlowTermuxResultReceiver.EXTRA_ERR, -1)
    val exitCode = if (bundle.containsKey(StudyFlowTermuxResultReceiver.EXTRA_EXIT_CODE)) {
        bundle.getInt(StudyFlowTermuxResultReceiver.EXTRA_EXIT_CODE)
    } else {
        null
    }
    val stderr = listOfNotNull(
        bundle.getString(StudyFlowTermuxResultReceiver.EXTRA_STDERR),
        bundle.getString(StudyFlowTermuxResultReceiver.EXTRA_ERRMSG)?.takeIf { it.isNotBlank() },
    ).joinToString(separator = "
")
    return TermuxCommandResult(
        requestId = requestId,
        state = if (err == -1 && exitCode == 0) TermuxExecutionState.SUCCEEDED else TermuxExecutionState.FAILED,
        exitCode = exitCode,
        stdoutText = bundle.getString(StudyFlowTermuxResultReceiver.EXTRA_STDOUT).orEmpty(),
        stderrText = stderr,
        resultJson = null,
        durationMillis = null,
        completedAtEpochMillis = completedAtEpochMillis,
    )
}
