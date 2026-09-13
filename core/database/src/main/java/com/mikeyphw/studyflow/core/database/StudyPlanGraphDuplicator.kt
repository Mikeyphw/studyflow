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
        remapDuplicatedPracticeConfigurations(newPlanId)
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
            WHERE ${spec.sourcePlanPredicate}${
                if (spec.tableName == "study_sessions") " AND `status` NOT IN ('RUNNING', 'PAUSED')" else ""
            }
            """.trimIndent(),
            arguments.toTypedArray(),
        )
    }

    private fun remapDuplicatedPracticeConfigurations(newPlanId: String) {
        listOf("study_tests", "mock_exams").forEach { table ->
            val updates = database.query(
                "SELECT `id`, `configurationJson` FROM ${quoteIdentifier(table)} WHERE `studyPlanId` = ?",
                arrayOf(newPlanId),
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        val id = cursor.getString(0)
                        val configuration = cursor.getString(1)
                        add(id to remapPracticeConfiguration(configuration, newPlanId))
                    }
                }
            }
            updates.forEach { (id, configuration) ->
                database.execSQL(
                    "UPDATE ${quoteIdentifier(table)} SET `configurationJson` = ? WHERE `id` = ? AND `studyPlanId` = ?",
                    arrayOf(configuration, id, newPlanId),
                )
            }
        }
    }

    private fun remapPracticeConfiguration(configuration: String, newPlanId: String): String =
        configuration.lineSequence().joinToString("\n") { rawLine ->
            val line = rawLine.trimEnd()
            when {
                line.startsWith(QuestionIdsPrefix) -> {
                    val ids = line.removePrefix(QuestionIdsPrefix).split(',')
                        .map(String::trim)
                        .filter(String::isNotBlank)
                    QuestionIdsPrefix + ids.joinToString(",") { id -> remappedId(newPlanId, id) }
                }
                line.startsWith(QuestionSnapshotPrefix) ->
                    remapEncodedLeadingId(line, QuestionSnapshotPrefix, newPlanId)
                line.startsWith(AnswerPrefix) ->
                    remapEncodedLeadingId(line, AnswerPrefix, newPlanId)
                else -> line
            }
        }

    private fun remapEncodedLeadingId(line: String, prefix: String, newPlanId: String): String {
        val body = line.removePrefix(prefix)
        val separatorIndex = body.indexOf(FieldSeparator)
        require(separatorIndex > 0) { "Practice attempt contains an invalid encoded identity record." }
        val encodedId = body.substring(0, separatorIndex)
        val decodedId = decodeBase64UrlNoPadding(encodedId)
        return prefix + encodeBase64UrlNoPadding(remappedId(newPlanId, decodedId)) + body.substring(separatorIndex)
    }

    private fun remappedId(newPlanId: String, sourceId: String): String = "$newPlanId:$sourceId"

    private fun encodeBase64UrlNoPadding(value: String): String {
        val bytes = value.encodeToByteArray()
        return buildString((bytes.size * 4 + 2) / 3) {
            var index = 0
            while (index + 2 < bytes.size) {
                val chunk = ((bytes[index].toInt() and 0xFF) shl 16) or
                    ((bytes[index + 1].toInt() and 0xFF) shl 8) or
                    (bytes[index + 2].toInt() and 0xFF)
                append(Base64UrlAlphabet[(chunk ushr 18) and 0x3F])
                append(Base64UrlAlphabet[(chunk ushr 12) and 0x3F])
                append(Base64UrlAlphabet[(chunk ushr 6) and 0x3F])
                append(Base64UrlAlphabet[chunk and 0x3F])
                index += 3
            }
            when (bytes.size - index) {
                1 -> {
                    val chunk = (bytes[index].toInt() and 0xFF) shl 4
                    append(Base64UrlAlphabet[(chunk ushr 6) and 0x3F])
                    append(Base64UrlAlphabet[chunk and 0x3F])
                }
                2 -> {
                    val chunk = ((bytes[index].toInt() and 0xFF) shl 10) or
                        ((bytes[index + 1].toInt() and 0xFF) shl 2)
                    append(Base64UrlAlphabet[(chunk ushr 12) and 0x3F])
                    append(Base64UrlAlphabet[(chunk ushr 6) and 0x3F])
                    append(Base64UrlAlphabet[chunk and 0x3F])
                }
            }
        }
    }

    private fun decodeBase64UrlNoPadding(value: String): String {
        val output = java.io.ByteArrayOutputStream(value.length * 3 / 4)
        var buffer = 0
        var bits = 0
        value.forEach { character ->
            val decoded = Base64UrlAlphabet.indexOf(character)
            require(decoded >= 0) { "Practice attempt contains an invalid Base64Url identity." }
            buffer = (buffer shl 6) or decoded
            bits += 6
            while (bits >= 8) {
                bits -= 8
                output.write((buffer shr bits) and 0xFF)
            }
        }
        require(bits == 0 || (buffer and ((1 shl bits) - 1)) == 0) {
            "Practice attempt contains invalid Base64Url tail bits."
        }
        return output.toByteArray().decodeToString()
    }

    private fun quoteIdentifier(identifier: String): String = "`$identifier`"

    data class TableSpec(
        val tableName: String,
        val columns: List<String>,
        val remappedIdentifierColumns: Set<String>,
        val sourcePlanPredicate: String = "`studyPlanId` = ?",
    )

    companion object {
        private const val QuestionIdsPrefix = "questionIds="
        private const val QuestionSnapshotPrefix = "questionSnapshotV1="
        private const val AnswerPrefix = "answer="
        private const val FieldSeparator = "|"
        private const val Base64UrlAlphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

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
            table(
                "termux_command_requests",
                "id", "studyPlanId", "exerciseId", "workspaceRoot", "courseSlug", "workspaceRelativePath",
                "action", "commandTemplateId", "commandTemplateLabel", "commandTemplateLanguage", "commandLine",
                "workingDirectory", "state", "createdAtEpochMillis",
                remap = setOf("id", "exerciseId"),
            ),
            table(
                "termux_command_results",
                "id", "requestId", "state", "exitCode", "stdoutText", "stderrText", "resultJson",
                "durationMillis", "completedAtEpochMillis",
                remap = setOf("id", "requestId"),
                sourcePlanPredicate =
                    "`requestId` IN (SELECT `id` FROM `termux_command_requests` WHERE `studyPlanId` = ?)",
            ),
            table(
                "language_deck_profiles",
                "id", "studyPlanId", "targetLanguage", "nativeLanguage", "regionFocus", "defaultRegister",
                "reviewDirectionsJson", "tagsJson", "createdAtEpochMillis", "updatedAtEpochMillis",
                remap = setOf("id"),
            ),
            table(
                "language_expressions",
                "id", "studyPlanId", "deckId", "expression", "meaning", "literalTranslation",
                "translationEquivalent", "type", "register", "region", "pronunciationNote", "commonMistake",
                "examplesJson", "tagsJson", "createdAtEpochMillis", "updatedAtEpochMillis",
                remap = setOf("id", "deckId"),
            ),
            table(
                "idiom_cards",
                "id", "expressionId", "naturalExample", "userSentence", "clozePrompt", "acceptedAnswersJson",
                "reviewDirectionsJson", "difficulty",
                remap = setOf("id", "expressionId"),
                sourcePlanPredicate =
                    "`expressionId` IN (SELECT `id` FROM `language_expressions` WHERE `studyPlanId` = ?)",
            ),
            table(
                "vocabulary_cards",
                "id", "expressionId", "partOfSpeech", "synonymsJson", "antonymsJson",
                "reviewDirectionsJson", "difficulty",
                remap = setOf("id", "expressionId"),
                sourcePlanPredicate =
                    "`expressionId` IN (SELECT `id` FROM `language_expressions` WHERE `studyPlanId` = ?)",
            ),
            table(
                "language_review_attempts",
                "id", "studyPlanId", "expressionId", "idiomCardId", "direction", "promptText", "answerText",
                "outcome", "scorePercent", "reviewItemId", "createdAtEpochMillis",
                remap = setOf("id", "expressionId", "idiomCardId", "reviewItemId"),
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

        // OV06: FULL portability covers the complete persisted plan graph, including parent-scoped rows.
        val PortabilityTableSpecs: List<TableSpec> = TableSpecs + AnnexSpec


        private fun table(
            tableName: String,
            vararg columns: String,
            remap: Set<String>,
            sourcePlanPredicate: String = "`studyPlanId` = ?",
        ) = TableSpec(tableName, columns.toList(), remap, sourcePlanPredicate)
    }
}
