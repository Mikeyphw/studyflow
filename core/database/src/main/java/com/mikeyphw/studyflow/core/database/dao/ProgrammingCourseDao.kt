package com.mikeyphw.studyflow.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.mikeyphw.studyflow.core.database.entity.CodeSnippetEntity
import com.mikeyphw.studyflow.core.database.entity.CodingExerciseEntity
import com.mikeyphw.studyflow.core.database.entity.MiniProjectEntity
import com.mikeyphw.studyflow.core.database.entity.ProgrammingCourseProfileEntity
import com.mikeyphw.studyflow.core.database.entity.TermuxRuntimePreferenceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProgrammingCourseDao {
    @Upsert
    suspend fun upsertProfile(profile: ProgrammingCourseProfileEntity)

    @Upsert
    suspend fun upsertCodeSnippet(snippet: CodeSnippetEntity)

    @Upsert
    suspend fun upsertCodingExercise(exercise: CodingExerciseEntity)

    @Upsert
    suspend fun upsertMiniProject(project: MiniProjectEntity)

    @Upsert
    suspend fun upsertTermuxRuntimePreference(preference: TermuxRuntimePreferenceEntity)

    @Query("SELECT * FROM programming_course_profiles WHERE studyPlanId = :planId LIMIT 1")
    fun observeProfile(planId: String): Flow<ProgrammingCourseProfileEntity?>

    @Query("SELECT * FROM programming_course_profiles WHERE studyPlanId = :planId LIMIT 1")
    suspend fun findProfile(planId: String): ProgrammingCourseProfileEntity?

    @Query("SELECT * FROM code_snippets WHERE studyPlanId = :planId ORDER BY updatedAtEpochMillis DESC")
    fun observeCodeSnippets(planId: String): Flow<List<CodeSnippetEntity>>

    @Query("SELECT * FROM coding_exercises WHERE studyPlanId = :planId ORDER BY updatedAtEpochMillis DESC")
    fun observeCodingExercises(planId: String): Flow<List<CodingExerciseEntity>>

    @Query("SELECT * FROM mini_projects WHERE studyPlanId = :planId ORDER BY updatedAtEpochMillis DESC")
    fun observeMiniProjects(planId: String): Flow<List<MiniProjectEntity>>

    @Query("SELECT * FROM termux_runtime_preferences WHERE studyPlanId = :planId LIMIT 1")
    fun observeTermuxRuntimePreference(planId: String): Flow<TermuxRuntimePreferenceEntity?>

    @Query("SELECT * FROM termux_runtime_preferences WHERE studyPlanId = :planId LIMIT 1")
    suspend fun findTermuxRuntimePreference(planId: String): TermuxRuntimePreferenceEntity?

    @Query("DELETE FROM code_snippets WHERE studyPlanId = :planId AND id = :snippetId")
    suspend fun deleteCodeSnippet(planId: String, snippetId: String): Int

    @Query("DELETE FROM coding_exercises WHERE studyPlanId = :planId AND id = :exerciseId")
    suspend fun deleteCodingExercise(planId: String, exerciseId: String): Int

    @Query("DELETE FROM mini_projects WHERE studyPlanId = :planId AND id = :projectId")
    suspend fun deleteMiniProject(planId: String, projectId: String): Int
}
