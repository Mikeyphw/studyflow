package com.mikeyphw.studyflow.core.database.mapper

import android.util.Base64
import java.nio.charset.StandardCharsets

class StudyFlowDatabaseCorruptionException(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)

internal inline fun <reified T : Enum<T>> String.decodeEnum(field: String): T =
    enumValues<T>().firstOrNull { candidate -> candidate.name == this }
        ?: throw StudyFlowDatabaseCorruptionException(
            "Unknown ${T::class.java.simpleName} value '$this' in $field.",
        )


internal inline fun <T> decodeModel(
    recordType: String,
    block: () -> T,
): T = try {
    block()
} catch (error: StudyFlowDatabaseCorruptionException) {
    throw error
} catch (error: IllegalArgumentException) {
    throw StudyFlowDatabaseCorruptionException(
        "Invalid persisted $recordType record.",
        error,
    )
}

private const val StableListPrefix = "b64:"

internal fun List<String>.encodeStringListStable(): String = joinToString(separator = "\n") { value ->
    StableListPrefix + Base64.encodeToString(
        value.toByteArray(StandardCharsets.UTF_8),
        Base64.NO_WRAP,
    )
}

internal fun String.decodeStringListStable(field: String): List<String> {
    if (isEmpty()) return emptyList()
    return lineSequence().mapIndexed { index, raw ->
        val encoded = raw.removePrefix(StableListPrefix)
        try {
            String(Base64.decode(encoded, Base64.NO_WRAP), StandardCharsets.UTF_8)
        } catch (error: IllegalArgumentException) {
            throw StudyFlowDatabaseCorruptionException(
                "Invalid encoded list element $index in $field.",
                error,
            )
        }
    }.toList()
}
