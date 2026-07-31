package com.mikeyphw.studyflow.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.mikeyphw.studyflow.core.database.entity.MockExamEntity
import com.mikeyphw.studyflow.core.database.entity.QuestionEntity
import com.mikeyphw.studyflow.core.database.entity.StudyTestEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StudyPracticeDao {
    @Upsert
    suspend fun upsertQuestions(questions: List<QuestionEntity>)

    @Upsert
    suspend fun upsertTests(tests: List<StudyTestEntity>)

    @Upsert
    suspend fun upsertMockExams(mockExams: List<MockExamEntity>)

    @Query("SELECT * FROM questions WHERE studyPlanId = :planId ORDER BY updatedAtEpochMillis DESC")
    fun observeQuestions(planId: String): Flow<List<QuestionEntity>>

    @Query("SELECT * FROM study_tests WHERE studyPlanId = :planId ORDER BY startedAtEpochMillis DESC")
    fun observeTests(planId: String): Flow<List<StudyTestEntity>>

    @Query("SELECT * FROM mock_exams WHERE studyPlanId = :planId ORDER BY startedAtEpochMillis DESC")
    fun observeMockExams(planId: String): Flow<List<MockExamEntity>>
}
