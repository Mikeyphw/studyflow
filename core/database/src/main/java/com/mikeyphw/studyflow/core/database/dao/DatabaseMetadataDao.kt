package com.mikeyphw.studyflow.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.mikeyphw.studyflow.core.database.entity.DatabaseMetadataEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DatabaseMetadataDao {
    @Query("SELECT value FROM database_metadata WHERE `key` = :key LIMIT 1")
    suspend fun findValue(key: String): String?

    @Query("SELECT value FROM database_metadata WHERE `key` = :key LIMIT 1")
    fun observeValue(key: String): Flow<String?>

    @Upsert
    suspend fun upsert(entry: DatabaseMetadataEntity)

    @Query("DELETE FROM database_metadata WHERE `key` = :key")
    suspend fun delete(key: String): Int
}
