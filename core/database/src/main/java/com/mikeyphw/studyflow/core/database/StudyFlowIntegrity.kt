package com.mikeyphw.studyflow.core.database

import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Durable SQLite invariants for the complete StudyFlow graph.
 *
 * Repository validation remains the friendly boundary, but imports, migrations and direct SQL must
 * obey the same ownership/revision rules. The managed trigger set is explicitly versioned and the
 * schema migration replaces stale trigger definitions instead of relying on CREATE TRIGGER IF NOT EXISTS.
 */
object StudyFlowIntegrity {
    const val GraphRevisionMetadataKey = "graph_revision"
    const val IntegrityTriggerSpecMetadataKey = "integrity_trigger_spec"
    const val IntegrityTriggerSpecVersion = 14

    private data class PlanOwnedTable(
        val table: String,
        val identityColumn: String = "id",
    )

    private data class ScopedReference(
        val table: String,
        val column: String,
        val targetTable: String,
        val targetColumn: String = "id",
        val extraPredicate: String = "",
    )

    private val planOwnedTables = listOf(
        PlanOwnedTable("study_targets"),
        PlanOwnedTable("exam_stages"),
        PlanOwnedTable("exam_notice_revisions"),
        PlanOwnedTable("legislation_documents"),
        PlanOwnedTable("legislation_provisions"),
        PlanOwnedTable("legislation_evidence_links"),
        PlanOwnedTable("subjects"),
        PlanOwnedTable("topics"),
        PlanOwnedTable("study_strategies"),
        PlanOwnedTable("study_cycles"),
        PlanOwnedTable("study_cycle_items"),
        PlanOwnedTable("schedule_rules"),
        PlanOwnedTable("planned_activities"),
        PlanOwnedTable("study_sessions"),
        PlanOwnedTable("study_goals"),
        PlanOwnedTable("study_materials"),
        PlanOwnedTable("video_resources"),
        PlanOwnedTable("annexes"),
        PlanOwnedTable("questions"),
        PlanOwnedTable("study_tests"),
        PlanOwnedTable("mock_exams"),
        PlanOwnedTable("flashcards"),
        PlanOwnedTable("error_entries"),
        PlanOwnedTable("review_items"),
        PlanOwnedTable("study_notes"),
        PlanOwnedTable("plan_snapshots"),
        PlanOwnedTable("programming_course_profiles"),
        PlanOwnedTable("code_snippets"),
        PlanOwnedTable("coding_exercises"),
        PlanOwnedTable("mini_projects"),
        PlanOwnedTable("termux_runtime_preferences", identityColumn = "studyPlanId"),
        PlanOwnedTable("termux_command_requests"),
        PlanOwnedTable("language_deck_profiles"),
        PlanOwnedTable("language_expressions"),
        PlanOwnedTable("language_review_attempts"),
    )

    /** Tables whose mutation must advance the logical graph revision. */
    private val revisionOnlyTables = listOf(
        // These rows inherit ownership through their parent but are still user-data mutations.
        "termux_command_results",
        "idiom_cards",
        "vocabulary_cards",
    )

    private val scopedReferences = listOf(
        ScopedReference("exam_stages", "studyTargetId", "study_targets"),
        ScopedReference("exam_notice_revisions", "studyTargetId", "study_targets"),
        ScopedReference("legislation_provisions", "legislationDocumentId", "legislation_documents"),
        ScopedReference("legislation_evidence_links", "provisionId", "legislation_provisions"),
        ScopedReference("legislation_evidence_links", "questionId", "questions"),
        ScopedReference("legislation_evidence_links", "errorEntryId", "error_entries"),
        ScopedReference("legislation_evidence_links", "reviewItemId", "review_items"),
        ScopedReference("subjects", "examStageId", "exam_stages"),
        ScopedReference("topics", "subjectId", "subjects"),
        ScopedReference(
            table = "topics",
            column = "parentTopicId",
            targetTable = "topics",
            extraPredicate = " AND parent.subjectId = NEW.subjectId",
        ),
        ScopedReference("topics", "examStageId", "exam_stages"),
        ScopedReference("study_cycle_items", "studyCycleId", "study_cycles"),
        ScopedReference("study_cycle_items", "subjectId", "subjects"),
        ScopedReference("study_cycle_items", "topicId", "topics"),
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
        ScopedReference("questions", "examStageId", "exam_stages"),
        ScopedReference("questions", "examNoticeRevisionId", "exam_notice_revisions"),
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
        ScopedReference("code_snippets", "subjectId", "subjects"),
        ScopedReference("code_snippets", "topicId", "topics"),
        ScopedReference("coding_exercises", "subjectId", "subjects"),
        ScopedReference("coding_exercises", "topicId", "topics"),
        ScopedReference("coding_exercises", "snippetId", "code_snippets"),
        ScopedReference("mini_projects", "subjectId", "subjects"),
        ScopedReference("mini_projects", "topicId", "topics"),
        ScopedReference("termux_command_requests", "exerciseId", "coding_exercises"),
        ScopedReference("language_expressions", "deckId", "language_deck_profiles"),
        ScopedReference("language_review_attempts", "expressionId", "language_expressions"),
        ScopedReference("language_review_attempts", "reviewItemId", "review_items"),
    )

