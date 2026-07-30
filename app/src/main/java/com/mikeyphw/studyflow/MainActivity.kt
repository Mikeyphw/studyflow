package com.mikeyphw.studyflow

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as StudyFlowApplication).container
        setContent {
            StudyFlowApp(container = container)
        }
    }
}
