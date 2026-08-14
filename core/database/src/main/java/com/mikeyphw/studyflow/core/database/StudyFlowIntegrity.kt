package com.mikeyphw.studyflow.core.database

import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Database-side invariants that Room's single-column foreign keys cannot express.
 *
 * StudyFlow intentionally uses globally keyed child rows. These triggers prevent an existing
 * child id from being silently re-parented and make every cross-entity reference plan-scoped.
 * Repository validation remains the friendly error boundary; these triggers are the durable
 * last line of defense for imports, future repository code, and concurrent writes.
 */
object StudyFlowIntegrity {
    const val GraphRevisionMetadataKey = "graph_revision"

    private data class ScopedReference(
        val table: String,
        val column: String,
        val targetTable: String,
        val targetColumn: String = "id",
        val extraPredicate: String = "",
    )

    private val planOwnedTables = listOf(
        "study_targets",
        "exam_stages",
        "subjects",
        "topics",
        "study_strategies",
        "schedule_rules",
        "planned_activities",
        "study_sessions",
        "study_goals",
        "study_materials",
        "video_resources",
        "annexes",
        "questions",
        "study_tests",
        "mock_exams",
        "flashcards",
        "error_entries",
        "review_items",
        "study_notes",
        "plan_snapshots",
    )

    private val scopedReferences = listOf(
        ScopedReference("exam_stages", "studyTargetId", "study_targets"),
        ScopedReference("subjects", "examStageId", "exam_stages"),
        ScopedReference("topics", "subjectId", "subjects"),
        ScopedReference(
            table = "topics",
            column = "parentTopicId",
            targetTable = "topics",
            extraPredicate = " AND parent.subjectId = NEW.subjectId",
        ),
        ScopedReference("topics", "examStageId", "exam_stages"),
        ScopedReference("schedule_rules", "subjectId", "subjects"),
        ScopedReference("study_materials", "subjectId", "subjects"),
        ScopedReference("study_materials", "topicId", "topics"),
        ScopedReference("video_resources", "subjectId", "subjects"),
        ScopedReference("video_resources", "topicId", "topics"),
        ScopedReference("planned_activities", "subjectId", "subjects"),
        ScopedReference("planned_activities", "topicId", "topics"),
        ScopedReference("planned_activities", "materialId", "study_materials"),
        ScopedReference("planned_activities", "videoResourceId", "video_resources"),
        ScopedReference("study_sessions", "plannedActivityId", "planned_activities"),
        ScopedReference("study_sessions", "subjectId", "subjects"),
        ScopedReference("study_sessions", "topicId", "topics"),
        ScopedReference("study_sessions", "materialId", "study_materials"),
        ScopedReference("study_sessions", "videoResourceId", "video_resources"),
        ScopedReference("study_goals", "subjectId", "subjects"),
        ScopedReference("study_goals", "topicId", "topics"),
        ScopedReference("annexes", "subjectId", "subjects"),
        ScopedReference("annexes", "topicId", "topics"),
        ScopedReference("annexes", "materialId", "study_materials"),
        ScopedReference("questions", "subjectId", "subjects"),
        ScopedReference("questions", "topicId", "topics"),
        ScopedReference("error_entries", "questionId", "questions"),
        ScopedReference("error_entries", "subjectId", "subjects"),
        ScopedReference("error_entries", "topicId", "topics"),
        ScopedReference("review_items", "subjectId", "subjects"),
        ScopedReference("review_items", "topicId", "topics"),
        ScopedReference("review_items", "questionId", "questions"),
        ScopedReference("review_items", "flashcardId", "flashcards"),
        ScopedReference("review_items", "errorEntryId", "error_entries"),
        ScopedReference("flashcards", "subjectId", "subjects"),
        ScopedReference("flashcards", "topicId", "topics"),
        ScopedReference("study_notes", "subjectId", "subjects"),
        ScopedReference("study_notes", "topicId", "topics"),
        ScopedReference("study_notes", "materialId", "study_materials"),
    )

