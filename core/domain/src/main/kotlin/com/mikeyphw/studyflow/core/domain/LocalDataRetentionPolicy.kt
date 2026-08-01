package com.mikeyphw.studyflow.core.domain

enum class LocalRetentionRuleType {
    PLAN_DATA,
    TRASH,
    PRIVATE_FILES,
    EXPORTED_FILES,
}

data class LocalRetentionRule(
    val type: LocalRetentionRuleType,
)

data class LocalDataRetentionPolicy(
    val rules: List<LocalRetentionRule>,
    val automaticDeletionEnabled: Boolean,
)

object StudyFlowLocalDataRetentionPolicy {
    fun current(): LocalDataRetentionPolicy = LocalDataRetentionPolicy(
        rules = LocalRetentionRuleType.entries.map(::LocalRetentionRule),
        automaticDeletionEnabled = false,
    )
}
