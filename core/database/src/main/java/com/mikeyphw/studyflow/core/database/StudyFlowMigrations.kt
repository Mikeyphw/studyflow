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


    val Migration6To7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS termux_command_requests (
                    id TEXT NOT NULL PRIMARY KEY,
                    studyPlanId TEXT NOT NULL,
                    exerciseId TEXT,
                    workspaceRoot TEXT NOT NULL,
                    courseSlug TEXT NOT NULL,
                    workspaceRelativePath TEXT NOT NULL,
                    action TEXT NOT NULL,
                    commandTemplateId TEXT,
                    commandTemplateLabel TEXT,
                    commandTemplateLanguage TEXT,
                    commandLine TEXT,
                    workingDirectory TEXT,
                    state TEXT NOT NULL,
                    createdAtEpochMillis INTEGER NOT NULL,
                    FOREIGN KEY(studyPlanId) REFERENCES study_plans(id) ON DELETE CASCADE,
                    FOREIGN KEY(exerciseId) REFERENCES coding_exercises(id) ON DELETE SET NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_termux_command_requests_studyPlanId " +
                    "ON termux_command_requests(studyPlanId)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_termux_command_requests_exerciseId " +
                    "ON termux_command_requests(exerciseId)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_termux_command_requests_state " +
                    "ON termux_command_requests(state)",
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS termux_command_results (
                    id TEXT NOT NULL PRIMARY KEY,
                    requestId TEXT NOT NULL,
                    state TEXT NOT NULL,
                    exitCode INTEGER,
                    stdoutText TEXT NOT NULL,
                    stderrText TEXT NOT NULL,
                    resultJson TEXT,
                    durationMillis INTEGER,
                    completedAtEpochMillis INTEGER NOT NULL,
                    FOREIGN KEY(requestId) REFERENCES termux_command_requests(id) ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_termux_command_results_requestId " +
                    "ON termux_command_results(requestId)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_termux_command_results_state " +
                    "ON termux_command_results(state)",
            )
            StudyFlowIntegrity.verifyExistingData(db)
            StudyFlowIntegrity.install(db)
        }
    }


    val Migration7To8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS language_deck_profiles (
                    id TEXT NOT NULL PRIMARY KEY,
                    studyPlanId TEXT NOT NULL,
                    targetLanguage TEXT NOT NULL,
                    nativeLanguage TEXT,
                    regionFocus TEXT,
                    defaultRegister TEXT NOT NULL,
                    reviewDirectionsJson TEXT NOT NULL,
                    tagsJson TEXT NOT NULL,
                    createdAtEpochMillis INTEGER NOT NULL,
                    updatedAtEpochMillis INTEGER NOT NULL,
                    FOREIGN KEY(studyPlanId) REFERENCES study_plans(id) ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_language_deck_profiles_studyPlanId " +
                    "ON language_deck_profiles(studyPlanId)",
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS language_expressions (
                    id TEXT NOT NULL PRIMARY KEY,
                    studyPlanId TEXT NOT NULL,
                    deckId TEXT NOT NULL,
                    expression TEXT NOT NULL,
                    meaning TEXT NOT NULL,
                    literalTranslation TEXT,
                    translationEquivalent TEXT,
                    type TEXT NOT NULL,
                    register TEXT NOT NULL,
                    region TEXT,
                    pronunciationNote TEXT,
                    commonMistake TEXT,
                    examplesJson TEXT NOT NULL,
                    tagsJson TEXT NOT NULL,
                    createdAtEpochMillis INTEGER NOT NULL,
                    updatedAtEpochMillis INTEGER NOT NULL,
                    FOREIGN KEY(studyPlanId) REFERENCES study_plans(id) ON DELETE CASCADE,
                    FOREIGN KEY(deckId) REFERENCES language_deck_profiles(id) ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_language_expressions_studyPlanId " +
                    "ON language_expressions(studyPlanId)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_language_expressions_deckId " +
                    "ON language_expressions(deckId)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_language_expressions_type " +
                    "ON language_expressions(type)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_language_expressions_register " +
                    "ON language_expressions(register)",
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS idiom_cards (
                    id TEXT NOT NULL PRIMARY KEY,
                    expressionId TEXT NOT NULL,
                    naturalExample TEXT NOT NULL,
                    userSentence TEXT,
                    clozePrompt TEXT,
                    acceptedAnswersJson TEXT NOT NULL,
                    reviewDirectionsJson TEXT NOT NULL,
                    difficulty INTEGER NOT NULL,
                    FOREIGN KEY(expressionId) REFERENCES language_expressions(id) ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_idiom_cards_expressionId " +
                    "ON idiom_cards(expressionId)",
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS vocabulary_cards (
                    id TEXT NOT NULL PRIMARY KEY,
                    expressionId TEXT NOT NULL,
                    partOfSpeech TEXT,
                    synonymsJson TEXT NOT NULL,
                    antonymsJson TEXT NOT NULL,
                    reviewDirectionsJson TEXT NOT NULL,
                    difficulty INTEGER NOT NULL,
                    FOREIGN KEY(expressionId) REFERENCES language_expressions(id) ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_vocabulary_cards_expressionId " +
                    "ON vocabulary_cards(expressionId)",
            )
            StudyFlowIntegrity.verifyExistingData(db)
            StudyFlowIntegrity.install(db)
        }
    }


    val Migration8To9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS language_review_attempts (
                    id TEXT NOT NULL PRIMARY KEY,
                    studyPlanId TEXT NOT NULL,
                    expressionId TEXT NOT NULL,
                    idiomCardId TEXT,
                    direction TEXT NOT NULL,
                    promptText TEXT NOT NULL,
                    answerText TEXT NOT NULL,
                    outcome TEXT NOT NULL,
                    scorePercent INTEGER NOT NULL,
                    reviewItemId TEXT,
                    createdAtEpochMillis INTEGER NOT NULL,
                    FOREIGN KEY(studyPlanId) REFERENCES study_plans(id) ON DELETE CASCADE,
                    FOREIGN KEY(expressionId) REFERENCES language_expressions(id) ON DELETE CASCADE,
                    FOREIGN KEY(idiomCardId) REFERENCES idiom_cards(id) ON DELETE SET NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_language_review_attempts_studyPlanId " +
                    "ON language_review_attempts(studyPlanId)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_language_review_attempts_expressionId " +
                    "ON language_review_attempts(expressionId)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_language_review_attempts_idiomCardId " +
                    "ON language_review_attempts(idiomCardId)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_language_review_attempts_outcome " +
                    "ON language_review_attempts(outcome)",
            )
            StudyFlowIntegrity.verifyExistingData(db)
            StudyFlowIntegrity.install(db)
        }
    }


    val Migration9To10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS exam_notice_revisions (
                    id TEXT NOT NULL PRIMARY KEY,
                    studyPlanId TEXT NOT NULL,
                    studyTargetId TEXT NOT NULL,
                    label TEXT NOT NULL,
                    sourceReference TEXT,
                    importedAtEpochMillis INTEGER NOT NULL,
                    normalizedContentSha256 TEXT NOT NULL,
                    parserVersion TEXT NOT NULL,
                    publicationDateIso TEXT,
                    addedCount INTEGER NOT NULL,
                    removedCount INTEGER NOT NULL,
                    changedCount INTEGER NOT NULL,
                    unchangedCount INTEGER NOT NULL,
                    FOREIGN KEY(studyPlanId) REFERENCES study_plans(id) ON DELETE CASCADE,
                    FOREIGN KEY(studyTargetId) REFERENCES study_targets(id) ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_exam_notice_revisions_studyPlanId " +
                    "ON exam_notice_revisions(studyPlanId)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_exam_notice_revisions_studyTargetId " +
                    "ON exam_notice_revisions(studyTargetId)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_exam_notice_revisions_studyPlanId_importedAtEpochMillis " +
                    "ON exam_notice_revisions(studyPlanId, importedAtEpochMillis)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_exam_notice_revisions_studyPlanId_normalizedContentSha256 " +
                    "ON exam_notice_revisions(studyPlanId, normalizedContentSha256)",
            )
            StudyFlowIntegrity.verifyExistingData(db)
            StudyFlowIntegrity.install(db)
        }
    }

    val Migration10To11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS study_cycles (
                    id TEXT NOT NULL PRIMARY KEY,
                    studyPlanId TEXT NOT NULL,
                    name TEXT NOT NULL,
                    enabled INTEGER NOT NULL,
                    currentItemId TEXT,
                    createdAtEpochMillis INTEGER NOT NULL,
                    updatedAtEpochMillis INTEGER NOT NULL,
                    FOREIGN KEY(studyPlanId) REFERENCES study_plans(id) ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_study_cycles_studyPlanId " +
                    "ON study_cycles(studyPlanId)",
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS study_cycle_items (
                    id TEXT NOT NULL PRIMARY KEY,
                    studyCycleId TEXT NOT NULL,
                    studyPlanId TEXT NOT NULL,
                    subjectId TEXT,
                    topicId TEXT,
                    position INTEGER NOT NULL,
                    targetDurationMinutes INTEGER NOT NULL,
                    enabled INTEGER NOT NULL,
                    activityType TEXT NOT NULL,
                    completionRule TEXT,
                    FOREIGN KEY(studyCycleId) REFERENCES study_cycles(id) ON DELETE CASCADE,
                    FOREIGN KEY(studyPlanId) REFERENCES study_plans(id) ON DELETE CASCADE,
                    FOREIGN KEY(subjectId) REFERENCES subjects(id) ON DELETE SET NULL,
                    FOREIGN KEY(topicId) REFERENCES topics(id) ON DELETE SET NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_study_cycle_items_studyCycleId " +
                    "ON study_cycle_items(studyCycleId)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_study_cycle_items_studyPlanId " +
                    "ON study_cycle_items(studyPlanId)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_study_cycle_items_subjectId " +
                    "ON study_cycle_items(subjectId)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_study_cycle_items_topicId " +
                    "ON study_cycle_items(topicId)",
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_study_cycle_items_studyCycleId_position " +
                    "ON study_cycle_items(studyCycleId, position)",
            )
            StudyFlowIntegrity.verifyExistingData(db)
            StudyFlowIntegrity.install(db)
        }
    }


    val All = arrayOf(
        Migration1To2,
        Migration2To3,
        Migration3To4,
        Migration4To5,
        Migration5To6,
        Migration6To7,
        Migration7To8,
        Migration8To9,
        Migration9To10,
        Migration10To11,
    )
}

