package com.mikeyphw.studyflow.core.link

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.max

private const val DefaultCalendarExportLookaheadDays = 90
private const val DefaultCalendarExportHistoryDays = 14
private const val MaxCalendarEvents = 1_024
private val calendarTokenPattern = Regex("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")

@Serializable
@JvmInline
value class StudyFlowCalendarExportId(val value: String) {
    init {
        require(calendarTokenPattern.matches(value)) {
            "Calendar export ID must be a bounded wire token"
        }
    }

    companion object {
        fun deterministic(vararg parts: String): StudyFlowCalendarExportId =
            StudyFlowCalendarExportId(
                StudyFlowAttentionOccurrenceId.deterministic("calendar", *parts).value,
            )
    }
}

@Serializable
enum class StudyFlowCalendarExportProvider {
    @SerialName("ics_file") ICS_FILE,
    @SerialName("webcal_feed") WEBCAL_FEED,
    @SerialName("google_calendar") GOOGLE_CALENDAR,
    @SerialName("outlook_calendar") OUTLOOK_CALENDAR,
    @SerialName("apple_calendar") APPLE_CALENDAR,
}

@Serializable
enum class StudyFlowCalendarPrivacyMode {
    @SerialName("full_details") FULL_DETAILS,
    @SerialName("private_title") PRIVATE_TITLE,
    @SerialName("busy_only") BUSY_ONLY,
}

@Serializable
enum class StudyFlowCalendarEventKind {
    @SerialName("study_block") STUDY_BLOCK,
    @SerialName("review") REVIEW,
    @SerialName("mock_exam") MOCK_EXAM,
    @SerialName("exam_date") EXAM_DATE,
    @SerialName("daily_summary") DAILY_SUMMARY,
    @SerialName("schedule_change") SCHEDULE_CHANGE,
}

@Serializable
data class StudyFlowCalendarExportPreferences(
    val provider: StudyFlowCalendarExportProvider = StudyFlowCalendarExportProvider.ICS_FILE,
    val privacyMode: StudyFlowCalendarPrivacyMode = StudyFlowCalendarPrivacyMode.FULL_DETAILS,
    val includeCompleted: Boolean = false,
    val includeReviews: Boolean = true,
    val includeExamDates: Boolean = true,
    val includeAttentionLead: Boolean = true,
    val lookaheadDays: Int = DefaultCalendarExportLookaheadDays,
    val historyDays: Int = DefaultCalendarExportHistoryDays,
    val calendarName: String = "StudyFlow schedule",
    val productId: String = "-//StudyFlow//Calendar Attention Export//EN",
) {
    init {
        require(lookaheadDays in 1..366) { "Calendar export lookahead must be 1..366 days" }
        require(historyDays in 0..366) { "Calendar export history must be 0..366 days" }
        require(calendarName.isNotBlank()) { "Calendar name must not be blank" }
        require(productId.isNotBlank()) { "Calendar product ID must not be blank" }
    }
}

@Serializable
data class StudyFlowCalendarScheduleItem(
    val itemId: StudyFlowEntityId,
    val workspaceId: StudyFlowWorkspaceId,
    val title: String,
    val body: String? = null,
    val kind: StudyFlowCalendarEventKind,
    val startEpochMillis: Long,
    val durationMinutes: Int = 30,
    val timezoneId: String? = null,
    val allDay: Boolean = false,
    val completed: Boolean = false,
    val routeHash: String = "#schedule",
    val attentionCategory: StudyFlowAttentionCategory = StudyFlowAttentionCategory.PLANNED_STUDY,
    val attentionPriority: StudyFlowAttentionPriority = StudyFlowAttentionPriority.NORMAL,
    val reminderLeadMinutes: Int = 10,
) {
    init {
        require(title.isNotBlank()) { "Calendar item title must not be blank" }
        require(startEpochMillis >= 0L) { "Calendar item start time must not be negative" }
        require(durationMinutes in 0..1_440) { "Calendar item duration must be 0..1440 minutes" }
        require(timezoneId == null || timezoneId.isNotBlank()) { "Calendar item timezone must not be blank" }
        require(routeHash.isNotBlank()) { "Calendar item route hash must not be blank" }
        require(reminderLeadMinutes in 0..1_440) { "Calendar reminder lead must be 0..1440 minutes" }
    }
}

