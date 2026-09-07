#!/usr/bin/env python3
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def text(p): return (ROOT/p).read_text(encoding='utf-8')
def require(c,m):
    if not c: raise SystemExit('OV14 verify failed: '+m)
phase=text('core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/StudyPhasePlanning.kt')
onboarding=text('core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/PublicExamOnboarding.kt')
wizard=text('core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/StudyOnboardingSamplePlanWizard.kt')
manager=text('core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/ManageStudySchedule.kt')
screen=text('feature/schedule/src/main/java/com/mikeyphw/studyflow/feature/schedule/StudyScheduleScreen.kt')
workspace=text('feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/PlanWorkspaceScreen.kt')
for token in ['StudyPhaseDefinition','StudyPhasePlanCodec','StudyPhasePlanning','BancoDoBrasilStudyPhasePreset','allocationIsStudyFlowSuggested']:
    require(token in phase, f'missing {token}')
for goal in ['questionGoal = 300','questionGoal = 500','questionGoal = 700','questionGoal = 1_000']:
    require(goal in phase, f'BB phase target missing {goal}')
require('phasePlan: StudyPhasePlan? = null' in onboarding, 'onboarding phasePlan is not optional')
require('StudyPhasePlanCodec.encode' in onboarding, 'onboarding strategy does not persist phase plan')
require('profile.copy(phasePlan = BancoDoBrasilStudyPhasePreset.plan())' in wizard, 'BB wizard does not seed phase plan')
require('savePhasePlan' in manager and 'effectiveStrategyForDate' in manager, 'schedule manager phase integration missing')
require('effectiveStrategyForDate' in screen, 'generated week ignores active phase')
require('StudyPhaseEditorDialog' in screen, 'phase editor missing')
require('study_phase_allocation_notice' in screen, 'ratio truthfulness notice missing')
require('StudyPhasePlanCodec.decode' in workspace, 'Plan overview does not expose current phase')
print('OV14 study phase planning source contract: PASS')
