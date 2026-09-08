package com.mikeyphw.studyflow.tv.focus

import android.view.View
import com.mikeyphw.studyflow.tv.R
import kotlin.math.roundToInt

/**
 * Native Android focus helper for the structured TV shell.
 *
 * This stays in the app-tv module and intentionally avoids Compose or AndroidX TV dependencies.
 */
fun View.applyStudyFlowTvDpadFocus() {
    isFocusable = true
    isFocusableInTouchMode = false
    val minimumPx = (StudyFlowTvFocusPolicy.minimumFocusTargetDp * resources.displayMetrics.density).roundToInt()
    minimumWidth = maxOf(minimumWidth, minimumPx)
    minimumHeight = maxOf(minimumHeight, minimumPx)
    setBackgroundResource(R.drawable.studyflow_tv_focus_background)
}