@Serializable
data class StudyFlowCalendarAttentionEvent(
    val exportId: StudyFlowCalendarExportId,
    val item: StudyFlowCalendarScheduleItem,
    val summary: String,
    val description: String,
    val location: String? = null,
    val startsAtEpochMillis: Long,
    val endsAtEpochMillis: Long,
    val reminderLeadMinutes: Int,
    val attentionOccurrence: StudyFlowAttentionOccurrence? = null,
) {
    init {
        require(summary.isNotBlank()) { "Calendar event summary must not be blank" }
        require(description.isNotBlank()) { "Calendar event description must not be blank" }
        require(endsAtEpochMillis >= startsAtEpochMillis) { "Calendar event end must not precede start" }
    }
}

@Serializable
data class StudyFlowCalendarExportDiagnostics(
    val inputItemCount: Int,
    val exportedEventCount: Int,
    val completedSuppressedCount: Int,
    val outsideWindowSuppressedCount: Int,
    val reviewSuppressedCount: Int,
    val attentionOccurrenceCount: Int,
    val privacyMode: StudyFlowCalendarPrivacyMode,
    val explanation: String,
) {
    init {
        require(inputItemCount >= 0) { "Calendar input count must not be negative" }
        require(exportedEventCount >= 0) { "Calendar exported count must not be negative" }
        require(completedSuppressedCount >= 0) { "Calendar completed suppression count must not be negative" }
        require(outsideWindowSuppressedCount >= 0) { "Calendar outside-window count must not be negative" }
        require(reviewSuppressedCount >= 0) { "Calendar review suppression count must not be negative" }
        require(attentionOccurrenceCount >= 0) { "Calendar attention count must not be negative" }
        require(explanation.isNotBlank()) { "Calendar diagnostics explanation must not be blank" }
    }
}

@Serializable
data class StudyFlowCalendarExportFeed(
    val preferences: StudyFlowCalendarExportPreferences,
    val generatedAtEpochMillis: Long,
    val events: List<StudyFlowCalendarAttentionEvent>,
    val diagnostics: StudyFlowCalendarExportDiagnostics,
    val icsText: String,
) {
    init {
        require(generatedAtEpochMillis >= 0L) { "Calendar export generation time must not be negative" }
        require(events.size <= MaxCalendarEvents) { "Calendar export cannot exceed $MaxCalendarEvents events" }
        require(icsText.contains("BEGIN:VCALENDAR")) { "Calendar export must contain an ICS calendar" }
    }
}

