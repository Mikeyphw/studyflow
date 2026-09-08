package com.mikeyphw.studyflow.tv.accessibility

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

enum class StudyFlowTvTextRole {
    BRAND,
    TITLE,
    BODY,
    QUESTION,
    SECTION,
    CONTROL,
    STATUS,
}

data class StudyFlowTvTypographySpec(
    val sizeSp: Int,
    val minimumLineHeightSp: Int,
) {
    init {
        require(sizeSp > 0) { "TV typography size must be positive." }
        require(minimumLineHeightSp >= sizeSp) { "TV line height must not be smaller than text size." }
    }
}

data class StudyFlowTvTenFootLayoutSpec(
    val horizontalSafeAreaDp: Int,
    val verticalSafeAreaDp: Int,
    val navigationRailWidthDp: Int,
    val contentGapDp: Int,
    val cardWidthDp: Int,
    val minimumFocusTargetDp: Int,
) {
    init {
        require(horizontalSafeAreaDp >= 32) { "TV horizontal safe area is too small." }
        require(verticalSafeAreaDp >= 32) { "TV vertical safe area is too small." }
        require(navigationRailWidthDp >= 240) { "TV navigation rail is too narrow for 10-foot labels." }
        require(contentGapDp >= 32) { "TV content gap is too small." }
        require(cardWidthDp >= 240) { "TV cards are too narrow for 10-foot reading." }
        require(minimumFocusTargetDp >= 48) { "TV focus target must meet the accessibility minimum." }
    }
}

/** TVOV17 accessibility and 10-foot viewing policy. Pure Kotlin keeps it unit-testable and tiny. */
object StudyFlowTvAccessibilityPolicy {
    const val overlay = "TVOV17"
    const val tenFootUiSealed = true
    const val nativeAccessibilitySemanticsRequired = true
    const val scrollableFocusedContentRequired = true
    const val semanticHeadingsRequired = true
    const val politeLiveStatusRequired = true
    const val touchInputNotRequired = true
    const val highVisibilityFocusMustAffectRendering = true

    const val minimumFocusTargetDp = 64
    const val minimumTitleTextSp = 34
    const val minimumBodyTextSp = 20
    const val minimumControlTextSp = 18
    const val minimumStatusTextSp = 16
    const val minimumContrastRatio = 4.5
    const val standardFocusStrokeDp = 3
    const val highVisibilityFocusStrokeDp = 5

    const val backgroundRgb = 0x0B1220
    const val primaryTextRgb = 0xFFFFFF
    const val secondaryTextRgb = 0xC7D2FE
    const val mutedTextRgb = 0x94A3B8
    const val focusedBackgroundRgb = 0x1D4ED8
    const val highVisibilityFocusRgb = 0xFDE047

    val layout = StudyFlowTvTenFootLayoutSpec(
        horizontalSafeAreaDp = 56,
        verticalSafeAreaDp = 48,
        navigationRailWidthDp = 292,
        contentGapDp = 48,
        cardWidthDp = 260,
        minimumFocusTargetDp = minimumFocusTargetDp,
    )

    fun typography(role: StudyFlowTvTextRole): StudyFlowTvTypographySpec = when (role) {
        StudyFlowTvTextRole.BRAND -> StudyFlowTvTypographySpec(sizeSp = 28, minimumLineHeightSp = 34)
        StudyFlowTvTextRole.TITLE -> StudyFlowTvTypographySpec(sizeSp = 36, minimumLineHeightSp = 42)
        StudyFlowTvTextRole.BODY -> StudyFlowTvTypographySpec(sizeSp = 20, minimumLineHeightSp = 26)
        StudyFlowTvTextRole.QUESTION -> StudyFlowTvTypographySpec(sizeSp = 22, minimumLineHeightSp = 28)
        StudyFlowTvTextRole.SECTION -> StudyFlowTvTypographySpec(sizeSp = 20, minimumLineHeightSp = 26)
        StudyFlowTvTextRole.CONTROL -> StudyFlowTvTypographySpec(sizeSp = 18, minimumLineHeightSp = 24)
        StudyFlowTvTextRole.STATUS -> StudyFlowTvTypographySpec(sizeSp = 16, minimumLineHeightSp = 22)
    }

    fun focusStrokeDp(highVisibility: Boolean): Int =
        if (highVisibility) highVisibilityFocusStrokeDp else standardFocusStrokeDp

    fun contrastRatio(foregroundRgb: Int, backgroundRgb: Int): Double {
        val foreground = relativeLuminance(foregroundRgb)
        val background = relativeLuminance(backgroundRgb)
        val lighter = max(foreground, background)
        val darker = min(foreground, background)
        return (lighter + 0.05) / (darker + 0.05)
    }

    fun meetsTextContrast(foregroundRgb: Int, backgroundRgb: Int): Boolean =
        contrastRatio(foregroundRgb, backgroundRgb) >= minimumContrastRatio

    private fun relativeLuminance(rgb: Int): Double {
        val red = linearChannel((rgb shr 16) and 0xFF)
        val green = linearChannel((rgb shr 8) and 0xFF)
        val blue = linearChannel(rgb and 0xFF)
        return (0.2126 * red) + (0.7152 * green) + (0.0722 * blue)
    }

    private fun linearChannel(channel: Int): Double {
        val srgb = channel / 255.0
        return if (srgb <= 0.04045) srgb / 12.92 else ((srgb + 0.055) / 1.055).pow(2.4)
    }
}
