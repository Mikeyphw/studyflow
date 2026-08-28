package com.mikeyphw.studyflow.core.database

import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Copies every database-owned child of a Study Plan while remapping identifiers.
 *
 * New identifiers use `<new-plan-id>:<old-id>`. The prefix makes collisions impossible
 * across duplicated plans and keeps every copied foreign key inside the duplicated graph.
 */
class StudyPlanGraphDuplicator(
    private val database: SupportSQLiteDatabase,
) {
    fun duplicate(
        sourcePlanId: String,
        newPlanId: String,
    ) {
        require(sourcePlanId.isNotBlank()) { "Source Study Plan id cannot be blank." }
        require(newPlanId.isNotBlank()) { "New Study Plan id cannot be blank." }
        require(sourcePlanId != newPlanId) { "A Study Plan graph cannot be copied onto itself." }

        TableSpecs.forEach { spec -> duplicateTable(spec, sourcePlanId, newPlanId) }
    }

    private fun duplicateTable(
        spec: TableSpec,
        sourcePlanId: String,
        newPlanId: String,
    ) {
        val arguments = mutableListOf<Any>()
        val selections = spec.columns.map { column ->
            val quotedColumn = quoteIdentifier(column)
            when {
                column == "studyPlanId" -> {
                    arguments += newPlanId
                    "?"
                }
                spec.tableName == "planned_activities" && column == "status" ->
                    "CASE WHEN $quotedColumn = 'IN_PROGRESS' THEN 'PLANNED' ELSE $quotedColumn END"
                spec.tableName == "planned_activities" && column == "completedAtEpochMillis" ->
                    "CASE WHEN `status` = 'IN_PROGRESS' THEN NULL ELSE $quotedColumn END"
                column in spec.remappedIdentifierColumns -> {
                    arguments += newPlanId
                    "CASE WHEN $quotedColumn IS NULL THEN NULL ELSE ? || ':' || $quotedColumn END"
                }
                else -> quotedColumn
            }
        }
        arguments += sourcePlanId

        database.execSQL(
            """
            INSERT INTO ${quoteIdentifier(spec.tableName)} (${spec.columns.joinToString { quoteIdentifier(it) }})
            SELECT ${selections.joinToString()}
            FROM ${quoteIdentifier(spec.tableName)}
            WHERE ${quoteIdentifier("studyPlanId")} = ?${
                if (spec.tableName == "study_sessions") " AND `status` NOT IN ('RUNNING', 'PAUSED')" else ""
            }
            """.trimIndent(),
            arguments.toTypedArray(),
        )
    }

    private fun quoteIdentifier(identifier: String): String = "`$identifier`"

    data class TableSpec(
        val tableName: String,
        val columns: List<String>,
        val remappedIdentifierColumns: Set<String>,
    )

    companion object {
        val TableSpecs = listOf(
            table(
                "study_targets",
                "id", "studyPlanId", "organization", "role", "examBoard", "noticeUrl",
                "confirmedExamDateIso", "expectedExamDateIso", "openings", "estimatedCutoffPercent",
                "createdAtEpochMillis", "updatedAtEpochMillis",
                remap = setOf("id"),
            ),
            table(
                "exam_stages",
                "id", "studyPlanId", "studyTargetId", "name", "type", "scheduledDateIso",
                "weight", "minimumScorePercent", "position",
                remap = setOf("id", "studyTargetId"),
            ),
            table(
                "exam_notice_revisions",
                "id", "studyPlanId", "studyTargetId", "label", "sourceReference",
                "importedAtEpochMillis", "normalizedContentSha256", "parserVersion",
                "publicationDateIso", "addedCount", "removedCount", "changedCount", "unchangedCount",
                remap = setOf("id", "studyTargetId"),
            ),
            table(
                "subjects",
                "id", "studyPlanId", "name", "description", "colorArgb", "priority", "examWeight",
                "estimatedMinutes", "progressPercent", "accuracyPercent", "status", "position", "archived",
                "examStageId",
                remap = setOf("id", "examStageId"),
            ),
            table(
                "topics",
                "id", "studyPlanId", "subjectId", "parentTopicId", "name", "description", "priority",
                "difficulty", "examIncidencePercent", "estimatedMinutes", "progressPercent", "masteryPercent",
                "status", "position", "archived", "examStageId",
                remap = setOf("id", "subjectId", "parentTopicId", "examStageId"),
            ),
            table(
                "legislation_documents",
                "id", "studyPlanId", "title", "sourceReference", "createdAtEpochMillis", "updatedAtEpochMillis",
                remap = setOf("id"),
            ),
            table(
                "legislation_provisions",
                "id", "legislationDocumentId", "studyPlanId", "reference", "parentReference", "kind", "text",
                "position", "read", "important", "createdAtEpochMillis", "updatedAtEpochMillis",
                remap = setOf("id", "legislationDocumentId"),
            ),
            table(
                "study_cycles",
                "id", "studyPlanId", "name", "enabled", "currentItemId",
                "createdAtEpochMillis", "updatedAtEpochMillis",
                remap = setOf("id", "currentItemId"),
            ),
            table(
                "study_cycle_items",
                "id", "studyCycleId", "studyPlanId", "subjectId", "topicId", "position",
                "targetDurationMinutes", "enabled", "activityType", "completionRule",
                remap = setOf("id", "studyCycleId", "subjectId", "topicId"),
            ),
            table(
                "study_strategies",
                "id", "studyPlanId", "type", "minimumSessionMinutes", "maximumSessionMinutes",
                "theoryRatioPercent", "questionRatioPercent", "reviewRatioPercent",
                "adaptivePlanningEnabled", "configurationJson",
                remap = setOf("id"),
            ),
            table(
                "schedule_rules",
                "id", "studyPlanId", "subjectId", "type", "dayOfWeek", "startMinuteOfDay",
                "durationMinutes", "recurrenceRule", "locked", "enabled", "position",
                remap = setOf("id", "subjectId"),
            ),
            table(
                "study_materials",
                "id", "studyPlanId", "subjectId", "topicId", "type", "title", "description", "sourceUri",
                "author", "totalUnits", "completedUnits", "primary", "favorite", "archived", "tagsJson",
                "createdAtEpochMillis", "updatedAtEpochMillis",
                remap = setOf("id", "subjectId", "topicId"),
            ),
            table(
                "video_resources",
                "id", "studyPlanId", "subjectId", "topicId", "title", "sourceType", "sourceUri",
                "canonicalSourceId", "thumbnailUri", "durationMillis", "playbackPositionMillis", "completed",
                "createdAtEpochMillis", "updatedAtEpochMillis",
                remap = setOf("id", "subjectId", "topicId"),
            ),
            table(
                "planned_activities",
                "id", "studyPlanId", "subjectId", "topicId", "materialId", "videoResourceId", "type", "title",
                "scheduledStartEpochMillis", "plannedDurationMinutes", "status", "locked", "completedAtEpochMillis",
                remap = setOf("id", "subjectId", "topicId", "materialId", "videoResourceId"),
            ),
            table(
                "study_sessions",
                "id", "studyPlanId", "plannedActivityId", "subjectId", "topicId", "materialId", "videoResourceId",
                "type", "status", "startedAtEpochMillis", "endedAtEpochMillis", "grossDurationSeconds",
                "pauseDurationSeconds", "pagesCompleted", "questionsAnswered", "correctAnswers",
                "concentrationRating", "difficultyRating", "notes", "targetDurationMinutes",
                remap = setOf("id", "plannedActivityId", "subjectId", "topicId", "materialId", "videoResourceId"),
            ),
            table(
                "study_goals",
                "id", "studyPlanId", "subjectId", "topicId", "type", "period", "title", "targetValue",
                "currentValue", "startsOnIso", "endsOnIso", "completedAtEpochMillis", "archived",
                remap = setOf("id", "subjectId", "topicId"),
            ),
            table(
                "questions",
                "id", "studyPlanId", "subjectId", "topicId", "type", "statement", "optionsJson", "correctAnswer",
                "explanation", "examBoard", "source", "year", "difficulty", "favorite", "disputed", "tagsJson",
                "createdAtEpochMillis", "updatedAtEpochMillis", "organization", "role", "examStageId", "authorship",
                "examNoticeRevisionId", "externalReference", "points",
                remap = setOf("id", "subjectId", "topicId", "examStageId", "examNoticeRevisionId"),
            ),
            table(
                "study_tests",
                "id", "studyPlanId", "title", "mode", "timeLimitSeconds", "questionCount", "answeredCount",
                "correctCount", "startedAtEpochMillis", "submittedAtEpochMillis", "configurationJson",
                remap = setOf("id"),
            ),
            table(
                "mock_exams",
                "id", "studyPlanId", "title", "mode", "timeLimitSeconds", "questionCount", "answeredCount",
                "correctCount", "scorePercent", "negativeMarking", "minimumScorePercent", "startedAtEpochMillis",
                "submittedAtEpochMillis", "configurationJson",
                remap = setOf("id"),
            ),
            table(
                "flashcards",
                "id", "studyPlanId", "subjectId", "topicId", "type", "front", "back", "clozeText", "tagsJson",
                "suspended", "createdAtEpochMillis", "updatedAtEpochMillis",
                remap = setOf("id", "subjectId", "topicId"),
            ),
            table(
                "error_entries",
                "id", "studyPlanId", "questionId", "subjectId", "topicId", "category", "chosenAnswer",
                "correctAnswer", "explanation", "recurrenceCount", "createdAtEpochMillis", "updatedAtEpochMillis",
                remap = setOf("id", "questionId", "subjectId", "topicId"),
            ),
            table(
                "review_items",
                "id", "studyPlanId", "subjectId", "topicId", "questionId", "flashcardId", "errorEntryId", "method",
                "status", "dueAtEpochMillis", "intervalDays", "repetitionCount", "easeFactor", "completedAtEpochMillis",
                remap = setOf("id", "subjectId", "topicId", "questionId", "flashcardId", "errorEntryId"),
            ),
            table(
                "legislation_evidence_links",
                "id", "studyPlanId", "provisionId", "questionId", "errorEntryId", "reviewItemId",
                remap = setOf("id", "provisionId", "questionId", "errorEntryId", "reviewItemId"),
            ),
            table(
                "study_notes",
                "id", "studyPlanId", "subjectId", "topicId", "materialId", "type", "title", "content", "pinned",
                "tagsJson", "createdAtEpochMillis", "updatedAtEpochMillis",
                remap = setOf("id", "subjectId", "topicId", "materialId"),
            ),
            table(
                "study_plan_settings",
                "studyPlanId", "adaptivePlanningEnabled", "reviewNotificationsEnabled", "strictExamModeEnabled",
                "preferredSessionMinutes", "quietHoursStartMinute", "quietHoursEndMinute", "configurationJson",
                remap = emptySet(),
            ),
            table(
                "programming_course_profiles",
                "id", "studyPlanId", "primaryLanguage", "level", "repositoryUri", "workspaceRelativePath",
                "buildSystem", "packageManager", "tagsJson", "createdAtEpochMillis", "updatedAtEpochMillis",
                remap = setOf("id"),
            ),
            table(
                "code_snippets",
                "id", "studyPlanId", "subjectId", "topicId", "title", "language", "code", "description",
                "sourceUri", "tagsJson", "createdAtEpochMillis", "updatedAtEpochMillis",
                remap = setOf("id", "subjectId", "topicId"),
            ),
            table(
                "coding_exercises",
                "id", "studyPlanId", "subjectId", "topicId", "snippetId", "type", "title", "prompt",
                "starterCode", "expectedOutput", "hintsJson", "solutionNotes", "language", "difficulty",
                "status", "runCommandTemplate", "testCommandTemplate", "filesJson", "tagsJson",
                "lastResultJson", "retryAtEpochMillis", "createdAtEpochMillis", "updatedAtEpochMillis",
                remap = setOf("id", "subjectId", "topicId", "snippetId"),
            ),
            table(
                "mini_projects",
                "id", "studyPlanId", "subjectId", "topicId", "title", "description", "status", "language",
                "repositoryUri", "workspaceRelativePath", "checkpointsJson", "deliverablesJson",
                "runCommandTemplate", "testCommandTemplate", "createdAtEpochMillis", "updatedAtEpochMillis",
                remap = setOf("id", "subjectId", "topicId"),
            ),
            table(
                "termux_runtime_preferences",
                "studyPlanId", "enabled", "workspaceRoot", "preferredLanguage", "defaultRunCommand",
                "defaultTestCommand", "allowedCommandTemplatesJson", "captureStdout", "captureStderr",
                "updatedAtEpochMillis",
                remap = emptySet(),
            ),
        )

        /**
         * File-backed rows are deliberately excluded from [TableSpecs] so ordinary plan duplication can
         * prepare physically independent bytes before its Room transaction. Portability still needs annex
         * metadata, so its table inventory opts in explicitly through [PortabilityTableSpecs].
         */
        val AnnexSpec = table(
            "annexes",
            "id", "studyPlanId", "subjectId", "topicId", "materialId", "displayName", "mimeType", "byteSize",
            "checksumSha256", "storageMode", "storageReference", "persistedPermission", "createdAtEpochMillis",
            "updatedAtEpochMillis",
            remap = setOf("id", "subjectId", "topicId", "materialId"),
        )

        val PortabilityTableSpecs: List<TableSpec> = TableSpecs + AnnexSpec

        private fun table(
            tableName: String,
            vararg columns: String,
            remap: Set<String>,
        ) = TableSpec(tableName, columns.toList(), remap)
    }
}
