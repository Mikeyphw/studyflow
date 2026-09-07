#!/usr/bin/env python3
from pathlib import Path
root = Path(__file__).resolve().parents[1]
resolver = (root / 'core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/PublicExamPreEdital.kt').read_text()
onboarding = (root / 'core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/PublicExamOnboarding.kt').read_text()
today = (root / 'feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/TodayWorkspaceScreen.kt').read_text()
progress = (root / 'feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/ProgressWorkspaceScreen.kt').read_text()
plan = (root / 'feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/PlanWorkspaceScreen.kt').read_text()
dashboard = (root / 'core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/PublicExamDashboard.kt').read_text()
runbook = (root / 'core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/StudyExamRunbook.kt').read_text()
provisioner = (root / 'app/src/main/java/com/mikeyphw/studyflow/SamplePlanProvisioner.kt').read_text()
checks = {
    'cycle strategy guard': 'readySchedule.strategy?.type == StudyStrategyType.STUDY_CYCLE' in today,
    'official resolver': 'fun daysUntilOfficial(' in resolver and 'fun officialCountdownDate(' in resolver,
    'expected suppressed': 'PublicExamDateSource.EXPECTED_TARGET, PublicExamDateSource.NONE -> null' in resolver,
    'dashboard canonical': 'PublicExamDateResolver.daysUntilOfficial(plan, target' in dashboard,
    'priority canonical': 'PublicExamDateResolver.daysUntilOfficial(' in plan,
    'progress target': 'contentManager.observeStudyTarget(plan.id)' in progress and 'target = target' in progress,
    'runbook canonical': 'PublicExamDateResolver.officialCountdownDate(plan, target)' in runbook,
    'truthful coverage': 'coverageTopicCount?.takeIf { it > 0 }' in onboarding and 'topicCount.toDouble()' in onboarding,
    'real topic count': 'coverageTopicCount = topics.count { !it.archived }' in provisioner,
    'no fake coverage target': '100.0, startsOnIso, idFactory, endsOnIso = targetDate' not in onboarding,
}
failed = [name for name, ok in checks.items() if not ok]
if failed:
    raise SystemExit('OV18 release seal failed: ' + ', '.join(failed))
print('OV18 public-exam release seal: PASS')