    fun install(db: SupportSQLiteDatabase) {
        planOwnedTables.forEach { table -> installStablePlanOwnerTrigger(db, table) }
        installPlanSettingsIdentityTriggers(db)
        scopedReferences.forEach { reference -> installScopedReferenceTriggers(db, reference) }
        installTopicHierarchyUpdateGuard(db)
        installExamStageDeleteCleanup(db)
        installSingleActiveSessionTriggers(db)
        installValueGuards(db)
        installGraphRevisionTriggers(db)
    }

    private fun installGraphRevisionTriggers(db: SupportSQLiteDatabase) {
        db.execSQL(
            "INSERT OR IGNORE INTO database_metadata (`key`, value) VALUES ('$GraphRevisionMetadataKey', '0')",
        )
        val revisionTables = (listOf("study_plans") + planOwnedTables + "study_plan_settings").distinct()
        revisionTables.forEach { table ->
            listOf("INSERT", "UPDATE", "DELETE").forEach { event ->
                val suffix = event.lowercase()
                db.execSQL(
                    """
                    CREATE TRIGGER IF NOT EXISTS studyflow_graph_revision_${table}_${suffix}
                    AFTER $event ON $table
                    BEGIN
                        UPDATE database_metadata
                        SET value = CAST(CAST(value AS INTEGER) + 1 AS TEXT)
                        WHERE `key` = '$GraphRevisionMetadataKey';
                    END
                    """.trimIndent(),
                )
            }
        }
    }


    fun verifyExistingData(db: SupportSQLiteDatabase) {
        db.query("PRAGMA foreign_key_check").use { cursor ->
            check(!cursor.moveToFirst()) {
                "StudyFlow database corruption: foreign-key violations must be repaired before schema v4."
            }
        }
        planOwnedTables.forEach { table ->
            requireNoRows(
                db = db,
                sql = "SELECT 1 FROM $table WHERE trim(id) = '' OR trim(studyPlanId) = '' LIMIT 1",
                message = "StudyFlow database corruption: $table contains a blank identity.",
            )
        }
        requireNoRows(
            db = db,
            sql = "SELECT 1 FROM study_plan_settings WHERE trim(studyPlanId) = '' LIMIT 1",
            message = "StudyFlow database corruption: Study Plan settings contain a blank identity.",
        )
        scopedReferences.forEach { reference -> verifyScopedReference(db, reference) }
        verifyValueGuards(db)
        requireNoRows(
            db = db,
            sql =
                "SELECT 1 FROM study_sessions WHERE status IN ('RUNNING', 'PAUSED') " +
                    "GROUP BY studyPlanId HAVING COUNT(*) > 1 LIMIT 1",
            message =
                "StudyFlow database corruption: multiple active Study Sessions exist for one Study Plan.",
        )
    }

    private fun verifyScopedReference(db: SupportSQLiteDatabase, reference: ScopedReference) {
        val targetAlias = if (reference.targetTable == reference.table) "parent" else "target"
        val extraPredicate = reference.extraPredicate.replace("NEW.", "child.")
        val predicate =
            "$targetAlias.${reference.targetColumn} = child.${reference.column} " +
                "AND $targetAlias.studyPlanId = child.studyPlanId$extraPredicate"
        requireNoRows(
            db = db,
            sql =
                "SELECT 1 FROM ${reference.table} AS child " +
                    "WHERE child.${reference.column} IS NOT NULL AND NOT EXISTS " +
                    "(SELECT 1 FROM ${reference.targetTable} AS $targetAlias WHERE $predicate) LIMIT 1",
            message =
                "StudyFlow database corruption: invalid ${reference.table}.${reference.column} ownership.",
        )
    }

    private fun requireNoRows(
        db: SupportSQLiteDatabase,
        sql: String,
        message: String,
    ) {
        db.query(sql).use { cursor ->
            check(!cursor.moveToFirst()) { message }
        }
    }

