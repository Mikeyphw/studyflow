package com.mikeyphw.studyflow.feature.settings

import com.mikeyphw.studyflow.core.model.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsContractTest {
    @Test
    fun `theme values are stable domain identifiers`() {
        assertEquals("SYSTEM_DEFAULT", ThemeMode.SYSTEM_DEFAULT.name)
    }
}
