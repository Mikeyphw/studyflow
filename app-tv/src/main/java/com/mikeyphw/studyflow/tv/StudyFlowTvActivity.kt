package com.mikeyphw.studyflow.tv

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView

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
    }
}
