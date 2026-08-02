package com.mikeyphw.studyflow.core.diagnostics

import java.security.MessageDigest
import java.util.Locale

/** Privacy boundary shared by timelines, runtime reports, and support bundles. */
object DiagnosticRedactor {
    private const val MaxTextLength = 512
    private const val MaxDetailCount = 32

    private val sensitiveTokens = setOf(
        "authorization",
        "auth",
        "cookie",
        "password",
        "passphrase",
        "secret",
        "session",
        "signature",
        "token",
    )
    private val sensitiveCompounds = setOf(
        "access_token",
        "api_key",
        "auth_token",
        "private_key",
        "proxy_authorization",
        "refresh_token",
        "set_cookie",
    )
    private val privateContentTokens = setOf(
        "answer",
        "body",
        "content",
        "filename",
        "label",
        "message",
        "name",
        "note",
        "question",
        "text",
        "title",
    )
    private val identifierTokens = setOf(
        "annex_id",
        "attempt_id",
        "exam_id",
        "plan_id",
        "reminder_id",
        "review_id",
        "session_id",
        "subject_id",
        "topic_id",
    )
    private val standardDetailKeys = setOf(
        "api_level",
        "available",
        "build_type",
        "channel",
        "count",
        "duration",
        "enabled",
        "importance",
        "locale",
        "operation",
        "permission",
        "phase",
        "policy",
        "reason",
        "result",
        "sdk",
        "source",
        "status",
        "target",
        "task",
        "timezone",
        "variant",
        "version",
        "window",
    )
    private val booleanDetailKeys = setOf("available", "enabled")
    private val numericDetailKeys = setOf(
        "api_level",
        "count",
        "duration",
        "importance",
        "sdk",
        "window",
    )
    private val buildTypeDetailKeys = setOf("build_type", "variant")
    private val allowedBuildTypes = setOf(
        "benchmark",
        "debug",
        "internal",
        "profile",
        "qa",
        "release",
        "staging",
    )
    private val safeExceptionTypes = setOf(
        "ArithmeticException",
        "CancellationException",
        "ConcurrentModificationException",
        "Error",
        "Exception",
        "IllegalArgumentException",
        "IllegalStateException",
        "IndexOutOfBoundsException",
        "IOException",
        "NoSuchElementException",
        "NullPointerException",
        "OutOfMemoryError",
        "RuntimeException",
        "SecurityException",
        "Throwable",
        "TimeoutException",
        "UnsupportedOperationException",
    )

    private val stableActionPattern = Regex("[a-z][a-z0-9]*(?:-[a-z0-9]+)*")
    private val integerPattern = Regex("-?[0-9]{1,20}")
    private val versionPattern = Regex("[0-9]+(?:\\.[0-9A-Za-z]+)*(?:[-+][0-9A-Za-z.-]+)?")
    private val localePattern = Regex("[A-Za-z]{2,3}(?:[-_][A-Za-z0-9]{2,8})*")
    private val timezonePattern = Regex(
        "(?:[A-Za-z][A-Za-z0-9_+-]*/[A-Za-z0-9_+.-]+(?:/[A-Za-z0-9_+.-]+)*)"
            + "|(?:UTC(?:[+-][0-9]{1,2}(?::[0-9]{2})?)?)",
    )
    private val safeFingerprintPattern = Regex("[0-9a-f]{16}")
    private val wrappedFingerprintPattern = Regex("<fingerprint:[0-9a-f]{16}>")
    private val credentialPattern = Regex(
        pattern = "(?i)\\b(bearer|basic)\\s+[A-Za-z0-9._~+/=-]{8,}",
    )
    private val querySecretPattern = Regex(
        pattern = "(?i)([?&][^=&#]*(?:token|secret|password|session|cookie|signature|key|auth)"
            + "[^=&#]*=)[^&#\\s]+",
    )
    private val emailPattern = Regex(
        pattern = "(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b",
    )
    private val uriPattern = Regex(
        pattern = "(?i)\\b(?:https?|content|file)://[^\\s,;]+",
    )
    private val absolutePathPattern = Regex(
        pattern = "(?<![A-Za-z0-9])/(?:data|storage|sdcard|home|mnt|system|vendor|apex|product)/[^\\s,;]+",
    )
    private val jwtPattern = Regex(
        pattern = "\\b[A-Za-z0-9_-]{12,}\\.[A-Za-z0-9_-]{12,}\\.[A-Za-z0-9_-]{12,}\\b",
    )
    private val uuidPattern = Regex(
        pattern = "(?i)\\b[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\\b",
    )

