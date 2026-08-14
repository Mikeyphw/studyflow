package com.mikeyphw.studyflow.core.i18n

import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DataStoreAppPreferencesRepositoryContractTest {
    @Test
    fun `storage corruption and io failures have separate recovery boundaries`() {
        val source = repositorySource()
        assertTrue(source.contains("ReplaceFileCorruptionHandler { emptyPreferences() }"))
        assertTrue(source.contains("if (error is IOException) throw AppPreferencesStorageException(error)"))
        assertFalse(source.contains("if (error is IOException) emit(emptyPreferences())"))
    }

    @Test
    fun `stored language is a mirror and cannot overwrite appcompat authority`() {
        val source = repositorySource()
        assertTrue(source.contains("language = AppLocaleController.currentLanguage()"))
        assertTrue(source.contains("values[LanguageKey] = language.name"))
        assertFalse(source.contains("AppLocaleController.apply(values[LanguageKey]"))
    }

    private fun repositorySource(): String {
        val module = Paths.get("src/main/java/com/mikeyphw/studyflow/core/i18n/DataStoreAppPreferencesRepository.kt")
        val root = Paths.get(
            "core/i18n/src/main/java/com/mikeyphw/studyflow/core/i18n/DataStoreAppPreferencesRepository.kt")
        val path: Path = listOf(module, root).first { it.toFile().isFile }
        return path.toFile().readText()
    }
}
