package com.mikeyphw.studyflow.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.mikeyphw.studyflow.core.database.entity.ExamStageEntity
import com.mikeyphw.studyflow.core.database.entity.StudyTargetEntity
import com.mikeyphw.studyflow.core.database.entity.SubjectEntity
import com.mikeyphw.studyflow.core.database.entity.TopicEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StudyStructureDao {
    @Upsert
    suspend fun upsertTarget(target: StudyTargetEntity)

    @Upsert
    suspend fun upsertExamStages(stages: List<ExamStageEntity>)

    @Upsert
    suspend fun upsertSubjects(subjects: List<SubjectEntity>)

    @Upsert
    suspend fun upsertTopics(topics: List<TopicEntity>)

    @Query("SELECT * FROM subjects WHERE studyPlanId = :planId ORDER BY position, name COLLATE NOCASE")
    fun observeSubjects(planId: String): Flow<List<SubjectEntity>>

    @Query("SELECT * FROM topics WHERE studyPlanId = :planId ORDER BY subjectId, position, name COLLATE NOCASE")
    fun observeTopics(planId: String): Flow<List<TopicEntity>>

    @Query("DELETE FROM subjects WHERE id = :subjectId AND studyPlanId = :planId")
    suspend fun deleteSubject(planId: String, subjectId: String): Int

    @Query("DELETE FROM topics WHERE id = :topicId AND studyPlanId = :planId")
    suspend fun deleteTopic(planId: String, topicId: String): Int
}
