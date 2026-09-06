package com.mikeyphw.studyflow.reminders

internal object DeliveredReminderHistory {
    private const val FormatPrefix = "v1;"

    fun append(
        orderedOccurrenceIds: Collection<String>,
        occurrenceId: String,
        maxSize: Int,
    ): List<String> {
        require(maxSize > 0) { "Delivered reminder history limit must be positive." }
        val ordered = LinkedHashSet<String>(orderedOccurrenceIds.size + 1)
        orderedOccurrenceIds.forEach(ordered::add)
        ordered.remove(occurrenceId)
        ordered.add(occurrenceId)
        while (ordered.size > maxSize) {
            ordered.remove(ordered.first())
        }
        return ordered.toList()
    }

    fun remove(
        orderedOccurrenceIds: Collection<String>,
        occurrenceId: String,
    ): List<String> = orderedOccurrenceIds.filterNot { it == occurrenceId }

    fun migrateLegacy(
        legacyOccurrenceIds: Set<String>,
        maxSize: Int,
    ): List<String> {
        require(maxSize > 0) { "Delivered reminder history limit must be positive." }
        // SharedPreferences StringSet never guaranteed insertion order, so legacy chronology cannot be
        // reconstructed. Preserve every legacy membership entry (up to the historical cap) and treat
        // the entire migrated block as older than deliveries recorded in the ordered format.
        return legacyOccurrenceIds.take(maxSize)
    }

    fun encode(orderedOccurrenceIds: Collection<String>): String = buildString {
        append(FormatPrefix)
        orderedOccurrenceIds.forEach { occurrenceId ->
            append(occurrenceId.length)
            append(':')
            append(occurrenceId)
        }
    }

    fun decode(encoded: String): List<String>? {
        if (!encoded.startsWith(FormatPrefix)) return null
        var cursor = FormatPrefix.length
        val ordered = LinkedHashSet<String>()
        while (cursor < encoded.length) {
            val separator = encoded.indexOf(':', startIndex = cursor)
            if (separator < 0) return null
            val length = encoded.substring(cursor, separator).toIntOrNull() ?: return null
            if (length < 0) return null
            val valueStart = separator + 1
            if (length > encoded.length - valueStart) return null
            val valueEnd = valueStart + length
            ordered.add(encoded.substring(valueStart, valueEnd))
            cursor = valueEnd
        }
        return ordered.toList()
    }
}
