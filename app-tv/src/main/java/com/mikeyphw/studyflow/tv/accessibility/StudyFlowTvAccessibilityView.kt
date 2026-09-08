package com.mikeyphw.studyflow.tv.accessibility

import android.os.Build
import android.view.View
import android.widget.TextView

/** Native-view accessibility helpers; no AndroidX TV or Compose runtime is required. */
fun TextView.applyStudyFlowTvTenFootText(
    role: StudyFlowTvTextRole,
    heading: Boolean = false,
) {
    val spec = StudyFlowTvAccessibilityPolicy.typography(role)
    textSize = spec.sizeSp.toFloat()
    includeFontPadding = true
    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
    if (heading && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        isAccessibilityHeading = true
    }
}

fun View.applyStudyFlowTvAccessibilityNode() {
    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
}
