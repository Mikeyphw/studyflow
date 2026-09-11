package com.mikeyphw.studyflow.core.portability

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** Canonical language-neutral JSON used by WEB20 and later PARITY04 semantic vectors. */
object CanonicalPortableJson {
    private val json = Json

    fun canonicalize(rawJson: String): String = canonicalize(json.parseToJsonElement(rawJson)).toString()

    private fun canonicalize(value: JsonElement): JsonElement = when (value) {
        is JsonObject -> JsonObject(value.entries.sortedBy { it.key }.associate { (key, child) ->
            key to canonicalize(child)
        })
        is JsonArray -> JsonArray(value.map(::canonicalize))
        else -> value
    }
}
