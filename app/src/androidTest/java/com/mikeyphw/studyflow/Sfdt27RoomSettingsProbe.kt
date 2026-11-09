package com.mikeyphw.studyflow

import android.content.Context
import androidx.room.Room
import com.mikeyphw.studyflow.core.database.StudyFlowDatabase
import com.mikeyphw.studyflow.core.database.StudyFlowMigrations
import com.mikeyphw.studyflow.core.database.entity.StudyPlanSettingsEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * Android-test-only observer. Keeping this second-handle Room lookup outside
 * Sfdt22HostedStudyJourneyTest preserves the historical HF22 rule that the
 * five production Compose journeys contain no blocking coroutine or injected
 * test repositories. No writes, defaults, fixtures, or logging are performed.
 *
 * This handle is deliberately independent from the production repository's
 * active transaction: a discrepancy is evidence, not an inferred root cause.
 */
internal object Sfdt27RoomSettingsProbe {
    fun read(context: Context, planId: String): StudyPlanSettingsEntity? {
        val secondHandle = Room.databaseBuilder(
            context.applicationContext,
            StudyFlowDatabase::class.java,
            StudyFlowDatabase.DATABASE_NAME,
        ).addMigrations(*StudyFlowMigrations.All).build()
        return try {
            runBlocking(Dispatchers.IO) {
                secondHandle.studySupportDao().findPlanSettings(planId)
            }
        } finally {
            secondHandle.close()
        }
    }
}
