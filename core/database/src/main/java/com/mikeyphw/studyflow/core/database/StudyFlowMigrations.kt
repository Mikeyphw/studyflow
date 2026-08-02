package com.mikeyphw.studyflow.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object StudyFlowMigrations {
    val Migration1To2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE subjects ADD COLUMN examStageId TEXT DEFAULT NULL")
            db.execSQL("ALTER TABLE topics ADD COLUMN examStageId TEXT DEFAULT NULL")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_subjects_examStageId ON subjects(examStageId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_topics_examStageId ON topics(examStageId)")
        }
    }

    val All = arrayOf(Migration1To2)
}
