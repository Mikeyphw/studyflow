package com.mikeyphw.studyflow.tv.focus

import android.view.View
import com.mikeyphw.studyflow.tv.R
import kotlin.math.roundToInt

/** Native Android focus helper kept dependency-free for the TV APK. */
fun View.applyStudyFlowTvDpadFocus() {
    applyStudyFlowTvDpadFocus(highVisibility = false)
}

fun View.applyStudyFlowTvDpadFocus(highVisibility: Boolean) {
    isFocusable = true
    isFocusableInTouchMode = false
    val minimumPx = (StudyFlowTvFocusPolicy.minimumFocusTargetDp * resources.displayMetrics.density).roundToInt()
    minimumWidth = maxOf(minimumWidth, minimumPx)
    minimumHeight = maxOf(minimumHeight, minimumPx)
    if (highVisibility) {
        setBackgroundResource(R.drawable.studyflow_tv_focus_background_high_visibility)
    } else {
        setBackgroundResource(R.drawable.studyflow_tv_focus_background)
    }
}
