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

    val Migration2To3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE study_sessions ADD COLUMN targetDurationMinutes INTEGER DEFAULT NULL",
            )
            db.execSQL(
                """
                UPDATE study_sessions
                SET targetDurationMinutes = (
                    SELECT activity.plannedDurationMinutes
                    FROM planned_activities AS activity
                    WHERE activity.id = study_sessions.plannedActivityId
                      AND activity.studyPlanId = study_sessions.studyPlanId
                )
                WHERE targetDurationMinutes IS NULL
                  AND plannedActivityId IS NOT NULL
                """.trimIndent(),
            )
        }
    }

    val Migration3To4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            StudyFlowIntegrity.verifyExistingData(db)
            StudyFlowIntegrity.install(db)
        }
    }

    val Migration4To5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE study_plans ADD COLUMN contentMode TEXT NOT NULL DEFAULT 'EXAM_PLAN'",
            )
            StudyFlowIntegrity.verifyExistingData(db)
            StudyFlowIntegrity.install(db)
        }
    }

    val Migration5To6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS programming_course_profiles (
                    id TEXT NOT NULL PRIMARY KEY,
                    studyPlanId TEXT NOT NULL,
                    primaryLanguage TEXT NOT NULL,
                    level TEXT NOT NULL,
                    repositoryUri TEXT,
                    workspaceRelativePath TEXT NOT NULL,
                    buildSystem TEXT,
                    packageManager TEXT,
                    tagsJson TEXT NOT NULL,
                    createdAtEpochMillis INTEGER NOT NULL,
                    updatedAtEpochMillis INTEGER NOT NULL,
                    FOREIGN KEY(studyPlanId) REFERENCES study_plans(id) ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE UNIQUE INDEX IF NOT EXISTS index_programming_course_profiles_studyPlanId
                ON programming_course_profiles(studyPlanId)
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS code_snippets (
                    id TEXT NOT NULL PRIMARY KEY,
                    studyPlanId TEXT NOT NULL,
                    subjectId TEXT,
                    topicId TEXT,
                    title TEXT NOT NULL,
                    language TEXT NOT NULL,
                    code TEXT NOT NULL,
                    description TEXT NOT NULL,
                    sourceUri TEXT,
                    tagsJson TEXT NOT NULL,
                    createdAtEpochMillis INTEGER NOT NULL,
                    updatedAtEpochMillis INTEGER NOT NULL,
                    FOREIGN KEY(studyPlanId) REFERENCES study_plans(id) ON DELETE CASCADE,
                    FOREIGN KEY(subjectId) REFERENCES subjects(id) ON DELETE SET NULL,
                    FOREIGN KEY(topicId) REFERENCES topics(id) ON DELETE SET NULL
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_code_snippets_studyPlanId ON code_snippets(studyPlanId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_code_snippets_subjectId ON code_snippets(subjectId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_code_snippets_topicId ON code_snippets(topicId)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS coding_exercises (
                    id TEXT NOT NULL PRIMARY KEY,
                    studyPlanId TEXT NOT NULL,
                    subjectId TEXT,
                    topicId TEXT,
                    snippetId TEXT,
                    type TEXT NOT NULL,
                    title TEXT NOT NULL,
                    prompt TEXT NOT NULL,
                    starterCode TEXT NOT NULL,
                    expectedOutput TEXT,
                    hintsJson TEXT NOT NULL,
                    solutionNotes TEXT NOT NULL,
                    language TEXT NOT NULL,
                    difficulty INTEGER NOT NULL,
                    status TEXT NOT NULL,
                    runCommandTemplate TEXT,
                    testCommandTemplate TEXT,
                    filesJson TEXT NOT NULL,
                    tagsJson TEXT NOT NULL,
                    lastResultJson TEXT,
                    retryAtEpochMillis INTEGER,
                    createdAtEpochMillis INTEGER NOT NULL,
                    updatedAtEpochMillis INTEGER NOT NULL,
                    FOREIGN KEY(studyPlanId) REFERENCES study_plans(id) ON DELETE CASCADE,
                    FOREIGN KEY(subjectId) REFERENCES subjects(id) ON DELETE SET NULL,
                    FOREIGN KEY(topicId) REFERENCES topics(id) ON DELETE SET NULL,
                    FOREIGN KEY(snippetId) REFERENCES code_snippets(id) ON DELETE SET NULL
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_coding_exercises_studyPlanId ON coding_exercises(studyPlanId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_coding_exercises_subjectId ON coding_exercises(subjectId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_coding_exercises_topicId ON coding_exercises(topicId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_coding_exercises_snippetId ON coding_exercises(snippetId)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS mini_projects (
                    id TEXT NOT NULL PRIMARY KEY,
                    studyPlanId TEXT NOT NULL,
                    subjectId TEXT,
                    topicId TEXT,
                    title TEXT NOT NULL,
                    description TEXT NOT NULL,
                    status TEXT NOT NULL,
                    language TEXT NOT NULL,
                    repositoryUri TEXT,
                    workspaceRelativePath TEXT NOT NULL,
                    checkpointsJson TEXT NOT NULL,
                    deliverablesJson TEXT NOT NULL,
                    runCommandTemplate TEXT,
                    testCommandTemplate TEXT,
                    createdAtEpochMillis INTEGER NOT NULL,
                    updatedAtEpochMillis INTEGER NOT NULL,
                    FOREIGN KEY(studyPlanId) REFERENCES study_plans(id) ON DELETE CASCADE,
                    FOREIGN KEY(subjectId) REFERENCES subjects(id) ON DELETE SET NULL,
                    FOREIGN KEY(topicId) REFERENCES topics(id) ON DELETE SET NULL
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_mini_projects_studyPlanId ON mini_projects(studyPlanId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_mini_projects_subjectId ON mini_projects(subjectId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_mini_projects_topicId ON mini_projects(topicId)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS termux_runtime_preferences (
                    studyPlanId TEXT NOT NULL PRIMARY KEY,
                    enabled INTEGER NOT NULL,
                    workspaceRoot TEXT NOT NULL,
                    preferredLanguage TEXT,
                    defaultRunCommand TEXT,
                    defaultTestCommand TEXT,
                    allowedCommandTemplatesJson TEXT NOT NULL,
                    captureStdout INTEGER NOT NULL,
                    captureStderr INTEGER NOT NULL,
                    updatedAtEpochMillis INTEGER NOT NULL,
                    FOREIGN KEY(studyPlanId) REFERENCES study_plans(id) ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            StudyFlowIntegrity.verifyExistingData(db)
            StudyFlowIntegrity.install(db)
        }
    }


    val All = arrayOf(Migration1To2, Migration2To3, Migration3To4, Migration4To5, Migration5To6)
}

