package com.mikeyphw.studyflow.core.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mikeyphw.studyflow.core.database.StudyFlowDatabase
import com.mikeyphw.studyflow.core.database.StudyFlowIntegrity
import com.mikeyphw.studyflow.core.database.entity.StudyPlanEntity
import com.mikeyphw.studyflow.core.model.ContentProgressStatus
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.Subject
import com.mikeyphw.studyflow.core.model.SubjectId
import com.mikeyphw.studyflow.core.model.Topic
import com.mikeyphw.studyflow.core.model.TopicId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StudyTopicMoveModernOwnershipTest {
    private lateinit var database: StudyFlowDatabase
    private lateinit var repository: RoomStudyContentRepository

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, StudyFlowDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        StudyFlowIntegrity.install(database.openHelper.writableDatabase)
        repository = RoomStudyContentRepository(database) { 1L }
    }

    @After
    fun close() = database.close()

    @Test
    fun movingTopicRebindsModernRowsToDestinationSubject() = runBlocking {
        val planId = StudyPlanId("plan")
        database.studyPlanDao().insert(
            StudyPlanEntity(
                id = planId.value, name = "Plan", description = "", type = "FREE_STUDY", status = "ACTIVE",
                examDateIso = null, weeklyGoalHours = 10, progressPercent = 0, studiedMinutesThisWeek = 0,
                questionsAnswered = 0, averageAccuracyPercent = 0, createdAtEpochMillis = 1,
                updatedAtEpochMillis = 1, trashedAtEpochMillis = null,
            ),
        )
        val oldSubject = subject(planId, "old", 0)
        val newSubject = subject(planId, "new", 1)
        repository.upsertSubject(oldSubject)
        repository.upsertSubject(newSubject)
        val topic = Topic(
            id = TopicId("topic"), studyPlanId = planId, subjectId = oldSubject.id, parentTopicId = null,
            name = "Topic", description = "", priority = 3, difficulty = 3, examIncidencePercent = null,
            estimatedMinutes = 0, progressPercent = 0, masteryPercent = 0,
            status = ContentProgressStatus.NOT_STARTED, position = 0, archived = false,
        )
        repository.upsertTopic(topic)
        val db = database.openHelper.writableDatabase
        db.execSQL(
            "INSERT INTO code_snippets (id,studyPlanId,subjectId,topicId,title,language,code,description," +
                "sourceUri,tagsJson,createdAtEpochMillis,updatedAtEpochMillis) " +
                "VALUES ('snippet','plan','old','topic','s','kotlin','x','',NULL,'[]',1,1)",
        )
        db.execSQL(
            "INSERT INTO coding_exercises (id,studyPlanId,subjectId,topicId,snippetId,type,title,prompt,starterCode," +
                "expectedOutput,hintsJson,solutionNotes,language,difficulty,status,runCommandTemplate,testCommandTemplate," +
                "filesJson,tagsJson,lastResultJson,retryAtEpochMillis,createdAtEpochMillis,updatedAtEpochMillis) VALUES " +
                "('exercise','plan','old','topic','snippet','CODE','e','p','',NULL,'[]','','kotlin',3,'TODO',NULL,NULL," +
                "'[]','[]',NULL,NULL,1,1)",
        )
        db.execSQL(
            "INSERT INTO mini_projects (id,studyPlanId,subjectId,topicId,title,description,status,language,repositoryUri," +
                "workspaceRelativePath,checkpointsJson,deliverablesJson,runCommandTemplate,testCommandTemplate," +
                "createdAtEpochMillis,updatedAtEpochMillis) VALUES " +
                "('project','plan','old','topic','p','','TODO','kotlin',NULL,'p','[]','[]',NULL,NULL,1,1)",
        )
        db.execSQL(
            "INSERT INTO study_cycles (id,studyPlanId,name,enabled,currentItemId,createdAtEpochMillis,updatedAtEpochMillis) " +
                "VALUES ('cycle','plan','Cycle',1,NULL,1,1)",
        )
        db.execSQL(
            "INSERT INTO study_cycle_items (id,studyCycleId,studyPlanId,subjectId,topicId,position,targetDurationMinutes," +
                "enabled,activityType,completionRule) VALUES ('item','cycle','plan','old','topic',0,25,1,'THEORY',NULL)",
        )

        repository.moveTopic(planId, topic.id, newSubject.id, null, 0)

        listOf("code_snippets", "coding_exercises", "mini_projects", "study_cycle_items").forEach { table ->
            val subjectId = db.query("SELECT subjectId FROM $table WHERE topicId='topic'").use { cursor ->
                check(cursor.moveToFirst()); cursor.getString(0)
            }
            assertEquals("new", subjectId)
        }
    }

    private fun subject(planId: StudyPlanId, id: String, position: Int) = Subject(
        id = SubjectId(id), studyPlanId = planId, name = id, description = "", colorArgb = null,
        priority = 3, examWeight = 1.0, estimatedMinutes = 0, progressPercent = 0, accuracyPercent = 0,
        status = ContentProgressStatus.NOT_STARTED, position = position, archived = false,
    )
}