    private val topicSubjectCoherenceTables = listOf(
        "code_snippets",
        "coding_exercises",
        "mini_projects",
        "study_cycle_items",
    )

    fun install(db: SupportSQLiteDatabase) {
        ensureMetadata(db)
        planOwnedTables.filter { spec -> tableExists(db, spec.table) }.forEach { spec ->
            installStablePlanOwnerTrigger(db, spec)
        }
        installPlanSettingsIdentityTriggers(db)
        scopedReferences
            .filter { reference -> tableExists(db, reference.table) && tableExists(db, reference.targetTable) }
            .forEach { reference -> installScopedReferenceTriggers(db, reference) }
        topicSubjectCoherenceTables.filter { tableExists(db, it) }.forEach { table ->
            installTopicSubjectCoherenceTriggers(db, table)
        }
        installLanguageAttemptCardCoherenceTriggers(db)
        installStudyCycleCurrentItemTriggers(db)
        installTopicHierarchyUpdateGuard(db)
        installExamStageDeleteCleanup(db)
        installSingleActiveSessionTriggers(db)
        installValueGuards(db)
        installLegislationEvidenceTargetTriggers(db)
        installGraphRevisionTriggers(db)
        db.execSQL(
            "INSERT OR REPLACE INTO database_metadata (`key`, value) VALUES " +
                "('$IntegrityTriggerSpecMetadataKey', '$IntegrityTriggerSpecVersion')",
        )
        StudyFlowSearchIndex.install(db)
    }

    /** Explicit replacement path used by schema migrations when trigger semantics change. */
    fun reinstall(db: SupportSQLiteDatabase) {
        dropManagedTriggers(db)
        install(db)
    }

    fun verifyExistingData(db: SupportSQLiteDatabase) {
        db.query("PRAGMA foreign_key_check").use { cursor ->
            check(!cursor.moveToFirst()) {
                "StudyFlow database corruption: foreign-key violations must be repaired before integrity upgrade."
            }
        }
        planOwnedTables.filter { spec -> tableExists(db, spec.table) }.forEach { spec ->
            val predicate = identityBlankPredicate(spec, alias = null)
            requireNoRows(
                db = db,
                sql = "SELECT 1 FROM ${spec.table} WHERE $predicate LIMIT 1",
                message = "StudyFlow database corruption: ${spec.table} contains a blank identity.",
            )
        }
        if (tableExists(db, "study_plan_settings")) {
            requireNoRows(
                db = db,
                sql = "SELECT 1 FROM study_plan_settings WHERE trim(studyPlanId) = '' LIMIT 1",
                message = "StudyFlow database corruption: Study Plan settings contain a blank identity.",
            )
        }
        scopedReferences
            .filter { reference -> tableExists(db, reference.table) && tableExists(db, reference.targetTable) }
            .forEach { reference -> verifyScopedReference(db, reference) }
        topicSubjectCoherenceTables.filter { tableExists(db, it) }.forEach { table ->
            verifyTopicSubjectCoherence(db, table)
        }
        verifyLanguageAttemptCardCoherence(db)
        verifyStudyCycleCurrentItem(db)
        verifyValueGuards(db)
        verifyLegislationEvidenceTargets(db)
        requireNoRows(
            db = db,
            sql =
                "SELECT 1 FROM study_sessions WHERE status IN ('RUNNING', 'PAUSED') " +
                    "LIMIT 1 OFFSET 1",
            message = "StudyFlow database corruption: multiple active Study Sessions exist globally.",
        )
    }

