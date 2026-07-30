package com.mikeyphw.studyflow.core.i18n

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.mikeyphw.studyflow.core.model.AppLanguage

object AppLocaleController {
    fun apply(language: AppLanguage) {
        val locales = language.languageTag
            ?.let(LocaleListCompat::forLanguageTags)
            ?: LocaleListCompat.getEmptyLocaleList()
        AppCompatDelegate.setApplicationLocales(locales)
    }

    fun currentLanguage(): AppLanguage {
        val languageTag = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        return AppLanguage.entries.firstOrNull { it.languageTag == languageTag }
            ?: AppLanguage.SYSTEM_DEFAULT
    }
}
