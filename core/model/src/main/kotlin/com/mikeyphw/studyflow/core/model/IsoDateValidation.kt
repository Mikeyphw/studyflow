package com.mikeyphw.studyflow.core.model

private val IsoDatePattern = Regex("^(\\d{4})-(\\d{2})-(\\d{2})$")

internal fun requireValidIsoDate(value: String, field: String) {
    val match = IsoDatePattern.matchEntire(value)
    requireNotNull(match) { "$field must use YYYY-MM-DD." }
    val year = match.groupValues[1].toInt()
    val month = match.groupValues[2].toInt()
    val day = match.groupValues[3].toInt()
    require(year in 1..9999) { "$field year is out of range." }
    require(month in 1..12) { "$field month is out of range." }
    val daysInMonth = when (month) {
        2 -> if (isLeapYear(year)) 29 else 28
        4, 6, 9, 11 -> 30
        else -> 31
    }
    require(day in 1..daysInMonth) { "$field day is out of range." }
}

private fun isLeapYear(year: Int): Boolean =
    year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)
