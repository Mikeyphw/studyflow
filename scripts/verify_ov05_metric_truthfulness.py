#!/usr/bin/env python3
from pathlib import Path

checks = []
def need(path: str, text: str, label: str):
    data = Path(path).read_text()
    if text not in data:
        checks.append(f"{label}: missing {text!r} in {path}")

helper = 'feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/ProgressMetricEvidence.kt'
ui = 'feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/ProgressWorkspaceScreen.kt'
strings = 'feature/workspace/src/main/res/values/strings.xml'
pt = 'feature/workspace/src/main/res/values-pt-rBR/strings.xml'
tests = 'feature/workspace/src/test/java/com/mikeyphw/studyflow/feature/workspace/ProgressMetricEvidenceTest.kt'
need(helper, 'if (answeredQuestions == 0) return unavailable()', 'zero answers no-data')
need(helper, 'mediumAt = 10, highAt = 50', 'accuracy confidence thresholds')
need(ui, 'scheduleMetric.percent?.let', 'schedule no-data rendering')
need(ui, 'goalMetric.percent?.let', 'goal no-data rendering')
need(ui, 'periodAccuracyMetric.confidence?.let', 'accuracy confidence rendering')
need(ui, 'R.string.content_integrity_score', 'content integrity score rendering')
need(strings, '<string name="content_health_title">Content integrity</string>', 'English Content Integrity naming')
need(pt, '<string name="content_health_title">Integridade do conteúdo</string>', 'Portuguese Content Integrity naming')
need(tests, 'zeroAnswersAreNoDataRatherThanZeroPercent', 'zero/no-data regression')
need(tests, 'thirtyNineAnswersRemainMediumConfidence', '39-answer confidence regression')
if checks:
    raise SystemExit('OV05 metric truthfulness violations:\n' + '\n'.join(f'- {x}' for x in checks))
print('OV05_METRIC_TRUTHFULNESS: PASS')
