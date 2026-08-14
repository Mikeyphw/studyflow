package com.mikeyphw.studyflow.core.database

import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * App-maintained FTS index for global discovery.
 *
 * It is deliberately outside Room's entity schema: base entities remain the source of truth and
 * every search hit is rejoined to current base rows before it can be returned to the UI.
 */
object StudyFlowSearchIndex {
    const val TableName = "studyflow_search_fts"
    private const val MetadataKey = "search_fts_schema"
    private const val MetadataVersion = "1"

    fun install(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE VIRTUAL TABLE IF NOT EXISTS $TableName USING fts4(" +
                "type, entityId, planId, content, tokenize=unicode61)",
        )
        installStandardTriggers(db)
        installQuestionErrorRefreshTrigger(db)
        rebuildIfNeeded(db)
    }

    private fun rebuildIfNeeded(db: SupportSQLiteDatabase) {
        val version = db.query(
            "SELECT value FROM database_metadata WHERE `key` = ? LIMIT 1",
            arrayOf(MetadataKey),
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        if (version == MetadataVersion) return

        db.beginTransaction()
        try {
            db.execSQL("DELETE FROM $TableName")
            seedStatements.forEach(db::execSQL)
            db.execSQL(
                "INSERT OR REPLACE INTO database_metadata (`key`, value) VALUES (?, ?)",
                arrayOf(MetadataKey, MetadataVersion),
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun installStandardTriggers(db: SupportSQLiteDatabase) {
        indexSpecs.forEach { spec ->
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS studyflow_search_${spec.table}_insert
                AFTER INSERT ON ${spec.table}
                BEGIN
                    DELETE FROM $TableName WHERE type = '${spec.type}' AND entityId = NEW.id;
                    INSERT INTO $TableName(type, entityId, planId, content)
                    VALUES ('${spec.type}', NEW.id, ${spec.planIdNew}, ${spec.contentNew});
                END
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS studyflow_search_${spec.table}_update
                AFTER UPDATE ON ${spec.table}
                BEGIN
                    DELETE FROM $TableName WHERE type = '${spec.type}' AND entityId = OLD.id;
                    INSERT INTO $TableName(type, entityId, planId, content)
                    VALUES ('${spec.type}', NEW.id, ${spec.planIdNew}, ${spec.contentNew});
                END
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS studyflow_search_${spec.table}_delete
                AFTER DELETE ON ${spec.table}
                BEGIN
                    DELETE FROM $TableName WHERE type = '${spec.type}' AND entityId = OLD.id;
                END
                """.trimIndent(),
            )
        }
    }

    private fun installQuestionErrorRefreshTrigger(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS studyflow_search_questions_refresh_errors
            AFTER UPDATE OF statement ON questions
            BEGIN
                DELETE FROM $TableName
                WHERE type = 'ERROR' AND entityId IN (
                    SELECT id FROM error_entries
                    WHERE studyPlanId = NEW.studyPlanId AND questionId = NEW.id
                );
                INSERT INTO $TableName(type, entityId, planId, content)
                SELECT 'ERROR', e.id, e.studyPlanId,
                    coalesce(NEW.statement, '') || ' ' || e.explanation || ' ' || e.category || ' ' ||
                    coalesce(e.chosenAnswer, '') || ' ' || coalesce(e.correctAnswer, '')
                FROM error_entries e
                WHERE e.studyPlanId = NEW.studyPlanId AND e.questionId = NEW.id;
            END
            """.trimIndent(),
        )
    }

    private data class IndexSpec(
        val table: String,
        val type: String,
        val planIdNew: String,
        val contentNew: String,
    )

    private val indexSpecs = listOf(
        IndexSpec("study_plans", "PLAN", "NEW.id", "NEW.name || ' ' || NEW.description"),
        IndexSpec("subjects", "SUBJECT", "NEW.studyPlanId", "NEW.name || ' ' || NEW.description"),
        IndexSpec("topics", "TOPIC", "NEW.studyPlanId", "NEW.name || ' ' || NEW.description"),
        IndexSpec(
            "questions",
            "QUESTION",
            "NEW.studyPlanId",
            "NEW.statement || ' ' || NEW.explanation || ' ' || coalesce(NEW.source, '')",
        ),
        IndexSpec(
            "study_notes",
            "NOTE",
            "NEW.studyPlanId",
            "NEW.title || ' ' || NEW.content || ' ' || NEW.tagsJson",
        ),
        IndexSpec(
            "study_materials",
            "MATERIAL",
            "NEW.studyPlanId",
            "NEW.title || ' ' || NEW.description || ' ' || NEW.tagsJson",
        ),
        IndexSpec(
            "flashcards",
            "FLASHCARD",
            "NEW.studyPlanId",
            "NEW.front || ' ' || NEW.back || ' ' || coalesce(NEW.clozeText, '')",
        ),
        IndexSpec(
            "error_entries",
            "ERROR",
            "NEW.studyPlanId",
            "coalesce((SELECT statement FROM questions q WHERE q.id = NEW.questionId " +
                "AND q.studyPlanId = NEW.studyPlanId), '') || ' ' || NEW.explanation || ' ' || NEW.category || ' ' || " +
                "coalesce(NEW.chosenAnswer, '') || ' ' || coalesce(NEW.correctAnswer, '')",
        ),
    )

    private val seedStatements = listOf(
        "INSERT INTO $TableName SELECT 'PLAN', id, id, name || ' ' || description FROM study_plans",
        "INSERT INTO $TableName SELECT 'SUBJECT', id, studyPlanId, name || ' ' || description FROM subjects",
        "INSERT INTO $TableName SELECT 'TOPIC', id, studyPlanId, name || ' ' || description FROM topics",
        "INSERT INTO $TableName SELECT 'QUESTION', id, studyPlanId, statement || ' ' || explanation || ' ' || coalesce(source, '') FROM questions",
        "INSERT INTO $TableName SELECT 'NOTE', id, studyPlanId, title || ' ' || content || ' ' || tagsJson FROM study_notes",
        "INSERT INTO $TableName SELECT 'MATERIAL', id, studyPlanId, title || ' ' || description || ' ' || tagsJson FROM study_materials",
        "INSERT INTO $TableName SELECT 'FLASHCARD', id, studyPlanId, front || ' ' || back || ' ' || coalesce(clozeText, '') FROM flashcards",
        """INSERT INTO $TableName
            SELECT 'ERROR', e.id, e.studyPlanId,
                coalesce(q.statement, '') || ' ' || e.explanation || ' ' || e.category || ' ' ||
                coalesce(e.chosenAnswer, '') || ' ' || coalesce(e.correctAnswer, '')
            FROM error_entries e
            LEFT JOIN questions q ON q.id = e.questionId AND q.studyPlanId = e.studyPlanId""".trimIndent(),
    )
}
