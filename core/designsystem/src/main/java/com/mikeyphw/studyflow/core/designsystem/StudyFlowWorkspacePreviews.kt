package com.mikeyphw.studyflow.core.designsystem

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.mikeyphw.studyflow.core.model.ThemeMode

@Preview(name = "Workspace light", showBackground = true)
@Composable
private fun WorkspaceLightPreview() {
    StudyFlowTheme(themeMode = ThemeMode.LIGHT, dynamicColorEnabled = false) {
        PreviewContent()
    }
}

@Preview(name = "Workspace dark", showBackground = true)
@Composable
private fun WorkspaceDarkPreview() {
    StudyFlowTheme(themeMode = ThemeMode.DARK, dynamicColorEnabled = false) {
        PreviewContent()
    }
}

@Composable
private fun PreviewContent() {
    StudyFlowSubjectAccordion(
        title = "Constitutional law",
        supportingText = "8 topics · Next: Fundamental rights",
        status = "62% progress",
        expanded = true,
        expandedStateDescription = "Expanded",
        collapsedStateDescription = "Collapsed",
        onExpandedChange = {},
    ) {
        Text("Fundamental rights · 40%")
        Text("Constitutional remedies · 75%")
    }
}
