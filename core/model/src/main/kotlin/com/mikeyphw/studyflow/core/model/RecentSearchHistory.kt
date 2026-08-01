package com.mikeyphw.studyflow.core.model

object RecentSearchHistory {
    const val MaximumEntries = 8
    const val Separator = "\u001F"

    fun record(current: List<String>, rawQuery: String): List<String> {
        val query = rawQuery.trim().replace(Separator, " ").replace(Regex("\\s+"), " ")
        if (query.length < 2) return current.take(MaximumEntries)
        return buildList {
            add(query)
            current.filterNot { it.equals(query, ignoreCase = true) }.forEach(::add)
        }.take(MaximumEntries)
    }

    fun encode(searches: List<String>): String = searches
        .map(String::trim)
        .filter(String::isNotBlank)
        .take(MaximumEntries)
        .joinToString(Separator)

    fun decode(encoded: String?): List<String> = encoded
        ?.split(Separator)
        ?.map(String::trim)
        ?.filter(String::isNotBlank)
        ?.take(MaximumEntries)
        .orEmpty()
}
