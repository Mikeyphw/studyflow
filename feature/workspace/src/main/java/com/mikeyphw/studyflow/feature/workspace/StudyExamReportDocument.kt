package com.mikeyphw.studyflow.feature.workspace

enum class StudyExamReportFormat {
    HTML,
    PDF,
}

data class StudyExamReportRow(
    val label: String,
    val value: String,
)

data class StudyExamReportSection(
    val title: String,
    val rows: List<StudyExamReportRow>,
)

data class StudyExamReportDocument(
    val title: String,
    val fileStem: String,
    val generatedAtLabel: String,
    val sections: List<StudyExamReportSection>,
    val privacyNotice: String,
) {
    init {
        require(title.isNotBlank())
        require(fileStem.isNotBlank())
        require(sections.isNotEmpty())
    }
}