    private fun installStablePlanOwnerTrigger(db: SupportSQLiteDatabase, table: String) {
        db.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS guard_${table}_stable_plan_owner
            BEFORE UPDATE OF studyPlanId ON $table
            WHEN OLD.studyPlanId <> NEW.studyPlanId
            BEGIN
                SELECT RAISE(ABORT, 'StudyFlow integrity: $table cannot move between Study Plans');
            END
            """.trimIndent(),
        )
        listOf("INSERT", "UPDATE").forEach { event ->
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS guard_${table}_nonblank_identity_${event.lowercase()}
                BEFORE $event ON $table
                WHEN trim(NEW.id) = '' OR trim(NEW.studyPlanId) = ''
                BEGIN
                    SELECT RAISE(ABORT, 'StudyFlow integrity: $table identity cannot be blank');
                END
                """.trimIndent(),
            )
        }
    }

    private fun installPlanSettingsIdentityTriggers(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS guard_study_plan_settings_stable_plan_owner
            BEFORE UPDATE OF studyPlanId ON study_plan_settings
            WHEN OLD.studyPlanId <> NEW.studyPlanId
            BEGIN
                SELECT RAISE(ABORT, 'StudyFlow integrity: Study Plan settings cannot move between Study Plans');
            END
            """.trimIndent(),
        )
        listOf("INSERT", "UPDATE").forEach { event ->
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS guard_study_plan_settings_nonblank_identity_${event.lowercase()}
                BEFORE $event ON study_plan_settings
                WHEN trim(NEW.studyPlanId) = ''
                BEGIN
                    SELECT RAISE(ABORT, 'StudyFlow integrity: Study Plan settings identity cannot be blank');
                END
                """.trimIndent(),
            )
        }
    }

    private fun installScopedReferenceTriggers(db: SupportSQLiteDatabase, reference: ScopedReference) {
        val prefix = "guard_${reference.table}_${reference.column}_plan_scope"
        val targetAlias = if (reference.targetTable == reference.table) "parent" else "target"
        val predicate =
            "$targetAlias.${reference.targetColumn} = NEW.${reference.column} " +
                "AND $targetAlias.studyPlanId = NEW.studyPlanId${reference.extraPredicate}"
        val condition =
            "NEW.${reference.column} IS NOT NULL AND NOT EXISTS " +
                "(SELECT 1 FROM ${reference.targetTable} AS $targetAlias WHERE $predicate)"
        listOf("INSERT" to "", "UPDATE" to " OF ${reference.column}, studyPlanId").forEach { (event, columns) ->
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS ${prefix}_${event.lowercase()}
                BEFORE $event$columns ON ${reference.table}
                WHEN $condition
                BEGIN
                    SELECT RAISE(
                        ABORT,
                        'StudyFlow integrity: invalid ${reference.table}.${reference.column} ownership'
                    );
                END
                """.trimIndent(),
            )
        }
    }


    private fun installTopicHierarchyUpdateGuard(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS guard_topics_parent_subject_change
            BEFORE UPDATE OF subjectId ON topics
            WHEN EXISTS (
                SELECT 1 FROM topics AS child
                WHERE child.parentTopicId = OLD.id
                  AND child.studyPlanId = OLD.studyPlanId
                  AND child.subjectId <> NEW.subjectId
            )
            BEGIN
                SELECT RAISE(
                    ABORT,
                    'StudyFlow integrity: a parent Topic cannot move away from its existing children'
                );
            END
            """.trimIndent(),
        )
    }


    private fun installExamStageDeleteCleanup(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS cleanup_exam_stage_subject_topic_refs
            AFTER DELETE ON exam_stages
            BEGIN
                UPDATE subjects
                SET examStageId = NULL
                WHERE studyPlanId = OLD.studyPlanId AND examStageId = OLD.id;
                UPDATE topics
                SET examStageId = NULL
                WHERE studyPlanId = OLD.studyPlanId AND examStageId = OLD.id;
            END
            """.trimIndent(),
        )
    }

    private fun installSingleActiveSessionTriggers(db: SupportSQLiteDatabase) {
        listOf("INSERT" to "", "UPDATE" to " OF status, studyPlanId").forEach { (event, columns) ->
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS guard_study_sessions_single_active_${event.lowercase()}
                BEFORE $event$columns ON study_sessions
                WHEN NEW.status IN ('RUNNING', 'PAUSED')
                  AND EXISTS (
                    SELECT 1 FROM study_sessions AS active
                    WHERE active.studyPlanId = NEW.studyPlanId
                      AND active.status IN ('RUNNING', 'PAUSED')
                      AND active.id <> NEW.id
                  )
                BEGIN
                    SELECT RAISE(ABORT, 'StudyFlow integrity: only one active Study Session is allowed per Study Plan');
                END
                """.trimIndent(),
            )
        }
    }

    private fun installValueGuards(db: SupportSQLiteDatabase) {
        forEachValueGuard { table, predicate ->
            installValueGuard(db, table, predicate)
        }
    }

    private fun verifyValueGuards(db: SupportSQLiteDatabase) {
        forEachValueGuard { table, predicate ->
            val rowPredicate = predicate.replace("NEW.", "row.")
            requireNoRows(
                db = db,
                sql = "SELECT 1 FROM $table AS row WHERE NOT ($rowPredicate) LIMIT 1",
                message = "StudyFlow database corruption: $table contains invalid persisted values.",
            )
        }
    }

    private fun forEachValueGuard(block: (table: String, predicate: String) -> Unit) {
        fun guard(table: String, predicate: String) = block(table, predicate)
        guard(
            table = "study_targets",
            predicate =
                "trim(NEW.organization) <> '' AND trim(NEW.role) <> '' AND trim(NEW.examBoard) <> '' " +
                    "AND (NEW.openings IS NULL OR NEW.openings >= 0) " +
                    "AND (NEW.estimatedCutoffPercent IS NULL OR NEW.estimatedCutoffPercent BETWEEN 0 AND 100)",
        )
        guard(
            table = "exam_stages",
            predicate =
                "trim(NEW.name) <> '' AND NEW.weight >= 0 AND NEW.position >= 0 " +
                    "AND (NEW.minimumScorePercent IS NULL OR NEW.minimumScorePercent BETWEEN 0 AND 100) " +
                    "AND NEW.type IN ('OBJECTIVE', 'WRITTEN', 'ESSAY', 'PRACTICAL', 'PHYSICAL', 'ORAL', " +
                    "'TITLES', 'OTHER')",
        )
        guard(
            table = "study_plans",
            predicate =
                "trim(NEW.id) <> '' AND trim(NEW.name) <> '' " +
                    "AND NEW.type IN ('PUBLIC_EXAMINATION', 'CERTIFICATION', 'UNIVERSITY', " +
                    "'ENTRANCE_EXAMINATION', 'FREE_STUDY') " +
                    "AND NEW.status IN ('DRAFT', 'ACTIVE', 'PAUSED', 'COMPLETED', 'ARCHIVED', 'TRASHED') " +
                    "AND NEW.weeklyGoalHours BETWEEN 1 AND 100 " +
                    "AND NEW.progressPercent BETWEEN 0 AND 100 " +
                    "AND NEW.studiedMinutesThisWeek >= 0 AND NEW.questionsAnswered >= 0 " +
                    "AND NEW.averageAccuracyPercent BETWEEN 0 AND 100",
        )
        guard(
            table = "subjects",
            predicate =
                "trim(NEW.name) <> '' AND NEW.status IN ('NOT_STARTED', 'STUDYING', 'REVIEWING', " +
                    "'MASTERED', 'ARCHIVED') " +
                    "AND NEW.priority BETWEEN 1 AND 5 AND NEW.estimatedMinutes >= 0 " +
                    "AND NEW.progressPercent BETWEEN 0 AND 100 AND NEW.accuracyPercent BETWEEN 0 AND 100 " +
                    "AND NEW.examWeight >= 0 AND NEW.position >= 0 AND NEW.archived IN (0, 1)",
        )
        guard(
            table = "topics",
            predicate =
                "trim(NEW.name) <> '' AND NEW.status IN ('NOT_STARTED', 'STUDYING', 'REVIEWING', " +
                    "'MASTERED', 'ARCHIVED') " +
                    "AND NEW.priority BETWEEN 1 AND 5 AND NEW.difficulty BETWEEN 1 AND 5 " +
                    "AND (NEW.examIncidencePercent IS NULL OR NEW.examIncidencePercent BETWEEN 0 AND 100) " +
                    "AND NEW.estimatedMinutes >= 0 AND NEW.progressPercent BETWEEN 0 AND 100 " +
                    "AND NEW.masteryPercent BETWEEN 0 AND 100 AND NEW.position >= 0 AND NEW.archived IN (0, 1)",
        )
        guard(
            table = "study_strategies",
            predicate =
                "NEW.type IN ('FIXED_WEEKLY', 'STUDY_CYCLE', 'GOAL_BASED', 'ADAPTIVE', 'FREE_GUIDED', " +
                    "'INTENSIVE_SPRINT', 'COUNTDOWN') " +
                    "AND NEW.minimumSessionMinutes BETWEEN 5 AND 240 " +
                    "AND NEW.maximumSessionMinutes BETWEEN NEW.minimumSessionMinutes AND 720 " +
                    "AND NEW.theoryRatioPercent BETWEEN 0 AND 100 " +
                    "AND NEW.questionRatioPercent BETWEEN 0 AND 100 " +
                    "AND NEW.reviewRatioPercent BETWEEN 0 AND 100 " +
                    "AND NEW.theoryRatioPercent + NEW.questionRatioPercent + NEW.reviewRatioPercent = 100",
        )
        guard(
            table = "schedule_rules",
            predicate =
                "NEW.type IN ('FIXED_BLOCK', 'RECURRING_BLOCK', 'CYCLE_SLOT', 'AVAILABILITY', 'DAY_OFF') " +
                    "AND NEW.durationMinutes BETWEEN 1 AND 720 " +
                    "AND (NEW.dayOfWeek IS NULL OR NEW.dayOfWeek BETWEEN 1 AND 7) " +
                    "AND (NEW.startMinuteOfDay IS NULL OR NEW.startMinuteOfDay BETWEEN 0 AND 1439) " +
                    "AND NEW.position >= 0 AND NEW.enabled IN (0, 1) AND NEW.locked IN (0, 1)",
        )
        guard(
            table = "planned_activities",
            predicate =
                "trim(NEW.title) <> '' " +
                    "AND NEW.type IN ('THEORY', 'QUESTIONS', 'REVIEW', 'FLASHCARDS', 'MOCK_EXAM', " +
                    "'WRITING', 'VIDEO', 'READING', 'CUSTOM') " +
                    "AND NEW.status IN ('PLANNED', 'IN_PROGRESS', 'COMPLETED', 'SKIPPED', 'MISSED', 'CANCELLED') " +
                    "AND NEW.plannedDurationMinutes BETWEEN 1 AND 720 AND NEW.locked IN (0, 1)",
        )
        guard(
            table = "study_sessions",
            predicate =
                "NEW.type IN ('THEORY', 'QUESTIONS', 'REVIEW', 'FLASHCARDS', 'MOCK_EXAM', " +
                    "'WRITING', 'VIDEO', 'READING', 'CUSTOM') " +
                    "AND NEW.status IN ('RUNNING', 'PAUSED', 'COMPLETED', 'CANCELLED') " +
                    "AND NEW.grossDurationSeconds >= 0 AND NEW.pauseDurationSeconds >= 0 " +
                    "AND NEW.pagesCompleted >= 0 AND NEW.questionsAnswered >= 0 " +
                    "AND NEW.correctAnswers BETWEEN 0 AND NEW.questionsAnswered " +
                    "AND (NEW.concentrationRating IS NULL OR NEW.concentrationRating BETWEEN 1 AND 5) " +
                    "AND (NEW.difficultyRating IS NULL OR NEW.difficultyRating BETWEEN 1 AND 5) " +
                    "AND (NEW.targetDurationMinutes IS NULL OR NEW.targetDurationMinutes BETWEEN 5 AND 240) " +
                    "AND (NEW.endedAtEpochMillis IS NULL OR NEW.endedAtEpochMillis >= NEW.startedAtEpochMillis)",
        )
        guard(
            table = "questions",
            predicate =
                "trim(NEW.statement) <> '' AND trim(NEW.correctAnswer) <> '' " +
                    "AND NEW.type IN ('MULTIPLE_CHOICE', 'TRUE_FALSE', 'SHORT_ANSWER', 'ESSAY') " +
                    "AND NEW.difficulty BETWEEN 1 AND 5 " +
                    "AND (NEW.year IS NULL OR NEW.year BETWEEN 1900 AND 2200)",
        )
        guard(
            table = "review_items",
            predicate =
                "NEW.method IN ('FIXED_INTERVAL', 'SPACED_REPETITION', 'PERFORMANCE_BASED', " +
                    "'QUESTION_BASED', 'ERROR_BASED', 'MANUAL') " +
                    "AND NEW.status IN ('SCHEDULED', 'DUE', 'COMPLETED', 'SKIPPED', 'CANCELLED') " +
                    "AND NEW.intervalDays >= 0 AND NEW.repetitionCount >= 0 AND NEW.easeFactor > 0",
        )
        guard(
            table = "study_goals",
            predicate =
                "trim(NEW.title) <> '' AND NEW.targetValue > 0 AND NEW.currentValue >= 0 " +
                    "AND NEW.type IN ('STUDY_TIME', 'SESSION_COUNT', 'QUESTION_COUNT', 'ACCURACY', " +
                    "'TOPIC_COMPLETION', 'MOCK_EXAM_COUNT', 'CUSTOM') " +
                    "AND NEW.period IN ('DAILY', 'WEEKLY', 'MONTHLY', 'CUSTOM') AND NEW.archived IN (0, 1)",
        )
        guard(
            table = "study_materials",
            predicate =
                "trim(NEW.title) <> '' AND NEW.completedUnits >= 0 " +
                    "AND (NEW.totalUnits IS NULL OR (NEW.totalUnits >= 0 AND NEW.completedUnits <= NEW.totalUnits)) " +
                    "AND NEW.type IN ('BOOK', 'PDF', 'WEBSITE', 'COURSE', 'NOTE', 'VIDEO', 'LOCAL_FILE', 'OTHER') " +
                    "AND NEW.`primary` IN (0, 1) AND NEW.favorite IN (0, 1) AND NEW.archived IN (0, 1)",
        )
        guard(
            table = "video_resources",
            predicate =
                "trim(NEW.title) <> '' AND trim(NEW.sourceUri) <> '' AND NEW.playbackPositionMillis >= 0 " +
                    "AND (NEW.durationMillis IS NULL OR NEW.durationMillis >= 0) " +
                    "AND NEW.sourceType IN ('YOUTUBE', 'DIRECT_MEDIA', 'WEB_PAGE', 'LOCAL_FILE') " +
                    "AND NEW.completed IN (0, 1)",
        )
        guard(
            table = "annexes",
            predicate =
                "trim(NEW.displayName) <> '' AND trim(NEW.mimeType) <> '' AND NEW.byteSize >= 0 " +
                    "AND trim(NEW.storageReference) <> '' " +
                    "AND NEW.storageMode IN ('IMPORTED_COPY', 'LINKED_DOCUMENT') " +
                    "AND NEW.persistedPermission IN (0, 1)",
        )
        guard(
            table = "study_tests",
            predicate =
                "trim(NEW.title) <> '' AND NEW.questionCount >= 0 " +
                    "AND NEW.answeredCount BETWEEN 0 AND NEW.questionCount " +
                    "AND NEW.correctCount BETWEEN 0 AND NEW.answeredCount " +
                    "AND (NEW.timeLimitSeconds IS NULL OR NEW.timeLimitSeconds > 0) " +
                    "AND NEW.mode IN ('PRACTICE', 'IMMEDIATE_FEEDBACK', 'EXAM')",
        )
        guard(
            table = "mock_exams",
            predicate =
                "trim(NEW.title) <> '' AND NEW.timeLimitSeconds > 0 AND NEW.questionCount > 0 " +
                    "AND NEW.answeredCount BETWEEN 0 AND NEW.questionCount " +
                    "AND NEW.correctCount BETWEEN 0 AND NEW.answeredCount " +
                    "AND (NEW.scorePercent IS NULL OR NEW.scorePercent BETWEEN 0 AND 100) " +
                    "AND NEW.negativeMarking >= 0 " +
                    "AND (NEW.minimumScorePercent IS NULL OR NEW.minimumScorePercent BETWEEN 0 AND 100) " +
                    "AND NEW.mode IN ('PRACTICE', 'IMMEDIATE_FEEDBACK', 'EXAM')",
        )
        guard(
            table = "flashcards",
            predicate =
                "trim(NEW.front) <> '' AND trim(NEW.back) <> '' AND NEW.type IN ('BASIC', 'CLOZE') " +
                    "AND NEW.suspended IN (0, 1)",
        )
        guard(
            table = "error_entries",
            predicate =
                "NEW.recurrenceCount >= 0 AND NEW.category IN ('CONTENT_GAP', 'FORGOTTEN_CONTENT', " +
                    "'CONCEPT_CONFUSION', 'INTERPRETATION', 'DISTRACTION', 'CALCULATION', 'TIME_PRESSURE', " +
                    "'DISPUTED_QUESTION', 'OTHER')",
        )
        guard(
            table = "study_notes",
            predicate =
                "trim(NEW.title) <> '' AND NEW.type IN ('FREEFORM', 'SUMMARY', 'FORMULA', 'LEGISLATION', " +
                    "'MIND_MAP', 'CHECKLIST')",
        )
        guard(
            table = "plan_snapshots",
            predicate =
                "trim(NEW.label) <> '' AND NEW.schemaVersion > 0 AND trim(NEW.payloadReference) <> '' " +
                    "AND trim(NEW.checksumSha256) <> '' " +
                    "AND NEW.kind IN ('AUTOMATIC', 'MANUAL', 'PRE_IMPORT', 'PRE_RECALCULATION', 'PRE_DELETION')",
        )
        guard(
            table = "study_plan_settings",
            predicate =
                "NEW.preferredSessionMinutes BETWEEN 5 AND 240 " +
                    "AND (NEW.quietHoursStartMinute IS NULL OR NEW.quietHoursStartMinute BETWEEN 0 AND 1439) " +
                    "AND (NEW.quietHoursEndMinute IS NULL OR NEW.quietHoursEndMinute BETWEEN 0 AND 1439) " +
                    "AND NEW.adaptivePlanningEnabled IN (0, 1) " +
                    "AND NEW.reviewNotificationsEnabled IN (0, 1) " +
                    "AND NEW.strictExamModeEnabled IN (0, 1)",
        )

    }

    private fun installValueGuard(
        db: SupportSQLiteDatabase,
        table: String,
        predicate: String,
    ) {
        listOf("INSERT", "UPDATE").forEach { event ->
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS guard_${table}_values_${event.lowercase()}
                BEFORE $event ON $table
                WHEN NOT ($predicate)
                BEGIN
                    SELECT RAISE(ABORT, 'StudyFlow integrity: invalid $table values');
                END
                """.trimIndent(),
            )
        }
    }
}
