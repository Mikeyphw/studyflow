package com.mikeyphw.studyflow.core.designsystem

import android.provider.Settings
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

object StudyFlowMotionTokens {
    const val FastMillis = 140
    const val StandardMillis = 220
    const val DeliberateMillis = 300
}

@Composable
fun rememberStudyFlowMotionEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) > 0f
    }
}

fun <T> studyFlowAnimationSpec(
    enabled: Boolean,
    durationMillis: Int = StudyFlowMotionTokens.StandardMillis,
): FiniteAnimationSpec<T> = if (enabled) tween(durationMillis = durationMillis) else snap()
