package com.mikeyphw.studyflow.desktop

import com.mikeyphw.studyflow.core.model.AppLanguage
import java.nio.file.Files
import java.util.Locale
import kotlin.test.assertEquals
import org.junit.Test

class DesktopLocalizationPersistenceTest {
    @Test
    fun englishPortugueseAndSystemLocaleResolveDeterministically() {
        assertEquals(
            "Settings",
            DesktopTextCatalog.forLanguage(AppLanguage.ENGLISH, Locale.US).text("Settings"),
        )
        assertEquals(
            "Configurações",
            DesktopTextCatalog.forLanguage(AppLanguage.PORTUGUESE_BRAZIL, Locale.US).text("Settings"),
        )
        assertEquals(
            AppLanguage.PORTUGUESE_BRAZIL,
            DesktopTextCatalog.forLanguage(AppLanguage.SYSTEM_DEFAULT, Locale.forLanguageTag("pt-BR")).resolvedLanguage,
        )
        assertEquals(
            AppLanguage.ENGLISH,
            DesktopTextCatalog.forLanguage(AppLanguage.SYSTEM_DEFAULT, Locale.US).resolvedLanguage,
        )
    }

    @Test
    fun selectedLanguagePersistsAcrossWorkspaceRestart() {
        val root = Files.createTempDirectory("studyflow-dov21-locale-")
        try {
            val first = services(root)
            first.setLanguage(AppLanguage.PORTUGUESE_BRAZIL)

            val reopened = services(root)
            assertEquals(AppLanguage.PORTUGUESE_BRAZIL, reopened.preferencesStore.current().language)
            assertEquals(
                "Configurações",
                DesktopTextCatalog.forLanguage(reopened.preferencesStore.current().language).text("Settings"),
            )
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    private fun services(root: java.nio.file.Path): DesktopStudyFlowServices {
        val persistence = DesktopWorkspacePersistence.open(
            root = root,
            seed = DesktopWorkspaceSnapshot.seed(DesktopStudyFlowSampleData.create()),
        )
        return createDesktopStudyFlowServices(workspacePersistence = persistence)
    }
}
