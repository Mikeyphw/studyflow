package com.mikeyphw.studyflow.core.portability

/**
 * Reads and rewrites the stable question references embedded in StudyTest/MockExam configuration.
 *
 * Practice attempt configuration is line-oriented. Question ids appear both in the leading
 * `questionIds=` record and, after answers are recorded, as the first Base64Url field of every
 * `answer=` record. Portability must treat those references like ordinary foreign keys so plan
 * remapping and metadata-private pseudonymization cannot leave stale/raw ids behind.
 */
object PracticeConfigurationReferences {
    fun referencedQuestionIds(configuration: String): Set<String> = buildSet {
        configuration.lineSequence().map(String::trim).filter(String::isNotBlank).forEach { line ->
            when {
                line.startsWith(QuestionIdsPrefix) -> {
                    line.removePrefix(QuestionIdsPrefix)
                        .split(',')
                        .map(String::trim)
                        .filter(String::isNotBlank)
                        .forEach(::add)
                }
                line.startsWith(AnswerPrefix) -> add(decodeAnswerQuestionId(line))
            }
        }
    }

    fun remapQuestionIds(
        configuration: String,
        transform: (String) -> String,
    ): String = configuration.lineSequence().joinToString("\n") { originalLine ->
        val trimmed = originalLine.trim()
        when {
            trimmed.startsWith(QuestionIdsPrefix) -> {
                val mapped = trimmed.removePrefix(QuestionIdsPrefix)
                    .split(',')
                    .map(String::trim)
                    .filter(String::isNotBlank)
                    .map { id -> requireMappedId(transform(id)) }
                QuestionIdsPrefix + mapped.joinToString(",")
            }
            trimmed.startsWith(AnswerPrefix) -> remapAnswerLine(trimmed, transform)
            else -> originalLine
        }
    }

    private fun remapAnswerLine(line: String, transform: (String) -> String): String {
        val encoded = line.removePrefix(AnswerPrefix)
        val parts = encoded.split(FieldSeparator)
        if (parts.size != AnswerFieldCount) {
            throw StudyPlanPackageException("Practice attempt configuration contains an invalid answer record.")
        }
        val rawQuestionId = decodeBase64Url(parts[0])
        val mapped = requireMappedId(transform(rawQuestionId))
        return AnswerPrefix + parts.toMutableList().also { values ->
            values[0] = encodeBase64Url(mapped)
        }.joinToString(FieldSeparator)
    }

    private fun decodeAnswerQuestionId(line: String): String {
        val parts = line.removePrefix(AnswerPrefix).split(FieldSeparator)
        if (parts.size != AnswerFieldCount) {
            throw StudyPlanPackageException("Practice attempt configuration contains an invalid answer record.")
        }
        return decodeBase64Url(parts[0]).also { id ->
            if (id.isBlank()) {
                throw StudyPlanPackageException("Practice attempt configuration contains a blank question id.")
            }
        }
    }

    private fun requireMappedId(id: String): String {
        if (id.isBlank()) throw StudyPlanPackageException("Practice attempt question id cannot be blank.")
        return id
    }

    private fun encodeBase64Url(value: String): String = value.encodeToByteArray().toBase64UrlNoPadding()

    private fun decodeBase64Url(value: String): String = try {
        value.fromBase64UrlNoPadding().decodeToString()
    } catch (error: IllegalArgumentException) {
        throw StudyPlanPackageException("Practice attempt configuration contains an invalid Base64Url question id.")
    }

    private fun ByteArray.toBase64UrlNoPadding(): String = buildString((size * 4 + 2) / 3) {
        var index = 0
        while (index + 2 < size) {
            val chunk = ((this@toBase64UrlNoPadding[index].toInt() and 0xFF) shl 16) or
                ((this@toBase64UrlNoPadding[index + 1].toInt() and 0xFF) shl 8) or
                (this@toBase64UrlNoPadding[index + 2].toInt() and 0xFF)
            append(Base64UrlAlphabet[(chunk ushr 18) and 0x3F])
            append(Base64UrlAlphabet[(chunk ushr 12) and 0x3F])
            append(Base64UrlAlphabet[(chunk ushr 6) and 0x3F])
            append(Base64UrlAlphabet[chunk and 0x3F])
            index += 3
        }
        when (size - index) {
            1 -> {
                val chunk = (this@toBase64UrlNoPadding[index].toInt() and 0xFF) shl 4
                append(Base64UrlAlphabet[(chunk ushr 6) and 0x3F])
                append(Base64UrlAlphabet[chunk and 0x3F])
            }
            2 -> {
                val chunk = ((this@toBase64UrlNoPadding[index].toInt() and 0xFF) shl 10) or
                    ((this@toBase64UrlNoPadding[index + 1].toInt() and 0xFF) shl 2)
                append(Base64UrlAlphabet[(chunk ushr 12) and 0x3F])
                append(Base64UrlAlphabet[(chunk ushr 6) and 0x3F])
                append(Base64UrlAlphabet[chunk and 0x3F])
            }
        }
    }

    private fun String.fromBase64UrlNoPadding(): ByteArray {
        val output = java.io.ByteArrayOutputStream(length * 3 / 4)
        var buffer = 0
        var bits = 0
        forEach { character ->
            val value = Base64UrlAlphabet.indexOf(character)
            require(value >= 0) { "Invalid Base64Url character." }
            buffer = (buffer shl 6) or value
            bits += 6
            while (bits >= 8) {
                bits -= 8
                output.write((buffer shr bits) and 0xFF)
            }
        }
        require(bits == 0 || (buffer and ((1 shl bits) - 1)) == 0) { "Invalid Base64Url tail bits." }
        return output.toByteArray()
    }

    private const val QuestionIdsPrefix = "questionIds="
    private const val AnswerPrefix = "answer="
    private const val FieldSeparator = "|"
    private const val AnswerFieldCount = 6
    private const val Base64UrlAlphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
}
