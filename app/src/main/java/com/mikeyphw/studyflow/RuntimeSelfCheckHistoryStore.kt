package com.mikeyphw.studyflow

import android.content.Context
import com.mikeyphw.studyflow.core.diagnostics.DebugProbeId
import com.mikeyphw.studyflow.core.diagnostics.DebugProbeMessage
import com.mikeyphw.studyflow.core.diagnostics.DebugProbeResult
import com.mikeyphw.studyflow.core.diagnostics.DebugProbeStatus
import com.mikeyphw.studyflow.core.diagnostics.RuntimeSelfCheckHistory
import com.mikeyphw.studyflow.core.diagnostics.RuntimeSelfCheckRun

/** Bounded, sanitized persistence for terminal Runtime Self-Check evidence only. */
internal class RuntimeSelfCheckHistoryStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "studyflow-runtime-self-check-history",
        Context.MODE_PRIVATE,
    )

    fun load(): RuntimeSelfCheckHistory {
        val runs = preferences.getString(KeyRuns, null)
            ?.lineSequence()
            ?.filter(String::isNotBlank)
            ?.mapNotNull(::decodeRun)
            ?.sortedByDescending(RuntimeSelfCheckRun::completedAtEpochMillis)
            ?.take(MaxRuns)
            ?.toList()
            .orEmpty()
        return RuntimeSelfCheckHistory(runs = runs, retainedRunCount = MaxRuns)
    }

    fun save(history: RuntimeSelfCheckHistory) {
        val encoded = history.runs
            .take(MaxRuns)
            .joinToString("\n") { run -> encodeRun(run.sanitized()) }
        preferences.edit().putString(KeyRuns, encoded).apply()
    }

    private fun encodeRun(run: RuntimeSelfCheckRun): String {
        val invariants = run.invariantFailures.joinToString(",")
        val results = run.attemptedProbeIds.joinToString(",") { id ->
            val result = run.results.getValue(id)
            listOf(
                id.name,
                result.status.name,
                result.message.name,
                result.durationMillis.toString(),
                result.completedAtEpochMillis.toString(),
            ).joinToString("~")
        }
        return listOf(
            run.startedAtEpochMillis.toString(),
            run.completedAtEpochMillis.toString(),
            invariants,
            results,
        ).joinToString("|")
    }

    private fun decodeRun(line: String): RuntimeSelfCheckRun? = try {
        val parts = line.split('|', limit = 4)
        require(parts.size == 4)
        val resultPairs = parts[3]
            .split(',')
            .filter(String::isNotBlank)
            .map { token ->
                val values = token.split('~')
                require(values.size == 5)
                val id = DebugProbeId.valueOf(values[0])
                id to DebugProbeResult(
                    id = id,
                    status = DebugProbeStatus.valueOf(values[1]),
                    message = DebugProbeMessage.valueOf(values[2]),
                    durationMillis = values[3].toLong(),
                    completedAtEpochMillis = values[4].toLong(),
                )
            }
        val results = resultPairs.toMap(linkedMapOf())
        RuntimeSelfCheckRun(
            startedAtEpochMillis = parts[0].toLong(),
            completedAtEpochMillis = parts[1].toLong(),
            attemptedProbeIds = resultPairs.map { it.first },
            results = results,
            invariantFailures = parts[2].split(',').filter(String::isNotBlank),
        )
    } catch (_: Exception) {
        null
    }

    private companion object {
        const val KeyRuns = "runs-v1"
        const val MaxRuns = 10
    }
}
