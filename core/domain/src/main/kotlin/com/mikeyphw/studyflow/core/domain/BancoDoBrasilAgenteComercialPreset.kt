package com.mikeyphw.studyflow.core.domain

/**
 * Stable facts carried by the bundled BB Agente Comercial reference preset.
 *
 * The preset is explicitly pre-edital. It uses Seleção Externa 2022/001 only as a
 * programmatic reference and deliberately carries no 2026 exam date or topic-incidence guesses.
 */
object BancoDoBrasilAgenteComercialPreset {
    const val ReferenceNotice = "Seleção Externa 2022/001"
    const val ReferenceNoticeUrl =
        "https://www.bb.com.br/docs/portal/dipes/Edital-de-Abertura-de-Selecao-Externa-2022-01.pdf"
    const val ObjectiveQuestionCount = 70
    const val ObjectiveMaximumPoints = 100.0
    const val EssayMinimumScorePercent = 70
    const val WeeklyGoalHours = 19
    const val ReferenceScheduledMinutes = 1_140

    val ObjectiveWeights: Map<OnboardingSampleSubjectKind, Double> = linkedMapOf(
        OnboardingSampleSubjectKind.BB_PORTUGUESE to 15.0,
        OnboardingSampleSubjectKind.BB_ENGLISH to 5.0,
        OnboardingSampleSubjectKind.BB_MATHEMATICS to 7.5,
        OnboardingSampleSubjectKind.BB_FINANCIAL_MARKET_CURRENT_AFFAIRS to 5.0,
        OnboardingSampleSubjectKind.BB_FINANCIAL_MATHEMATICS to 7.5,
        OnboardingSampleSubjectKind.BB_BANKING_KNOWLEDGE to 15.0,
        OnboardingSampleSubjectKind.BB_INFORMATION_TECHNOLOGY to 22.5,
        OnboardingSampleSubjectKind.BB_SALES_NEGOTIATION to 22.5,
    )

    init {
        require(ObjectiveWeights.size == 8)
        require(kotlin.math.abs(ObjectiveWeights.values.sum() - ObjectiveMaximumPoints) < 0.0001)
    }
}
