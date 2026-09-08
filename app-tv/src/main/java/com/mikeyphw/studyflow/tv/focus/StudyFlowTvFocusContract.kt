package com.mikeyphw.studyflow.tv.focus

/**
 * Size-safe Android TV focus contract introduced in TVOV04.
 *
 * The policy is pure Kotlin on purpose. It gives the TV shell deterministic D-pad behavior
 * without pulling in Compose, AndroidX TV UI, or transport dependencies.
 */
enum class StudyFlowTvRemoteCommand {
    UP,
    DOWN,
    LEFT,
    RIGHT,
    SELECT,
    BACK,
    PLAY_PAUSE,
}

enum class StudyFlowTvFocusRole {
    PRIMARY_ACTION,
    NAVIGATION_ITEM,
    CONTENT_CARD,
    QUESTION_CHOICE,
    DIALOG_ACTION,
}

data class StudyFlowTvFocusItem(
    val id: String,
    val role: StudyFlowTvFocusRole,
    val row: Int,
    val column: Int,
    val enabled: Boolean = true,
) {
    init {
        require(id.isNotBlank()) { "TV focus item id must not be blank." }
        require(row >= 0) { "TV focus item row must be non-negative." }
        require(column >= 0) { "TV focus item column must be non-negative." }
    }
}

object StudyFlowTvRemoteKeyMapper {
    const val keyCodeDpadUp = 19
    const val keyCodeDpadDown = 20
    const val keyCodeDpadLeft = 21
    const val keyCodeDpadRight = 22
    const val keyCodeDpadCenter = 23
    const val keyCodeEnter = 66
    const val keyCodeBack = 4
    const val keyCodeMediaPlayPause = 85

    fun commandForKeyCode(keyCode: Int): StudyFlowTvRemoteCommand? = when (keyCode) {
        keyCodeDpadUp -> StudyFlowTvRemoteCommand.UP
        keyCodeDpadDown -> StudyFlowTvRemoteCommand.DOWN
        keyCodeDpadLeft -> StudyFlowTvRemoteCommand.LEFT
        keyCodeDpadRight -> StudyFlowTvRemoteCommand.RIGHT
        keyCodeDpadCenter,
        keyCodeEnter,
        -> StudyFlowTvRemoteCommand.SELECT
        keyCodeBack -> StudyFlowTvRemoteCommand.BACK
        keyCodeMediaPlayPause -> StudyFlowTvRemoteCommand.PLAY_PAUSE
        else -> null
    }
}

object StudyFlowTvFocusPolicy {
    const val minimumFocusTargetDp = 64
    const val visibleFocusRequired = true
    const val deterministicInitialFocusRequired = true
    const val restoresFocusAfterNavigation = true
    const val directionalNavigationMustNotWrap = true

    fun initialFocusId(items: List<StudyFlowTvFocusItem>): String? =
        items.enabledSorted().firstOrNull()?.id

    fun nextFocusId(
        items: List<StudyFlowTvFocusItem>,
        currentId: String?,
        command: StudyFlowTvRemoteCommand,
    ): String? {
        val enabledItems = items.enabledSorted()
        if (enabledItems.isEmpty()) return null
        val current = enabledItems.firstOrNull { it.id == currentId } ?: enabledItems.first()
        val next = when (command) {
            StudyFlowTvRemoteCommand.UP -> enabledItems
                .filter { it.row < current.row }
                .maxOfOrNull { it.row }
                ?.let { nearestInRow(enabledItems, it, current.column) }
            StudyFlowTvRemoteCommand.DOWN -> enabledItems
                .filter { it.row > current.row }
                .minOfOrNull { it.row }
                ?.let { nearestInRow(enabledItems, it, current.column) }
            StudyFlowTvRemoteCommand.LEFT -> enabledItems
                .filter { it.row == current.row && it.column < current.column }
                .maxByOrNull { it.column }
            StudyFlowTvRemoteCommand.RIGHT -> enabledItems
                .filter { it.row == current.row && it.column > current.column }
                .minByOrNull { it.column }
            StudyFlowTvRemoteCommand.SELECT,
            StudyFlowTvRemoteCommand.BACK,
            StudyFlowTvRemoteCommand.PLAY_PAUSE,
            -> current
        }
        return (next ?: current).id
    }

    private fun List<StudyFlowTvFocusItem>.enabledSorted(): List<StudyFlowTvFocusItem> =
        filter { it.enabled }.sortedWith(compareBy({ it.row }, { it.column }, { it.id }))

    private fun nearestInRow(
        items: List<StudyFlowTvFocusItem>,
        row: Int,
        targetColumn: Int,
    ): StudyFlowTvFocusItem? = items
        .filter { it.row == row }
        .minWithOrNull(
            compareBy<StudyFlowTvFocusItem> { kotlin.math.abs(it.column - targetColumn) }
                .thenBy { it.column }
                .thenBy { it.id },
        )
}