    fun redactDetails(details: Map<String, String>): Map<String, String> = details
        .entries
        .asSequence()
        .sortedBy { (key, _) -> key.lowercase(Locale.US) }
        .take(MaxDetailCount)
        .associateTo(linkedMapOf()) { (key, value) ->
            safeKey(key) to redactValueForKey(key, value)
        }

    fun redactValueForKey(key: String, value: String?): String {
        val normalized = normalizeKey(key)
        if (normalized.isBlank()) return ""
        return when {
            isSensitiveKey(normalized) -> "<redacted>"
            isPrivateContentKey(normalized) -> "<redacted-content>"
            isIdentifierKey(normalized) -> fingerprintValue(value)
            isPathKey(normalized) -> "<redacted-path>"
            isUriKey(normalized) -> redactUri(value)
            normalized in booleanDetailKeys -> safeBoolean(value)
            normalized in numericDetailKeys -> safeInteger(value)
            normalized in buildTypeDetailKeys -> safeBuildType(value)
            normalized == "version" -> safeVersion(value)
            normalized == "locale" -> safeLocale(value)
            normalized == "timezone" -> safeTimezone(value)
            else -> fingerprintValue(value)
        }
    }

    fun safeAction(value: String): String {
        val action = value.trim()
        return if (action.length <= 96 && stableActionPattern.matches(action)) {
            action
        } else {
            "action-${fingerprint(action)}"
        }
    }

    fun redactText(value: String?): String {
        val text = value?.trim()?.takeIf(String::isNotBlank) ?: return ""
        return text
            .replace(credentialPattern) { match ->
                "${match.groupValues[1].lowercase(Locale.US)} <redacted>"
            }
            .replace(querySecretPattern) { match -> "${match.groupValues[1]}<redacted>" }
            .replace(emailPattern, "<redacted-email>")
            .replace(uriPattern) { match -> redactUri(match.value) }
            .replace(absolutePathPattern, "<redacted-path>")
            .replace(jwtPattern, "<redacted-token>")
            .replace(uuidPattern) { match -> "id:${fingerprint(match.value)}" }
            .take(MaxTextLength)
    }

    fun redactUri(value: String?): String {
        val raw = value?.trim()?.takeIf(String::isNotBlank) ?: return ""
        val scheme = raw.substringBefore("://", missingDelimiterValue = "uri")
            .lowercase(Locale.US)
            .filter(Char::isLetterOrDigit)
            .take(16)
            .ifBlank { "uri" }
        return "<redacted-$scheme-uri>"
    }

    fun summarizeThrowable(throwable: Throwable): DiagnosticExceptionSummary {
        val rawType = throwable::class.qualifiedName
            ?: throwable::class.simpleName
            ?: "Throwable"
        val topFrame = throwable.stackTrace.firstOrNull()?.let { frame ->
            "${frame.className}.${frame.methodName}:${frame.lineNumber}"
        }.orEmpty()
        val fingerprintInput = listOf(
            rawType,
            throwable.message.orEmpty(),
            topFrame,
        ).joinToString("|")
        return DiagnosticExceptionSummary(
            type = safeExceptionType(rawType),
            fingerprint = fingerprint(fingerprintInput),
        )
    }

    fun sanitizeExceptionSummary(summary: DiagnosticExceptionSummary): DiagnosticExceptionSummary {
        val safeType = safeExceptionType(summary.type)
        val safeFingerprint = summary.fingerprint
            .lowercase(Locale.US)
            .takeIf(safeFingerprintPattern::matches)
            ?: fingerprint("${summary.type}|${summary.fingerprint}")
        return DiagnosticExceptionSummary(
            type = safeType,
            fingerprint = safeFingerprint,
        )
    }

    fun safeSessionId(value: String): String = "session-${fingerprint(value)}"

