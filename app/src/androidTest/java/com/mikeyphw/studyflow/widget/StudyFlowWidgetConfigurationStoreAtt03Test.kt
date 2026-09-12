package com.mikeyphw.studyflow.widget

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StudyFlowWidgetConfigurationStoreAtt03Test {
    @Test
    fun persistsPerInstanceModePlanAndMigratesRestoredWidgetId() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.getSharedPreferences("studyflow_widget_configuration_v2", android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
        val store = StudyFlowWidgetConfigurationStore(context)

        store.save(StudyFlowWidgetConfiguration(41, StudyFlowWidgetMode.SESSION, "plan-a", false))
        assertEquals(StudyFlowWidgetMode.SESSION, store.load(41).mode)
        assertEquals("plan-a", store.load(41).studyPlanId)

        store.restore(intArrayOf(41), intArrayOf(77))
        assertEquals(StudyFlowWidgetMode.SESSION, store.load(77).mode)
        assertEquals("plan-a", store.load(77).studyPlanId)

        store.delete(intArrayOf(77))
        val legacyDefault = store.load(77)
        assertEquals(StudyFlowWidgetMode.TODAY, legacyDefault.mode)
        assertNull(legacyDefault.studyPlanId)
    }
}
