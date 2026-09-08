package com.mikeyphw.studyflow.tv.focus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyFlowTvFocusPolicyTest {
    private val items = listOf(
        StudyFlowTvFocusItem("home", StudyFlowTvFocusRole.NAVIGATION_ITEM, row = 0, column = 0),
        StudyFlowTvFocusItem("today", StudyFlowTvFocusRole.CONTENT_CARD, row = 0, column = 1),
        StudyFlowTvFocusItem("practice", StudyFlowTvFocusRole.QUESTION_CHOICE, row = 1, column = 0),
        StudyFlowTvFocusItem("mock", StudyFlowTvFocusRole.PRIMARY_ACTION, row = 1, column = 1),
        StudyFlowTvFocusItem("disabled", StudyFlowTvFocusRole.DIALOG_ACTION, row = 0, column = 2, enabled = false),
    )

    @Test
    fun initialFocusIsDeterministicAndSkipsDisabledItems() {
        assertTrue(StudyFlowTvFocusPolicy.deterministicInitialFocusRequired)
        assertTrue(StudyFlowTvFocusPolicy.visibleFocusRequired)
        assertTrue(StudyFlowTvFocusPolicy.restoresFocusAfterNavigation)
        assertTrue(StudyFlowTvFocusPolicy.directionalNavigationMustNotWrap)
        assertEquals(64, StudyFlowTvFocusPolicy.minimumFocusTargetDp)
        assertEquals("home", StudyFlowTvFocusPolicy.initialFocusId(items))
        assertNull(StudyFlowTvFocusPolicy.initialFocusId(items.filterNot { it.enabled }))
    }

    @Test
    fun dpadNavigationMovesAcrossRowsAndColumnsWithoutWrapping() {
        assertEquals("today", StudyFlowTvFocusPolicy.nextFocusId(items, "home", StudyFlowTvRemoteCommand.RIGHT))
        assertEquals("home", StudyFlowTvFocusPolicy.nextFocusId(items, "today", StudyFlowTvRemoteCommand.LEFT))
        assertEquals("mock", StudyFlowTvFocusPolicy.nextFocusId(items, "today", StudyFlowTvRemoteCommand.DOWN))
        assertEquals("today", StudyFlowTvFocusPolicy.nextFocusId(items, "mock", StudyFlowTvRemoteCommand.UP))
        assertEquals("today", StudyFlowTvFocusPolicy.nextFocusId(items, "today", StudyFlowTvRemoteCommand.RIGHT))
        assertEquals("home", StudyFlowTvFocusPolicy.nextFocusId(items, "home", StudyFlowTvRemoteCommand.LEFT))
    }

    @Test
    fun selectBackAndPlayPauseKeepCurrentFocus() {
        assertEquals("mock", StudyFlowTvFocusPolicy.nextFocusId(items, "mock", StudyFlowTvRemoteCommand.SELECT))
        assertEquals("mock", StudyFlowTvFocusPolicy.nextFocusId(items, "mock", StudyFlowTvRemoteCommand.BACK))
        assertEquals("mock", StudyFlowTvFocusPolicy.nextFocusId(items, "mock", StudyFlowTvRemoteCommand.PLAY_PAUSE))
    }

    @Test
    fun androidRemoteKeyCodesMapToSmallCommands() {
        assertEquals(StudyFlowTvRemoteCommand.UP, StudyFlowTvRemoteKeyMapper.commandForKeyCode(StudyFlowTvRemoteKeyMapper.keyCodeDpadUp))
        assertEquals(StudyFlowTvRemoteCommand.DOWN, StudyFlowTvRemoteKeyMapper.commandForKeyCode(StudyFlowTvRemoteKeyMapper.keyCodeDpadDown))
        assertEquals(StudyFlowTvRemoteCommand.LEFT, StudyFlowTvRemoteKeyMapper.commandForKeyCode(StudyFlowTvRemoteKeyMapper.keyCodeDpadLeft))
        assertEquals(StudyFlowTvRemoteCommand.RIGHT, StudyFlowTvRemoteKeyMapper.commandForKeyCode(StudyFlowTvRemoteKeyMapper.keyCodeDpadRight))
        assertEquals(StudyFlowTvRemoteCommand.SELECT, StudyFlowTvRemoteKeyMapper.commandForKeyCode(StudyFlowTvRemoteKeyMapper.keyCodeDpadCenter))
        assertEquals(StudyFlowTvRemoteCommand.SELECT, StudyFlowTvRemoteKeyMapper.commandForKeyCode(StudyFlowTvRemoteKeyMapper.keyCodeEnter))
        assertEquals(StudyFlowTvRemoteCommand.BACK, StudyFlowTvRemoteKeyMapper.commandForKeyCode(StudyFlowTvRemoteKeyMapper.keyCodeBack))
        assertEquals(StudyFlowTvRemoteCommand.PLAY_PAUSE, StudyFlowTvRemoteKeyMapper.commandForKeyCode(StudyFlowTvRemoteKeyMapper.keyCodeMediaPlayPause))
        assertNull(StudyFlowTvRemoteKeyMapper.commandForKeyCode(-1))
    }
}
