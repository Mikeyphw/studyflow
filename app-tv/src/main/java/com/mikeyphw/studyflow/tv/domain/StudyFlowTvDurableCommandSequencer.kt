package com.mikeyphw.studyflow.tv.domain

import android.content.Context
import android.content.SharedPreferences

/**
 * Restart-safe TV command-id generator. The previous Activity-local counter restarted at 1 after
 * recreation, colliding with durable phone-side dedup journals. OV18 commits the next value before
 * exposing the command id to the outbox or transport.
 */
class StudyFlowTvDurableCommandSequencer(context: Context) {
    private val preferences: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFERENCES,
        Context.MODE_PRIVATE,
    )

    @Synchronized
    fun next(): String {
        val nextValue = preferences.getLong(KEY_NEXT_SEQUENCE, 1L).coerceAtLeast(1L)
        val following = nextValue + 1L
        val committed = preferences.edit().putLong(KEY_NEXT_SEQUENCE, following).commit()
        check(committed) { "Unable to durably reserve TV command id $nextValue." }
        return "tv-local-$nextValue"
    }

    @Synchronized
    fun peekNextSequence(): Long = preferences.getLong(KEY_NEXT_SEQUENCE, 1L).coerceAtLeast(1L)

    companion object {
        const val overlay = "OV18"
        const val commandIdsSurviveActivityRecreation = true
        const val commitBeforeIdExposure = true
        private const val PREFERENCES = "studyflow-tv-command-sequence-v1"
        private const val KEY_NEXT_SEQUENCE = "next-sequence"
    }
}
