package com.mikeyphw.studyflow.tv.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyFlowTvAccessibilityPolicyTest {
    @Test
    fun tenFootTypographyAndTargetsStayAboveTheSealMinimums() {
        assertEquals("TVOV17", StudyFlowTvAccessibilityPolicy.overlay)
        assertTrue(StudyFlowTvAccessibilityPolicy.tenFootUiSealed)
        assertTrue(StudyFlowTvAccessibilityPolicy.scrollableFocusedContentRequired)
        assertTrue(StudyFlowTvAccessibilityPolicy.semanticHeadingsRequired)
        assertTrue(StudyFlowTvAccessibilityPolicy.politeLiveStatusRequired)
        assertTrue(StudyFlowTvAccessibilityPolicy.touchInputNotRequired)
        assertEquals(64, StudyFlowTvAccessibilityPolicy.layout.minimumFocusTargetDp)
        assertTrue(
            StudyFlowTvAccessibilityPolicy.typography(StudyFlowTvTextRole.TITLE).sizeSp >=
                StudyFlowTvAccessibilityPolicy.minimumTitleTextSp,
        )
        assertTrue(
            StudyFlowTvAccessibilityPolicy.typography(StudyFlowTvTextRole.BODY).sizeSp >=
                StudyFlowTvAccessibilityPolicy.minimumBodyTextSp,
        )
        assertTrue(
            StudyFlowTvAccessibilityPolicy.typography(StudyFlowTvTextRole.CONTROL).sizeSp >=
                StudyFlowTvAccessibilityPolicy.minimumControlTextSp,
        )
    }

    @Test
    fun shellPaletteMeetsTextContrastAndHighVisibilityFocusIsStronger() {
        assertTrue(
            StudyFlowTvAccessibilityPolicy.meetsTextContrast(
                StudyFlowTvAccessibilityPolicy.primaryTextRgb,
                StudyFlowTvAccessibilityPolicy.backgroundRgb,
            ),
        )
        assertTrue(
            StudyFlowTvAccessibilityPolicy.meetsTextContrast(
                StudyFlowTvAccessibilityPolicy.secondaryTextRgb,
                StudyFlowTvAccessibilityPolicy.backgroundRgb,
            ),
        )
        assertTrue(
            StudyFlowTvAccessibilityPolicy.meetsTextContrast(
                StudyFlowTvAccessibilityPolicy.mutedTextRgb,
                StudyFlowTvAccessibilityPolicy.backgroundRgb,
            ),
        )
        assertTrue(
            StudyFlowTvAccessibilityPolicy.meetsTextContrast(
                StudyFlowTvAccessibilityPolicy.primaryTextRgb,
                StudyFlowTvAccessibilityPolicy.focusedBackgroundRgb,
            ),
        )
        assertTrue(
            StudyFlowTvAccessibilityPolicy.focusStrokeDp(highVisibility = true) >
                StudyFlowTvAccessibilityPolicy.focusStrokeDp(highVisibility = false),
        )
    }
}