    fun fingerprint(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(byte) }
        .take(16)

    fun jsonEscape(value: String): String = buildString(value.length) {
        value.forEach { character ->
            when (character) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> {
                    if (character.code < 0x20) {
                        append("\\u%04x".format(character.code))
                    } else {
                        append(character)
                    }
                }
            }
        }
    }

    private fun safeBoolean(value: String?): String {
        val normalized = value?.trim()?.lowercase(Locale.US).orEmpty()
        return if (normalized in setOf("false", "true", "unknown")) normalized else fingerprintValue(value)
    }

    private fun safeInteger(value: String?): String {
        val normalized = value?.trim().orEmpty()
        return if (integerPattern.matches(normalized)) normalized else fingerprintValue(value)
    }

    private fun safeBuildType(value: String?): String {
        val normalized = value?.trim()?.lowercase(Locale.US).orEmpty()
        return if (normalized in allowedBuildTypes) normalized else fingerprintValue(value)
    }

    private fun safeVersion(value: String?): String {
        val normalized = value?.trim().orEmpty()
        return if (normalized.length <= 64 && versionPattern.matches(normalized)) {
            normalized
        } else {
            fingerprintValue(value)
        }
    }

    private fun safeLocale(value: String?): String {
        val normalized = value?.trim().orEmpty()
        return if (normalized.length <= 40 && localePattern.matches(normalized)) {
            normalized
        } else {
            fingerprintValue(value)
        }
    }

    private fun safeTimezone(value: String?): String {
        val normalized = value?.trim().orEmpty()
        return if (normalized.length <= 80 && timezonePattern.matches(normalized)) {
            normalized
        } else {
            fingerprintValue(value)
        }
    }

    private fun fingerprintValue(value: String?): String {
        val normalized = value?.trim()?.takeIf(String::isNotBlank) ?: return ""
        if (wrappedFingerprintPattern.matches(normalized)) return normalized
        return "<fingerprint:${fingerprint(normalized)}>"
    }

    private fun safeExceptionType(value: String): String {
        val simpleName = value.trim().substringAfterLast('.').take(160)
        return when {
            simpleName in safeExceptionTypes -> simpleName
            simpleName.endsWith("Exception") -> "Exception-${fingerprint(value)}"
            simpleName.endsWith("Error") -> "Error-${fingerprint(value)}"
            else -> "Throwable-${fingerprint(value)}"
        }
    }

    private fun normalizeKey(key: String): String = key
        .replace(Regex("([a-z0-9])([A-Z])"), "$1_$2")
        .lowercase(Locale.US)
        .replace(Regex("[^a-z0-9]+"), "_")
        .trim('_')

    private fun isSensitiveKey(normalized: String): Boolean {
        if (normalized in sensitiveCompounds) return true
        val tokens = normalized.split('_').filter(String::isNotBlank)
        return tokens.any(sensitiveTokens::contains)
    }

    private fun isPrivateContentKey(normalized: String): Boolean {
        val tokens = normalized.split('_').filter(String::isNotBlank)
        return tokens.any(privateContentTokens::contains)
    }

    private fun isIdentifierKey(normalized: String): Boolean =
        normalized in identifierTokens || normalized.endsWith("_id") || normalized == "id"

    private fun isPathKey(normalized: String): Boolean {
        val tokens = normalized.split('_').filter(String::isNotBlank)
        return tokens.any { token -> token in setOf("directory", "file", "location", "path") }
    }

    private fun isUriKey(normalized: String): Boolean {
        val tokens = normalized.split('_').filter(String::isNotBlank)
        return tokens.any { token -> token in setOf("uri", "url") }
    }

    private fun safeKey(key: String): String {
        val normalized = normalizeKey(key)
        if (normalized.isBlank()) return "detail"
        val isKnownKey = isSensitiveKey(normalized) ||
            isPrivateContentKey(normalized) ||
            isIdentifierKey(normalized) ||
            isPathKey(normalized) ||
            isUriKey(normalized) ||
            normalized in standardDetailKeys
        return if (isKnownKey) normalized.take(80) else "detail_${fingerprint(normalized)}"
    }
}
