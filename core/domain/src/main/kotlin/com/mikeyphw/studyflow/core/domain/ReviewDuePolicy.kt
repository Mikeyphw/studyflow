package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.ReviewItem
import com.mikeyphw.studyflow.core.model.ReviewStatus
import java.time.Instant
import java.time.ZoneId
import java.time.LocalDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

object ReviewDuePolicy {
    fun isActive(item: ReviewItem): Boolean = item.status == ReviewStatus.SCHEDULED || item.status == ReviewStatus.DUE

    fun isDue(item: ReviewItem, nowEpochMillis: Long): Boolean =
        isActive(item) && item.dueAtEpochMillis <= nowEpochMillis

    fun localDate(epochMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): LocalDate =
        Instant.ofEpochMilli(epochMillis).atZone(zoneId).toLocalDate()

    fun completedOnLocalDay(
        item: ReviewItem,
        day: LocalDate,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Boolean = item.status == ReviewStatus.COMPLETED && item.completedAtEpochMillis?.let {
        localDate(it, zoneId) == day
    } == true
}

fun tickingClockFlow(
    clock: () -> Long = System::currentTimeMillis,
    intervalMillis: Long = 60_000L,
): Flow<Long> = flow {
    require(intervalMillis > 0L)
    while (true) {
        emit(clock())
        delay(intervalMillis)
    }
}
