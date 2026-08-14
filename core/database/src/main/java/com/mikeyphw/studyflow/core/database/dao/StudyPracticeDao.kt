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
    @Upsert suspend fun upsertQuestion(question: QuestionEntity)
    @Upsert suspend fun upsertQuestions(questions: List<QuestionEntity>)
    @Upsert suspend fun upsertTest(test: StudyTestEntity)
    @Upsert suspend fun upsertTests(tests: List<StudyTestEntity>)
    @Upsert suspend fun upsertMockExam(mockExam: MockExamEntity)
    @Upsert suspend fun upsertMockExams(mockExams: List<MockExamEntity>)

    @Query("SELECT * FROM questions WHERE studyPlanId = :planId ORDER BY updatedAtEpochMillis DESC")
    fun observeQuestions(planId: String): Flow<List<QuestionEntity>>

    @Query("SELECT * FROM questions WHERE studyPlanId = :planId AND id = :questionId LIMIT 1")
    suspend fun findQuestion(planId: String, questionId: String): QuestionEntity?

    @Query("DELETE FROM questions WHERE studyPlanId = :planId AND id = :questionId")
    suspend fun deleteQuestion(planId: String, questionId: String): Int

    @Query("SELECT * FROM study_tests WHERE studyPlanId = :planId ORDER BY startedAtEpochMillis DESC")
    fun observeTests(planId: String): Flow<List<StudyTestEntity>>

    @Query("SELECT * FROM study_tests WHERE studyPlanId = :planId")
    suspend fun findAllTests(planId: String): List<StudyTestEntity>

    @Query("SELECT * FROM study_tests WHERE studyPlanId = :planId AND id = :testId LIMIT 1")
    suspend fun findTest(planId: String, testId: String): StudyTestEntity?

    @Query(
        """
        UPDATE study_tests SET startedAtEpochMillis = :startedAtEpochMillis
        WHERE studyPlanId = :planId AND id = :testId
          AND startedAtEpochMillis IS NULL AND submittedAtEpochMillis IS NULL
        """,
    )
    suspend fun startStudyTestIfNotStarted(planId: String, testId: String, startedAtEpochMillis: Long): Int

    @Query(
        """
        UPDATE study_tests
        SET answeredCount = :answeredCount,
            correctCount = :correctCount,
            startedAtEpochMillis = :startedAtEpochMillis,
            configurationJson = :nextConfiguration
        WHERE studyPlanId = :planId AND id = :testId
          AND submittedAtEpochMillis IS NULL
          AND configurationJson = :expectedConfiguration
        """,
    )
    suspend fun updateStudyTestAttemptIfOpen(
        planId: String,
        testId: String,
        expectedConfiguration: String,
        nextConfiguration: String,
        answeredCount: Int,
        correctCount: Int,
        startedAtEpochMillis: Long?,
    ): Int

    @Query(
        """
        UPDATE study_tests
        SET answeredCount = :answeredCount,
            correctCount = :correctCount,
            submittedAtEpochMillis = :submittedAtEpochMillis,
            configurationJson = :nextConfiguration
        WHERE studyPlanId = :planId AND id = :testId
          AND submittedAtEpochMillis IS NULL
          AND configurationJson = :expectedConfiguration
        """,
    )
    suspend fun submitStudyTestIfOpen(
        planId: String,
        testId: String,
        expectedConfiguration: String,
        nextConfiguration: String,
        answeredCount: Int,
        correctCount: Int,
        submittedAtEpochMillis: Long,
    ): Int

    @Query("SELECT * FROM mock_exams WHERE studyPlanId = :planId ORDER BY startedAtEpochMillis DESC")
    fun observeMockExams(planId: String): Flow<List<MockExamEntity>>

    @Query("SELECT * FROM mock_exams WHERE studyPlanId = :planId")
    suspend fun findAllMockExams(planId: String): List<MockExamEntity>

    @Query("SELECT * FROM mock_exams WHERE studyPlanId = :planId AND id = :mockExamId LIMIT 1")
    suspend fun findMockExam(planId: String, mockExamId: String): MockExamEntity?

    @Query(
        """
        UPDATE mock_exams SET startedAtEpochMillis = :startedAtEpochMillis
        WHERE studyPlanId = :planId AND id = :mockExamId
          AND startedAtEpochMillis IS NULL AND submittedAtEpochMillis IS NULL
        """,
    )
    suspend fun startMockExamIfNotStarted(planId: String, mockExamId: String, startedAtEpochMillis: Long): Int

    @Query(
        """
        UPDATE mock_exams
        SET answeredCount = :answeredCount,
            correctCount = :correctCount,
            startedAtEpochMillis = :startedAtEpochMillis,
            configurationJson = :nextConfiguration
        WHERE studyPlanId = :planId AND id = :mockExamId
          AND submittedAtEpochMillis IS NULL
          AND configurationJson = :expectedConfiguration
        """,
    )
    suspend fun updateMockExamAttemptIfOpen(
        planId: String,
        mockExamId: String,
        expectedConfiguration: String,
        nextConfiguration: String,
        answeredCount: Int,
        correctCount: Int,
        startedAtEpochMillis: Long?,
    ): Int

    @Query(
        """
        UPDATE mock_exams
        SET answeredCount = :answeredCount,
            correctCount = :correctCount,
            scorePercent = :scorePercent,
            submittedAtEpochMillis = :submittedAtEpochMillis,
            configurationJson = :nextConfiguration
        WHERE studyPlanId = :planId AND id = :mockExamId
          AND submittedAtEpochMillis IS NULL
          AND configurationJson = :expectedConfiguration
        """,
    )
    suspend fun submitMockExamIfOpen(
        planId: String,
        mockExamId: String,
        expectedConfiguration: String,
        nextConfiguration: String,
        answeredCount: Int,
        correctCount: Int,
        scorePercent: Double,
        submittedAtEpochMillis: Long,
    ): Int
}
