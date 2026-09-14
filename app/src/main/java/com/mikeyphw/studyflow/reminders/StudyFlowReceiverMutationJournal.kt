package com.mikeyphw.studyflow.reminders

import android.content.Context
import java.security.MessageDigest

/**
 * Persistent receiver mutation ledger for reminder/widget broadcasts.
 *
 * BroadcastReceiver coroutines can be interrupted by process death; every mutation intent is
 * recorded before execution and settled only after the mutation path returns. Boot/resume
 * reconciliation can inspect pending/failed entries instead of losing the fact that a receiver
 * accepted work.
 */
class StudyFlowReceiverMutationJournal(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE)

    fun begin(kind: String, identity: String, nowEpochMillis: Long = System.currentTimeMillis()): StudyFlowReceiverMutationHandle {
        require(kind.isNotBlank()) { "Receiver mutation kind must not be blank." }
        require(identity.isNotBlank()) { "Receiver mutation identity must not be blank." }
        val handle = StudyFlowReceiverMutationHandle(key(kind, identity))
        val attempts = preferences.getString(handle.key, null)
            ?.split('|')
            ?.getOrNull(3)
            ?.toIntOrNull()
            ?: 0
        preferences.edit()
            .putString(handle.key, listOf("pending", nowEpochMillis, kind, attempts + 1, identity).joinToString("|"))
            .apply()
        return handle
    }

    fun complete(handle: StudyFlowReceiverMutationHandle) {
        preferences.edit().remove(handle.key).apply()
    }

    fun fail(handle: StudyFlowReceiverMutationHandle, reason: String, nowEpochMillis: Long = System.currentTimeMillis()) {
        preferences.edit()
            .putString(handle.key, listOf("failed", nowEpochMillis, reason.replace('|', '-')).joinToString("|"))
            .apply()
    }

    fun pendingCount(): Int = preferences.all.count { (_, value) -> value.toString().startsWith("pending|") }

    private fun key(kind: String, identity: String): String = "receiver_mutation_sha256_" + MessageDigest
        .getInstance("SHA-256")
        .digest("$kind|$identity".toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private companion object {
        const val PREFERENCE_NAME = "studyflow_receiver_mutations"
    }
}

data class StudyFlowReceiverMutationHandle(val key: String)

object StudyFlowReceiverMutationRecovery {
    fun noteReconcile(context: Context, reason: String) {
        StudyFlowReceiverMutationJournal(context).begin("reconcile", reason).also { handle ->
            StudyFlowReceiverMutationJournal(context).complete(handle)
        }
    }
}
