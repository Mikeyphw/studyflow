package com.mikeyphw.studyflow.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object StudyFlowMigrations {
    val Migration1To2 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE subjects ADD COLUMN examStageId TEXT DEFAULT NULL")
            database.execSQL("ALTER TABLE topics ADD COLUMN examStageId TEXT DEFAULT NULL")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_subjects_examStageId ON subjects(examStageId)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_topics_examStageId ON topics(examStageId)")
        }
    }

    val All = arrayOf(Migration1To2)
}
