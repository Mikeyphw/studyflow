package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.StudyActivityType
import com.mikeyphw.studyflow.core.model.StudySession
import com.mikeyphw.studyflow.core.model.StudySessionStatus
import java.nio.charset.StandardCharsets
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.Base64

/** Five equally weighted rubric dimensions. Each dimension contributes up to 20 points. */
data class WritingRubric(
    val themeAndTask: Int,
    val textType: Int,
    val cohesion: Int,
    val argumentation: Int,
    val standardLanguage: Int,
) {
    init {
        listOf(themeAndTask, textType, cohesion, argumentation, standardLanguage).forEach { score ->
            require(score in 0..20) { "Writing rubric dimensions must be between 0 and 20." }
        }
    }

    val totalScore: Int
        get() = themeAndTask + textType + cohesion + argumentation + standardLanguage
}

data class WritingCompletionChecklist(
    val taskReviewed: Boolean,
    val structureReviewed: Boolean,
    val cohesionReviewed: Boolean,
    val languageReviewed: Boolean,
) {
    val complete: Boolean
        get() = taskReviewed && structureReviewed && cohesionReviewed && languageReviewed
}

data class WritingSessionRecord(
    val prompt: String,
    val draftText: String,
    val rubric: WritingRubric? = null,
    val checklist: WritingCompletionChecklist = WritingCompletionChecklist(
        taskReviewed = false,
        structureReviewed = false,
        cohesionReviewed = false,
        languageReviewed = false,
    ),
) {
    init {
        require(prompt.isNotBlank()) { "Writing prompt cannot be blank." }
    }

    val scorePercent: Int?
        get() = rubric?.totalScore
}

/**
 * Stores writing-specific state inside the already durable StudySession.notes field.
 *
 * This deliberately avoids a second writing database. Existing backup/restore and session-history
 * semantics therefore continue to own the data. Legacy notes remain readable because decode()
 * returns null for anything that is not a versioned writing payload.
 */
object WritingSessionNoteCodec {
    private const val Prefix = "studyflowWritingV1="

    fun encode(record: WritingSessionRecord): String {
        val rubric = record.rubric
        val payload = buildString {
            appendLine("version=1")
            appendLine("prompt=${record.prompt.encodeValue()}")
            appendLine("draft=${record.draftText.encodeValue()}")
            appendLine("theme=${rubric?.themeAndTask ?: -1}")
            appendLine("textType=${rubric?.textType ?: -1}")
            appendLine("cohesion=${rubric?.cohesion ?: -1}")
            appendLine("argumentation=${rubric?.argumentation ?: -1}")
            appendLine("language=${rubric?.standardLanguage ?: -1}")
            appendLine("taskReviewed=${record.checklist.taskReviewed}")
            appendLine("structureReviewed=${record.checklist.structureReviewed}")
            appendLine("cohesionReviewed=${record.checklist.cohesionReviewed}")
            appendLine("languageReviewed=${record.checklist.languageReviewed}")
        }.trimEnd()
        return Prefix + payload.encodeValue()
    }

    fun decode(notes: String): WritingSessionRecord? {
        if (!notes.startsWith(Prefix)) return null
        return runCatching {
            val values = notes.removePrefix(Prefix).decodeValue()
                .lineSequence()
                .map(String::trim)
                .filter(String::isNotBlank)
                .associate { line ->
                    val split = line.indexOf('=')
                    require(split > 0)
                    line.substring(0, split) to line.substring(split + 1)
                }
            require(values.getValue("version") == "1")
            val rubricValues = listOf("theme", "textType", "cohesion", "argumentation", "language")
                .map { key -> values.getValue(key).toInt() }
            val rubric = if (rubricValues.all { it >= 0 }) {
                WritingRubric(
                    themeAndTask = rubricValues[0],
                    textType = rubricValues[1],
                    cohesion = rubricValues[2],
                    argumentation = rubricValues[3],
                    standardLanguage = rubricValues[4],
                )
            } else {
                require(rubricValues.all { it == -1 }) { "Writing rubric payload is incomplete." }
                null
            }
            WritingSessionRecord(
                prompt = values.getValue("prompt").decodeValue(),
                draftText = values.getValue("draft").decodeValue(),
                rubric = rubric,
                checklist = WritingCompletionChecklist(
                    taskReviewed = values.getValue("taskReviewed").toBooleanStrict(),
                    structureReviewed = values.getValue("structureReviewed").toBooleanStrict(),
                    cohesionReviewed = values.getValue("cohesionReviewed").toBooleanStrict(),
                    languageReviewed = values.getValue("languageReviewed").toBooleanStrict(),
                ),
            )
        }.getOrNull()
    }

    private fun String.encodeValue(): String = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(toByteArray(StandardCharsets.UTF_8))

    private fun String.decodeValue(): String = String(
        Base64.getUrlDecoder().decode(this),
        StandardCharsets.UTF_8,
    )
}

data class WritingHistoryEntry(
    val session: StudySession,
    val record: WritingSessionRecord?,
)

data class WritingWeeklyProgress(
    val completed: Int,
    val target: Int,
) {
    init {
        require(completed >= 0)
        require(target > 0)
    }

    val reached: Boolean
        get() = completed >= target
}

object WritingWorkspaceAnalytics {
    fun history(sessions: List<StudySession>): List<WritingHistoryEntry> = sessions
        .asSequence()
        .filter { session ->
            session.type == StudyActivityType.WRITING && session.status == StudySessionStatus.COMPLETED
        }
        .sortedByDescending { session -> session.endedAtEpochMillis ?: Long.MIN_VALUE }
        .map { session -> WritingHistoryEntry(session, WritingSessionNoteCodec.decode(session.notes)) }
        .toList()

    fun weeklyProgress(
        sessions: List<StudySession>,
        target: Int,
        nowEpochMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): WritingWeeklyProgress {
        require(target > 0) { "Writing weekly target must be positive." }
        val today = Instant.ofEpochMilli(nowEpochMillis).atZone(zoneId).toLocalDate()
        val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val nextWeekStart = weekStart.plusWeeks(1)
        val completed = history(sessions).count { entry ->
            val endedAt = entry.session.endedAtEpochMillis ?: return@count false
            val endedDate = Instant.ofEpochMilli(endedAt).atZone(zoneId).toLocalDate()
            !endedDate.isBefore(weekStart) && endedDate.isBefore(nextWeekStart)
        }
        return WritingWeeklyProgress(completed = completed, target = target)
    }
}
