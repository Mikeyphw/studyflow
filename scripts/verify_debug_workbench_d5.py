#!/usr/bin/env python3
from pathlib import Path
import xml.etree.ElementTree as ET
root = Path(__file__).resolve().parents[1]
required = {
    'core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/RoomStudyDatabaseDiagnostics.kt': [
        'PRAGMA quick_check', 'PRAGMA foreign_key_check', 'StudyFlowMigrations.All',
        'catch (cancelled: CancellationException)', 'throw cancelled', 'userTableNames()'],
    'app/src/main/java/com/mikeyphw/studyflow/StudyFlowDatabasePortabilityLab.kt': [
        'BackupRestoreDrillSeal.runManagedDrills(', 'DatabaseHealthOperation',
        'PortabilityDrillOperation', 'catch (cancelled: CancellationException)'],
    'feature/settings/src/main/java/com/mikeyphw/studyflow/feature/settings/DatabasePortabilityLabPanel.kt': [
        'fun DatabasePortabilityLabPanel(', 'debug_portability_lab_zero_write'],
    'feature/settings/src/main/java/com/mikeyphw/studyflow/feature/settings/DebugLabsPanel.kt': [
        'DebugLabPage.REMINDERS', 'DebugLabPage.DATABASE_PORTABILITY'],
    'app/src/test/java/com/mikeyphw/studyflow/StudyFlowDatabasePortabilityLabTest.kt': [
        'block before operation without default plan', 'cancellation is never converted',
        'fatal errors are never converted', 'event pair'],
    'core/data/src/test/kotlin/com/mikeyphw/studyflow/core/data/StudyFlowDatabaseHealthInspectorContractTest.kt': [
        'declared migration chain reaches the current schema', 'unknown future schema',
        'database inspector preserves cancellation'],
}
for relative, needles in required.items():
    path = root / relative
    assert path.is_file(), relative
    text = path.read_text(encoding='utf-8')
    for needle in needles:
        assert needle in text, f'{relative}: missing {needle}'
models = (root / 'core/diagnostics/src/main/kotlin/com/mikeyphw/studyflow/core/diagnostics/DebugWorkbenchLabs.kt').read_text(encoding='utf-8')
assert 'drills.none(PortabilityDrillDiagnostic::destructive)' in models
assert 'Debug portability drills must never be destructive.' in models
assert 'runCatching {' not in (root / 'core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/RoomStudyDatabaseDiagnostics.kt').read_text(encoding='utf-8')
for relative in ['feature/settings/src/main/res/values/strings.xml','feature/settings/src/main/res/values-pt-rBR/strings.xml']:
    ET.parse(root / relative)
print('StudyFlow Debug Workbench D5 verifier passed')
