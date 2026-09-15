package com.mikeyphw.studyflow.core.model

data class LanguageDeckDashboardSummary(
    val expressionCount: Int,
    val idiomCount: Int,
    val vocabularyCount: Int,
    val dueCount: Int,
    val learnedCount: Int,
    val usageAccuracyPercent: Int,
) {
    init {
        require(expressionCount >= 0) { "Expression count cannot be negative." }
        require(idiomCount >= 0) { "Idiom count cannot be negative." }
        require(vocabularyCount >= 0) { "Vocabulary count cannot be negative." }
        require(dueCount >= 0) { "Due count cannot be negative." }
        require(learnedCount >= 0) { "Learned count cannot be negative." }
        require(usageAccuracyPercent in 0..100) { "Language accuracy must be 0 to 100." }
    }
}
