package com.mikeyphw.studyflow.feature.schedule

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

private val scheduleTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

internal fun parseScheduleDate(value: String): LocalDate? = try {
    LocalDate.parse(value.trim())
} catch (_: DateTimeParseException) {
    null
}

internal fun parseScheduleTime(value: String): LocalTime? = try {
    LocalTime.parse(value.trim(), scheduleTimeFormatter)
} catch (_: DateTimeParseException) {
    null
}

internal fun scheduleStartEpochMillis(date: String, time: String, zoneId: ZoneId): Long? {
    val parsedDate = parseScheduleDate(date) ?: return null
    val parsedTime = parseScheduleTime(time) ?: return null
    return parsedDate.atTime(parsedTime).atZone(zoneId).toInstant().toEpochMilli()
}
