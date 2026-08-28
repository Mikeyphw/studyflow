package com.mikeyphw.studyflow.core.domain

enum class PublicExamFinalStretchBand {
    BEYOND_30_DAYS,
    DAYS_30,
    DAYS_15,
    DAYS_7,
    DAYS_2,
    EXAM_EVE,
}

data class PublicExamFinalStretchAllocation(
    val newContentPercent: Int,
    val reviewPercent: Int,
    val errorNotebookPercent: Int,
    val questionsPercent: Int,
    val mockExamPercent: Int,
    val legislationPercent: Int,
) {
    init {
        val values = listOf(
            newContentPercent,
            reviewPercent,
            errorNotebookPercent,
            questionsPercent,
            mockExamPercent,
            legislationPercent,
        )
        require(values.all { value -> value in 0..100 }) { "Final-stretch allocations must be percentages." }
        require(values.sum() == 100) { "Final-stretch allocations must total 100 percent." }
    }
}

data class PublicExamFinalStretchStrategy(
    val daysUntilExam: Int,
    val band: PublicExamFinalStretchBand,
    val allocation: PublicExamFinalStretchAllocation,
) {
    init {
        require(daysUntilExam >= 0) { "Days until exam cannot be negative." }
    }
}

object PublicExamFinalStretchPlanner {
    fun plan(daysUntilExam: Int?): PublicExamFinalStretchStrategy? {
        val days = daysUntilExam ?: return null
        require(days >= 0) { "Days until exam cannot be negative." }
        val band = when {
            days > 30 -> PublicExamFinalStretchBand.BEYOND_30_DAYS
            days > 15 -> PublicExamFinalStretchBand.DAYS_30
            days > 7 -> PublicExamFinalStretchBand.DAYS_15
            days > 2 -> PublicExamFinalStretchBand.DAYS_7
            days == 2 -> PublicExamFinalStretchBand.DAYS_2
            else -> PublicExamFinalStretchBand.EXAM_EVE
        }
        return PublicExamFinalStretchStrategy(days, band, allocationFor(band))
    }

    private fun allocationFor(band: PublicExamFinalStretchBand): PublicExamFinalStretchAllocation = when (band) {
        PublicExamFinalStretchBand.BEYOND_30_DAYS -> allocation(35, 15, 10, 25, 5, 10)
        PublicExamFinalStretchBand.DAYS_30 -> allocation(25, 20, 10, 25, 10, 10)
        PublicExamFinalStretchBand.DAYS_15 -> allocation(18, 22, 12, 28, 10, 10)
        PublicExamFinalStretchBand.DAYS_7 -> allocation(10, 25, 15, 30, 10, 10)
        PublicExamFinalStretchBand.DAYS_2 -> allocation(5, 30, 20, 25, 10, 10)
        PublicExamFinalStretchBand.EXAM_EVE -> allocation(0, 35, 20, 20, 10, 15)
    }

    private fun allocation(
        newContent: Int,
        review: Int,
        errors: Int,
        questions: Int,
        mocks: Int,
        legislation: Int,
    ) = PublicExamFinalStretchAllocation(newContent, review, errors, questions, mocks, legislation)
}