class StudyFlowCalendarAttentionExporter {
    fun export(
        items: List<StudyFlowCalendarScheduleItem>,
        nowEpochMillis: Long,
        preferences: StudyFlowCalendarExportPreferences = StudyFlowCalendarExportPreferences(),
    ): StudyFlowCalendarExportFeed {
        require(items.distinctBy(StudyFlowCalendarScheduleItem::itemId).size == items.size) {
            "Calendar schedule items must be unique"
        }
        require(nowEpochMillis >= 0L) { "Calendar export time must not be negative" }
        val windowStart = nowEpochMillis - preferences.historyDays.daysToMillis()
        val windowEnd = nowEpochMillis + preferences.lookaheadDays.daysToMillis()
        var completedSuppressed = 0
        var outsideSuppressed = 0
        var reviewSuppressed = 0
        val exported = items.asSequence()
            .onEach {
                if (it.completed && !preferences.includeCompleted) completedSuppressed += 1
                else if (
                    it.kind == StudyFlowCalendarEventKind.REVIEW &&
                    !preferences.includeReviews
                ) {
                    reviewSuppressed += 1
                }
                else if (it.startEpochMillis !in windowStart..windowEnd) outsideSuppressed += 1
            }
            .filterNot { it.completed && !preferences.includeCompleted }
            .filterNot { it.kind == StudyFlowCalendarEventKind.REVIEW && !preferences.includeReviews }
            .filter { it.startEpochMillis in windowStart..windowEnd }
            .sortedWith(compareBy<StudyFlowCalendarScheduleItem> { it.startEpochMillis }.thenBy { it.itemId.value })
            .take(MaxCalendarEvents)
            .map { it.toCalendarEvent(preferences) }
            .toList()
        val ics = renderIcs(exported, preferences, nowEpochMillis)
        return StudyFlowCalendarExportFeed(
            preferences = preferences,
            generatedAtEpochMillis = nowEpochMillis,
            events = exported,
            diagnostics = StudyFlowCalendarExportDiagnostics(
                inputItemCount = items.size,
                exportedEventCount = exported.size,
                completedSuppressedCount = completedSuppressed,
                outsideWindowSuppressedCount = outsideSuppressed,
                reviewSuppressedCount = reviewSuppressed,
                attentionOccurrenceCount = exported.count { it.attentionOccurrence != null },
                privacyMode = preferences.privacyMode,
                explanation =
                    "ATT09 exports schedule events and matching attention occurrences without " +
                        "becoming a background delivery owner.",
            ),
            icsText = ics,
        )
    }

    fun attentionOccurrences(feed: StudyFlowCalendarExportFeed): List<StudyFlowAttentionOccurrence> =
        feed.events.mapNotNull(StudyFlowCalendarAttentionEvent::attentionOccurrence)

    private fun StudyFlowCalendarScheduleItem.toCalendarEvent(
        preferences: StudyFlowCalendarExportPreferences,
    ): StudyFlowCalendarAttentionEvent {
        val safeSummary = when (preferences.privacyMode) {
            StudyFlowCalendarPrivacyMode.FULL_DETAILS -> title
            StudyFlowCalendarPrivacyMode.PRIVATE_TITLE -> "StudyFlow block"
            StudyFlowCalendarPrivacyMode.BUSY_ONLY -> "Busy"
        }
        val safeDescription = when (preferences.privacyMode) {
            StudyFlowCalendarPrivacyMode.FULL_DETAILS -> listOfNotNull(
                body,
                "Open in StudyFlow: $routeHash",
                "Source: ATT09 Calendar / Schedule Export Attention Integration.",
            ).joinToString("\n")
            StudyFlowCalendarPrivacyMode.PRIVATE_TITLE ->
                "Study details are hidden by your ATT09 calendar privacy setting. " +
                    "Open StudyFlow for the full plan."
            StudyFlowCalendarPrivacyMode.BUSY_ONLY -> "Busy time exported by StudyFlow. Details hidden."
        }
        val end = if (allDay) startEpochMillis else startEpochMillis + durationMinutes.minutesToMillis()
        val occurrence = if (preferences.includeAttentionLead) {
            toAttentionOccurrence(safeSummary, safeDescription)
        } else {
            null
        }
        return StudyFlowCalendarAttentionEvent(
            exportId = StudyFlowCalendarExportId.deterministic(
                workspaceId.value,
                itemId.value,
                startEpochMillis.toString(),
            ),
            item = this,
            summary = safeSummary,
            description = safeDescription,
            startsAtEpochMillis = startEpochMillis,
            endsAtEpochMillis = end,
            reminderLeadMinutes = reminderLeadMinutes,
            attentionOccurrence = occurrence,
        )
    }