    /** Shared diagnostic truth: the debug health report uses exactly the same modern graph rules. */
    fun logicalIntegrityViolationCount(db: SupportSQLiteDatabase): Int {
        var total = 0L
        fun addCount(sql: String) {
            db.query(sql).use { cursor ->
                if (cursor.moveToFirst()) total += cursor.getLong(0).coerceAtLeast(0L)
            }
        }
        planOwnedTables.filter { spec -> tableExists(db, spec.table) }.forEach { spec ->
            addCount("SELECT COUNT(*) FROM ${spec.table} WHERE ${identityBlankPredicate(spec, alias = null)}")
        }
        if (tableExists(db, "study_plan_settings")) {
            addCount("SELECT COUNT(*) FROM study_plan_settings WHERE trim(studyPlanId) = ''")
        }
        scopedReferences
            .filter { reference -> tableExists(db, reference.table) && tableExists(db, reference.targetTable) }
            .forEach { reference -> addCount(scopedReferenceViolationCountSql(reference)) }
        topicSubjectCoherenceTables.filter { tableExists(db, it) }.forEach { table ->
            addCount(topicSubjectCoherenceViolationCountSql(table))
        }
        if (tableExists(db, "language_review_attempts") && tableExists(db, "idiom_cards")) {
            addCount(languageAttemptCardViolationCountSql())
        }
        if (tableExists(db, "study_cycles") && tableExists(db, "study_cycle_items")) {
            addCount(studyCycleCurrentItemViolationCountSql())
        }
        if (tableExists(db, "legislation_evidence_links")) {
            addCount(
                "SELECT COUNT(*) FROM legislation_evidence_links WHERE " +
                    "((questionId IS NOT NULL) + (errorEntryId IS NOT NULL) + (reviewItemId IS NOT NULL)) <> 1",
            )
        }
        if (tableExists(db, "study_sessions")) {
            addCount(
                "SELECT CASE WHEN COUNT(*) > 1 THEN COUNT(*) - 1 ELSE 0 END " +
                    "FROM study_sessions WHERE status IN ('RUNNING', 'PAUSED')",
            )
        }
        forEachValueGuard { table, predicate ->
            if (tableExists(db, table)) {
                addCount("SELECT COUNT(*) FROM $table AS row WHERE NOT (${predicate.replace("NEW.", "row.")})")
            }
        }
        return total.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    private fun ensureMetadata(db: SupportSQLiteDatabase) {
        if (!tableExists(db, "database_metadata")) return
        db.execSQL(
            "INSERT OR IGNORE INTO database_metadata (`key`, value) VALUES ('$GraphRevisionMetadataKey', '0')",
        )
    }

    private fun installGraphRevisionTriggers(db: SupportSQLiteDatabase) {
        ensureMetadata(db)
        val revisionTables = (
            listOf("study_plans") + planOwnedTables.map(PlanOwnedTable::table) +
                listOf("study_plan_settings") + revisionOnlyTables
            ).distinct().filter { tableExists(db, it) }
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

    private fun dropManagedTriggers(db: SupportSQLiteDatabase) {
        val names = db.query(
            "SELECT name FROM sqlite_master WHERE type='trigger' AND (" +
                "name LIKE 'guard_%' OR " +
                "name LIKE 'studyflow_graph_revision_%' OR " +
                "name = 'cleanup_exam_stage_subject_topic_refs')",
        ).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }
        names.forEach { name ->
            val quoted = name.replace("\"", "\"\"")
            db.execSQL("DROP TRIGGER IF EXISTS \"$quoted\"")
        }
    }

    private fun verifyScopedReference(db: SupportSQLiteDatabase, reference: ScopedReference) {
        requireNoRows(
            db = db,
            sql = scopedReferenceViolationSelectSql(reference),
            message = "StudyFlow database corruption: invalid ${reference.table}.${reference.column} ownership.",
        )
    }

    private fun scopedReferenceViolationSelectSql(reference: ScopedReference): String =
        scopedReferenceViolationSql(reference, count = false)

    private fun scopedReferenceViolationCountSql(reference: ScopedReference): String =
        scopedReferenceViolationSql(reference, count = true)

    private fun scopedReferenceViolationSql(reference: ScopedReference, count: Boolean): String {
        val targetAlias = if (reference.targetTable == reference.table) "parent" else "target"
        val extraPredicate = reference.extraPredicate.replace("NEW.", "child.")
        val predicate =
            "$targetAlias.${reference.targetColumn} = child.${reference.column} " +
                "AND $targetAlias.studyPlanId = child.studyPlanId$extraPredicate"
        val projection = if (count) "COUNT(*)" else "1"
        val limit = if (count) "" else " LIMIT 1"
        return "SELECT $projection FROM ${reference.table} AS child " +
            "WHERE child.${reference.column} IS NOT NULL AND NOT EXISTS " +
            "(SELECT 1 FROM ${reference.targetTable} AS $targetAlias WHERE $predicate)$limit"
    }

    private fun installStablePlanOwnerTrigger(db: SupportSQLiteDatabase, spec: PlanOwnedTable) {
        val table = spec.table
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
                WHEN ${identityBlankPredicate(spec, alias = "NEW")}
                BEGIN
                    SELECT RAISE(ABORT, 'StudyFlow integrity: $table identity cannot be blank');
                END
                """.trimIndent(),
            )
        }
    }

    private fun identityBlankPredicate(spec: PlanOwnedTable, alias: String?): String {
        val prefix = alias?.let { "$it." }.orEmpty()
        val plan = "trim(${prefix}studyPlanId) = ''"
        return if (spec.identityColumn == "studyPlanId") plan else "trim(${prefix}${spec.identityColumn}) = '' OR $plan"
    }

    private fun installPlanSettingsIdentityTriggers(db: SupportSQLiteDatabase) {
        if (!tableExists(db, "study_plan_settings")) return
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

    private fun installTopicSubjectCoherenceTriggers(db: SupportSQLiteDatabase, table: String) {
        val condition =
            "NEW.topicId IS NOT NULL AND NEW.subjectId IS NOT NULL AND NOT EXISTS (" +
                "SELECT 1 FROM topics AS topic WHERE topic.id = NEW.topicId " +
                "AND topic.studyPlanId = NEW.studyPlanId AND topic.subjectId = NEW.subjectId)"
        listOf("INSERT" to "", "UPDATE" to " OF topicId, subjectId, studyPlanId").forEach { (event, columns) ->
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS guard_${table}_topic_subject_coherence_${event.lowercase()}
                BEFORE $event$columns ON $table
                WHEN $condition
                BEGIN
                    SELECT RAISE(ABORT, 'StudyFlow integrity: $table topic and subject ownership disagree');
                END
                """.trimIndent(),
            )
        }
    }

    private fun verifyTopicSubjectCoherence(db: SupportSQLiteDatabase, table: String) {
        requireNoRows(
            db = db,
            sql = topicSubjectCoherenceViolationSelectSql(table),
            message = "StudyFlow database corruption: $table topic and subject ownership disagree.",
        )
    }

    private fun topicSubjectCoherenceViolationSelectSql(table: String): String =
        topicSubjectCoherenceViolationSql(table, count = false)

    private fun topicSubjectCoherenceViolationCountSql(table: String): String =
        topicSubjectCoherenceViolationSql(table, count = true)

    private fun topicSubjectCoherenceViolationSql(table: String, count: Boolean): String {
        val projection = if (count) "COUNT(*)" else "1"
        val limit = if (count) "" else " LIMIT 1"
        return "SELECT $projection FROM $table AS child WHERE child.topicId IS NOT NULL " +
            "AND child.subjectId IS NOT NULL AND NOT EXISTS (SELECT 1 FROM topics AS topic " +
            "WHERE topic.id = child.topicId AND topic.studyPlanId = child.studyPlanId " +
            "AND topic.subjectId = child.subjectId)$limit"
    }

    private fun installLanguageAttemptCardCoherenceTriggers(db: SupportSQLiteDatabase) {
        if (!tableExists(db, "language_review_attempts") || !tableExists(db, "idiom_cards")) return
        val condition =
            "NEW.idiomCardId IS NOT NULL AND NOT EXISTS (SELECT 1 FROM idiom_cards AS card " +
                "WHERE card.id = NEW.idiomCardId AND card.expressionId = NEW.expressionId)"
        listOf("INSERT" to "", "UPDATE" to " OF idiomCardId, expressionId").forEach { (event, columns) ->
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS guard_language_review_attempts_idiom_card_${event.lowercase()}
                BEFORE $event$columns ON language_review_attempts
                WHEN $condition
                BEGIN
                    SELECT RAISE(ABORT, 'StudyFlow integrity: language review card belongs to another expression');
                END
                """.trimIndent(),
            )
        }
    }

    private fun verifyLanguageAttemptCardCoherence(db: SupportSQLiteDatabase) {
        if (!tableExists(db, "language_review_attempts") || !tableExists(db, "idiom_cards")) return
        requireNoRows(
            db = db,
            sql = languageAttemptCardViolationSelectSql(),
            message = "StudyFlow database corruption: language review card belongs to another expression.",
        )
    }

    private fun languageAttemptCardViolationSelectSql(): String =
        languageAttemptCardViolationSql(count = false)

    private fun languageAttemptCardViolationCountSql(): String =
        languageAttemptCardViolationSql(count = true)

    private fun languageAttemptCardViolationSql(count: Boolean): String {
        val projection = if (count) "COUNT(*)" else "1"
        val limit = if (count) "" else " LIMIT 1"
        return "SELECT $projection FROM language_review_attempts AS attempt " +
            "WHERE attempt.idiomCardId IS NOT NULL AND NOT EXISTS (SELECT 1 FROM idiom_cards AS card " +
            "WHERE card.id = attempt.idiomCardId AND card.expressionId = attempt.expressionId)$limit"
    }

    private fun installStudyCycleCurrentItemTriggers(db: SupportSQLiteDatabase) {
        if (!tableExists(db, "study_cycles") || !tableExists(db, "study_cycle_items")) return
        val invalidPointer =
            "NEW.currentItemId IS NOT NULL AND NOT EXISTS (SELECT 1 FROM study_cycle_items AS item " +
                "WHERE item.id = NEW.currentItemId AND item.studyCycleId = NEW.id " +
                "AND item.studyPlanId = NEW.studyPlanId)"
        listOf("INSERT" to "", "UPDATE" to " OF currentItemId, id, studyPlanId").forEach { (event, columns) ->
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS guard_study_cycles_current_item_${event.lowercase()}
                BEFORE $event$columns ON study_cycles
                WHEN $invalidPointer
                BEGIN
                    SELECT RAISE(ABORT, 'StudyFlow integrity: study cycle current item is not owned by the cycle');
                END
                """.trimIndent(),
            )
        }
        db.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS guard_study_cycle_items_current_membership_update
            BEFORE UPDATE OF id, studyCycleId, studyPlanId ON study_cycle_items
            WHEN EXISTS (
                SELECT 1 FROM study_cycles AS cycle
                WHERE cycle.id = OLD.studyCycleId
                  AND cycle.studyPlanId = OLD.studyPlanId
                  AND cycle.currentItemId = OLD.id
            ) AND (NEW.id <> OLD.id OR NEW.studyCycleId <> OLD.studyCycleId OR NEW.studyPlanId <> OLD.studyPlanId)
            BEGIN
                SELECT RAISE(ABORT, 'StudyFlow integrity: current study cycle item cannot move between cycles');
            END
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS guard_study_cycle_items_current_delete_cleanup
            AFTER DELETE ON study_cycle_items
            WHEN EXISTS (
                SELECT 1 FROM study_cycles AS cycle
                WHERE cycle.id = OLD.studyCycleId
                  AND cycle.studyPlanId = OLD.studyPlanId
                  AND cycle.currentItemId = OLD.id
            )
            BEGIN
                UPDATE study_cycles
                SET currentItemId = NULL
                WHERE id = OLD.studyCycleId AND studyPlanId = OLD.studyPlanId AND currentItemId = OLD.id;
            END
            """.trimIndent(),
        )
    }

    private fun verifyStudyCycleCurrentItem(db: SupportSQLiteDatabase) {
        if (!tableExists(db, "study_cycles") || !tableExists(db, "study_cycle_items")) return
        requireNoRows(
            db = db,
            sql = studyCycleCurrentItemViolationSelectSql(),
            message = "StudyFlow database corruption: study cycle current item is dangling or foreign.",
        )
    }

    private fun studyCycleCurrentItemViolationSelectSql(): String =
        studyCycleCurrentItemViolationSql(count = false)

    private fun studyCycleCurrentItemViolationCountSql(): String =
        studyCycleCurrentItemViolationSql(count = true)

    private fun studyCycleCurrentItemViolationSql(count: Boolean): String {
        val projection = if (count) "COUNT(*)" else "1"
        val limit = if (count) "" else " LIMIT 1"
        return "SELECT $projection FROM study_cycles AS cycle WHERE cycle.currentItemId IS NOT NULL " +
            "AND NOT EXISTS (SELECT 1 FROM study_cycle_items AS item WHERE item.id = cycle.currentItemId " +
            "AND item.studyCycleId = cycle.id AND item.studyPlanId = cycle.studyPlanId)$limit"
    }

    private fun installTopicHierarchyUpdateGuard(db: SupportSQLiteDatabase) {
        if (!tableExists(db, "topics")) return
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
        if (!tableExists(db, "exam_stages")) return
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
        if (!tableExists(db, "study_sessions")) return
        listOf("INSERT" to "", "UPDATE" to " OF status, studyPlanId").forEach { (event, columns) ->
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS guard_study_sessions_single_active_${event.lowercase()}
                BEFORE $event$columns ON study_sessions
                WHEN NEW.status IN ('RUNNING', 'PAUSED')
                  AND EXISTS (
                    SELECT 1 FROM study_sessions AS active
                    WHERE active.status IN ('RUNNING', 'PAUSED')
                      AND active.id <> NEW.id
                  )
                BEGIN
                    SELECT RAISE(ABORT, 'StudyFlow integrity: only one active Study Session is allowed globally');
                END
                """.trimIndent(),
            )
        }
    }

    private fun installValueGuards(db: SupportSQLiteDatabase) {
        forEachValueGuard { table, predicate ->
            if (tableExists(db, table)) installValueGuard(db, table, predicate)
        }
    }

    private fun verifyValueGuards(db: SupportSQLiteDatabase) {
        forEachValueGuard { table, predicate ->
            if (tableExists(db, table)) {
                val rowPredicate = predicate.replace("NEW.", "row.")
                requireNoRows(
                    db = db,
                    sql = "SELECT 1 FROM $table AS row WHERE NOT ($rowPredicate) LIMIT 1",
                    message = "StudyFlow database corruption: $table contains invalid persisted values.",
                )
            }
        }
    }

    private fun installLegislationEvidenceTargetTriggers(db: SupportSQLiteDatabase) {
        if (!tableExists(db, "legislation_evidence_links")) return
        listOf("INSERT", "UPDATE").forEach { event ->
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS guard_legislation_evidence_exactly_one_${event.lowercase()}
                BEFORE $event ON legislation_evidence_links
                WHEN ((NEW.questionId IS NOT NULL) + (NEW.errorEntryId IS NOT NULL) +
                    (NEW.reviewItemId IS NOT NULL)) <> 1
                BEGIN
                    SELECT RAISE(ABORT, 'StudyFlow integrity: legislation evidence requires exactly one target');
                END
                """.trimIndent(),
            )
        }
    }

    private fun verifyLegislationEvidenceTargets(db: SupportSQLiteDatabase) {
        if (!tableExists(db, "legislation_evidence_links")) return
        requireNoRows(
            db = db,
            sql =
                "SELECT 1 FROM legislation_evidence_links WHERE " +
                    "((questionId IS NOT NULL) + (errorEntryId IS NOT NULL) + " +
                    "(reviewItemId IS NOT NULL)) <> 1 LIMIT 1",
            message = "StudyFlow database corruption: legislation evidence must reference exactly one target.",
        )
    }

    private fun tableExists(db: SupportSQLiteDatabase, table: String): Boolean =
        db.query(
            "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ? LIMIT 1",
            arrayOf(table),
        ).use { cursor -> cursor.moveToFirst() }

    private fun requireNoRows(
        db: SupportSQLiteDatabase,
        sql: String,
        message: String,
    ) {
        db.query(sql).use { cursor -> check(!cursor.moveToFirst()) { message } }
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
                    "AND (NEW.targetDurationMinutes IS NULL OR NEW.targetDurationMinutes BETWEEN 5 AND 720) " +
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
