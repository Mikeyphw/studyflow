package com.mikeyphw.studyflow

import com.mikeyphw.studyflow.core.model.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Test

class AppContainerContractTest {
    @Test
    fun `Brazilian Portuguese uses a stable language tag`() {
        assertEquals("pt-BR", AppLanguage.PORTUGUESE_BRAZIL.languageTag)
    }
}
