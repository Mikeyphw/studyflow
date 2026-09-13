package com.mikeyphw.studyflow.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mikeyphw.studyflow.core.portability.StudyPlanExportMode
import com.mikeyphw.studyflow.core.portability.StudyPlanExportOptions
import com.mikeyphw.studyflow.core.portability.StudyPlanImportStrategy
import com.mikeyphw.studyflow.core.portability.StudyPlanPackagePurpose
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StudyPlanPortabilityOv06Test {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val database = Room.inMemoryDatabaseBuilder(context, StudyFlowDatabase::class.java)
        .allowMainThreadQueries().build()
    private val store = StudyPlanGraphPortabilityStore(database)

    @After fun close() = database.close()

    @Test fun fullReplaceCoversParentScopedLanguageAndTermuxRows() = runBlocking {
        val db = database.openHelper.writableDatabase
        insertPlan(db, "source", "ACTIVE", 11, 12)
        insertPlan(db, "target", "PAUSED", 21, 22)
        insertLanguage(db, "source", "src"); insertLanguage(db, "target", "stale")
        insertTermux(db, "source", "src"); insertTermux(db, "target", "stale")
        val payload = store.exportPayload("source", StudyPlanExportOptions(), "ov06", 50)
        assertEquals(1, payload.table("language_deck_profiles").rows.size)
        assertEquals(1, payload.table("vocabulary_cards").rows.size)
        assertEquals(1, payload.table("termux_command_results").rows.size)
        store.importPayload(payload, StudyPlanImportStrategy.REPLACE_EXISTING, "target", 99)
        assertEquals(listOf("target:src-deck"), strings("SELECT id FROM language_deck_profiles WHERE studyPlanId='target'"))
        assertEquals("target:src-expr", scalar("SELECT expressionId FROM vocabulary_cards"))
        assertEquals("target:src-req", scalar("SELECT requestId FROM termux_command_results"))
        assertFalse(strings("SELECT id FROM language_deck_profiles").any { it.contains("stale") })
    }

    @Test fun mergeRebindsChildrenToRetainedSingletonParent() = runBlocking {
        val db = database.openHelper.writableDatabase
        insertPlan(db, "source", "ACTIVE", 1, 2); insertPlan(db, "target", "ACTIVE", 3, 4)
        insertTarget(db, "source-target", "source"); insertTarget(db, "target-target", "target")
        db.execSQL("INSERT INTO exam_stages (id,studyPlanId,studyTargetId,name,type,scheduledDateIso,weight,minimumScorePercent,position) VALUES ('source-stage','source','source-target','Stage','OBJECTIVE',NULL,1.0,NULL,0)")
        val payload = store.exportPayload("source", StudyPlanExportOptions(), "ov06", 10)
        store.importPayload(payload, StudyPlanImportStrategy.MERGE_COMPATIBLE, "target", 20)
        assertEquals(1, longScalar("SELECT COUNT(*) FROM study_targets WHERE studyPlanId='target'"))
        assertEquals("target-target", scalar("SELECT studyTargetId FROM exam_stages WHERE studyPlanId='target'"))
    }

    @Test fun exactSnapshotRestoresRootStateAndTimestamps() = runBlocking {
        val db = database.openHelper.writableDatabase
        insertPlan(db, "plan", "ACTIVE", 111, 222)
        val snapshot = store.exportPayload(
            "plan", StudyPlanExportOptions(StudyPlanExportMode.FULL, true, true), "ov06", 333,
            StudyPlanPackagePurpose.POINT_IN_TIME_SNAPSHOT,
        )
        db.execSQL("UPDATE study_plans SET status='PAUSED',createdAtEpochMillis=999,updatedAtEpochMillis=999 WHERE id='plan'")
        store.importPayload(snapshot, StudyPlanImportStrategy.REPLACE_EXISTING, "plan", 777)
        assertEquals("ACTIVE", scalar("SELECT status FROM study_plans WHERE id='plan'"))
        assertEquals(111L, longScalar("SELECT createdAtEpochMillis FROM study_plans WHERE id='plan'"))
        assertEquals(222L, longScalar("SELECT updatedAtEpochMillis FROM study_plans WHERE id='plan'"))
    }

    @Test fun legacyV1MayOmitModernTablesButV2IsStrict() = runBlocking {
        val db = database.openHelper.writableDatabase
        insertPlan(db, "source", "ACTIVE", 1, 2)
        val full = store.exportPayload("source", StudyPlanExportOptions(), "ov06", 5)
        val legacy = full.copy(
            manifest = full.manifest.copy(schemaVersion = 1, tableRowCounts = mapOf("study_plans" to 1)),
            tables = full.tables.filter { it.name == "study_plans" },
        )
        assertEquals(1, store.validatePayload(legacy))
        assertTrue(runCatching { store.validatePayload(legacy.copy(manifest = legacy.manifest.copy(schemaVersion = 2))) }.isFailure)
    }

    private fun insertPlan(db: androidx.sqlite.db.SupportSQLiteDatabase, id: String, status: String, created: Long, updated: Long) {
        db.execSQL("""INSERT INTO study_plans (id,name,description,type,status,examDateIso,weeklyGoalHours,progressPercent,studiedMinutesThisWeek,questionsAnswered,averageAccuracyPercent,createdAtEpochMillis,updatedAtEpochMillis,trashedAtEpochMillis,contentMode) VALUES (?,?,'','FREE_STUDY',?,NULL,10,0,0,0,0,?,?,NULL,'EXAM_PLAN')""", arrayOf(id,id,status,created,updated))
    }
    private fun insertTarget(db: androidx.sqlite.db.SupportSQLiteDatabase, id: String, plan: String) {
        db.execSQL("INSERT INTO study_targets (id,studyPlanId,organization,role,examBoard,noticeUrl,confirmedExamDateIso,expectedExamDateIso,openings,estimatedCutoffPercent,createdAtEpochMillis,updatedAtEpochMillis) VALUES (?,?,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,1,1)", arrayOf(id,plan))
    }
    private fun insertLanguage(db: androidx.sqlite.db.SupportSQLiteDatabase, plan: String, prefix: String) {
        db.execSQL("INSERT INTO language_deck_profiles (id,studyPlanId,targetLanguage,nativeLanguage,regionFocus,defaultRegister,reviewDirectionsJson,tagsJson,createdAtEpochMillis,updatedAtEpochMillis) VALUES (?,?, 'en','pt-BR',NULL,'NEUTRAL','[]','[]',1,1)", arrayOf("$prefix-deck",plan))
        db.execSQL("INSERT INTO language_expressions (id,studyPlanId,deckId,expression,meaning,literalTranslation,translationEquivalent,type,register,region,pronunciationNote,commonMistake,examplesJson,tagsJson,createdAtEpochMillis,updatedAtEpochMillis) VALUES (?,?,?,'hello','oi',NULL,NULL,'VOCABULARY','NEUTRAL',NULL,NULL,NULL,'[]','[]',1,1)", arrayOf("$prefix-expr",plan,"$prefix-deck"))
        db.execSQL("INSERT INTO vocabulary_cards (id,expressionId,partOfSpeech,synonymsJson,antonymsJson,reviewDirectionsJson,difficulty) VALUES (?, ?, NULL, '[]','[]','[]',1)", arrayOf("$prefix-vocab","$prefix-expr"))
    }
    private fun insertTermux(db: androidx.sqlite.db.SupportSQLiteDatabase, plan: String, prefix: String) {
        db.execSQL("INSERT INTO termux_command_requests (id,studyPlanId,exerciseId,workspaceRoot,courseSlug,workspaceRelativePath,action,commandTemplateId,commandTemplateLabel,commandTemplateLanguage,commandLine,workingDirectory,state,createdAtEpochMillis) VALUES (?,?,NULL,'~/studyflow/courses','course','.','RUN',NULL,NULL,NULL,'echo ok','.','COMPLETED',1)", arrayOf("$prefix-req",plan))
        db.execSQL("INSERT INTO termux_command_results (id,requestId,state,exitCode,stdoutText,stderrText,resultJson,durationMillis,completedAtEpochMillis) VALUES (?,?,'COMPLETED',0,'ok','',NULL,1,2)", arrayOf("$prefix-res","$prefix-req"))
    }
    private fun com.mikeyphw.studyflow.core.portability.StudyPlanPackagePayload.table(name: String) = tables.first { it.name == name }
    private fun strings(sql: String): List<String> = database.openHelper.writableDatabase.query(sql).use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }
    private fun scalar(sql: String) = strings(sql).single()
    private fun longScalar(sql: String): Long = database.openHelper.writableDatabase.query(sql).use { c -> assertTrue(c.moveToFirst()); c.getLong(0) }
}
