package com.mikeyphw.studyflow.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.mikeyphw.studyflow.core.database.entity.DatabaseMetadataEntity

@Dao
interface DatabaseMetadataDao {
    @Query("SELECT value FROM database_metadata WHERE `key` = :key LIMIT 1")
    suspend fun findValue(key: String): String?

    @Upsert
    suspend fun upsert(entry: DatabaseMetadataEntity)
}