    private fun StudyFlowCalendarScheduleItem.toAttentionOccurrence(
        summary: String,
        description: String,
    ): StudyFlowAttentionOccurrence {
        val dueAt = max(0L, startEpochMillis - reminderLeadMinutes.minutesToMillis())
        return StudyFlowAttentionOccurrence(
            occurrenceId = StudyFlowAttentionOccurrenceId.deterministic(
                "calendar",
                workspaceId.value,
                itemId.value,
                startEpochMillis.toString(),
            ),
            workspaceId = workspaceId,
            scheduleEntityId = itemId,
            category = attentionCategory,
            priority = attentionPriority,
            scheduledAtEpochMillis = dueAt,
            originalScheduledAtEpochMillis = dueAt,
            expiresAtEpochMillis = startEpochMillis + durationMinutes.minutesToMillis(),
            timezoneId = timezoneId,
            deliveryPolicy = StudyFlowAttentionDeliveryPolicy.ACTIVE_THEN_PRIMARY,
            actions = listOf(
                StudyFlowAttentionAction(StudyFlowAttentionActionKind.OPEN),
                StudyFlowAttentionAction(StudyFlowAttentionActionKind.SNOOZE),
                StudyFlowAttentionAction(StudyFlowAttentionActionKind.DISMISS),
            ),
            payload = JsonObject(
                mapOf<String, JsonElement>(
                    "title" to JsonPrimitive(summary),
                    "body" to JsonPrimitive(description),
                    "routeHash" to JsonPrimitive(routeHash),
                    "calendarItemId" to JsonPrimitive(itemId.value),
                    "calendarKind" to JsonPrimitive(kind.name.lowercase()),
                ),
            ),
        )
    }

    private fun renderIcs(
        events: List<StudyFlowCalendarAttentionEvent>,
        preferences: StudyFlowCalendarExportPreferences,
        generatedAtEpochMillis: Long,
    ): String {
        val lines = mutableListOf(
            "BEGIN:VCALENDAR",
            "VERSION:2.0",
            "PRODID:${preferences.productId}",
            "CALSCALE:GREGORIAN",
            "METHOD:PUBLISH",
            "X-WR-CALNAME:${icsEscape(preferences.calendarName)}",
            "X-STUDYFLOW-ATTENTION-INTEGRATION:ATT09",
        )
        for (event in events) {
            lines += "BEGIN:VEVENT"
            lines += "UID:${event.exportId.value}@studyflow.local"
            lines += "DTSTAMP:${formatUtc(generatedAtEpochMillis)}"
            lines += if (event.item.allDay) {
                "DTSTART;VALUE=DATE:${formatDate(event.startsAtEpochMillis, event.item.timezoneId)}"
            } else {
                "DTSTART:${formatUtc(event.startsAtEpochMillis)}"
            }
            lines += if (event.item.allDay) {
                "DTEND;VALUE=DATE:${formatDate(event.endsAtEpochMillis + 86_400_000L, event.item.timezoneId)}"
            } else {
                "DTEND:${formatUtc(event.endsAtEpochMillis)}"
            }
            lines += "SUMMARY:${icsEscape(event.summary)}"
            lines += "DESCRIPTION:${icsEscape(event.description)}"
            event.location?.let { lines += "LOCATION:${icsEscape(it)}" }
            if (event.reminderLeadMinutes > 0) {
                lines += "BEGIN:VALARM"
                lines += "ACTION:DISPLAY"
                lines += "DESCRIPTION:${icsEscape(event.summary)}"
                lines += "TRIGGER:-PT${event.reminderLeadMinutes}M"
                lines += "END:VALARM"
            }
            lines += "END:VEVENT"
        }
        lines += "END:VCALENDAR"
        return lines.joinToString("\r\n", postfix = "\r\n")
    }
}

private fun Int.minutesToMillis(): Long = this.toLong() * 60_000L
private fun Int.daysToMillis(): Long = this.toLong() * 86_400_000L
private fun formatUtc(epochMillis: Long): String = DateTimeFormatter.ISO_INSTANT
    .format(Instant.ofEpochMilli(epochMillis))
    .replace("-", "")
    .replace(":", "")
    .replace(".000", "")

private fun formatDate(epochMillis: Long, timezoneId: String?): String {
    val zone = timezoneId?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.of("UTC")
    val date = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
    return date.format(DateTimeFormatter.BASIC_ISO_DATE)
}

private fun icsEscape(value: String): String = value
    .replace("\\", "\\\\")
    .replace(";", "\\;")
    .replace(",", "\\,")
    .replace("\n", "\\n")
