package com.mikeyphw.studyflow.tv

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.mikeyphw.studyflow.tv.focus.applyStudyFlowTvDpadFocus

class StudyFlowTvActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(createContentView())
    }

    private fun createContentView(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setBackgroundColor(Color.rgb(11, 18, 32))
        setPadding(72, 72, 72, 72)
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )

        addView(
            TextView(context).apply {
                text = getString(R.string.tv_app_title)
                setTextColor(Color.WHITE)
                textSize = 34f
                gravity = Gravity.CENTER
            },
        )
        addView(
            TextView(context).apply {
                text = getString(R.string.tv_app_status)
                setTextColor(Color.rgb(199, 210, 254))
                textSize = 20f
                gravity = Gravity.CENTER
                setPadding(0, 24, 0, 0)
            },
        )
        addView(
            TextView(context).apply {
                text = getString(R.string.tv_app_next_step)
                setTextColor(Color.rgb(148, 163, 184))
                textSize = 16f
                gravity = Gravity.CENTER
                setPadding(0, 18, 0, 0)
            },
        )

        val primaryAction = tvActionButton(R.string.tv_app_primary_action)
        val pairPhoneAction = tvActionButton(R.string.tv_app_pair_phone_action)
        val practiceAction = tvActionButton(R.string.tv_app_practice_action)
        primaryAction.nextFocusRightId = pairPhoneAction.id
        pairPhoneAction.nextFocusLeftId = primaryAction.id
        pairPhoneAction.nextFocusRightId = practiceAction.id
        practiceAction.nextFocusLeftId = pairPhoneAction.id

        addView(
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                setPadding(0, 34, 0, 0)
                addView(primaryAction)
                addView(pairPhoneAction)
                addView(practiceAction)
            },
        )
        post { primaryAction.requestFocus() }
    }

    private fun tvActionButton(labelResId: Int): Button = Button(this).apply {
        id = View.generateViewId()
        text = getString(labelResId)
        textSize = 18f
        isAllCaps = false
        setTextColor(Color.WHITE)
        setPadding(32, 18, 32, 18)
        applyStudyFlowTvDpadFocus()
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            marginStart = 12
            marginEnd = 12
        }
    }
}
