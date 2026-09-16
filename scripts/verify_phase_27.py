#!/usr/bin/env python3
from pathlib import Path
import re
import sys
from phase_version import phase_version_at_least

ROOT = Path(__file__).resolve().parents[1]

def read(rel):
    path = ROOT / rel
    if not path.exists():
        raise AssertionError(f"missing required file: {rel}")
    return path.read_text()

def require(text, needle, label):
    if needle not in text:
        raise AssertionError(f"missing {label}: {needle}")

def forbid(text, needle, label):
    if needle in text:
        raise AssertionError(f"forbidden {label}: {needle}")

if not phase_version_at_least(ROOT, 27):
    raise AssertionError("app version must be Phase 27 or later")

root_build = read("build.gradle.kts")
roadmap = read('docs/roadmap/ACTIVE_ROADMAP.md')
require(roadmap, 'single active roadmap', 'active roadmap boundary')

routes = read("core/navigation/src/main/kotlin/com/mikeyphw/studyflow/core/navigation/StudyFlowRoutes.kt")
require(routes, 'data object RuntimeSmokeRoute', 'runtime smoke route')

release_factory = read("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/ReleaseValidationSealFactory.kt")
match = re.search(r'private const val CurrentPhase = (\d+)', release_factory)
if not match or int(match.group(1)) < 27:
    raise AssertionError('release validation seal must move to Phase 27 or later')

release_tests = read("core/domain/src/test/kotlin/com/mikeyphw/studyflow/core/domain/ReleaseValidationSealFactoryTest.kt")
require(release_tests, 'assertEquals(seal.currentPhase, seal.validatedPhase)', 'phase-forward seal test')
forbid(release_tests, 'assertEquals(26, seal.currentPhase)', 'stale Phase 26 seal test')

model = read("core/model/src/main/kotlin/com/mikeyphw/studyflow/core/model/RuntimeSmoke.kt")
for probe in ['REMINDER_RECONCILIATION', 'WIDGET_REFRESH', 'BACKUP_DRY_RUN', 'SEARCH_QUERY', 'RECOMMENDATION_QUERY', 'SYNC_FOUNDATION']:
    require(model, probe, f'runtime smoke probe {probe}')
require(model, 'val destructive: Boolean = false', 'safe-by-default probe flag')
require(model, 'readyForReleaseSeal', 'release seal readiness')

workbench = read("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/RuntimeSmokeWorkbench.kt")
require(workbench, 'RuntimeSmokeProbeKind.values().map', 'all probes included')
require(workbench, 'completedReport', 'completed report helper')
require(workbench, 'markBlocked', 'blocked probe helper')

screen = read("feature/settings/src/main/java/com/mikeyphw/studyflow/feature/settings/RuntimeSmokeWorkbenchScreen.kt")
require(screen, 'RuntimeSmokeWorkbenchScreen', 'settings workbench screen')
require(screen, 'onRunSafeProbeSet', 'run safe probe set action')
require(screen, 'probe.safeForManualRun', 'safe manual gate')

settings = read("feature/settings/src/main/java/com/mikeyphw/studyflow/feature/settings/SettingsScreen.kt")
require(settings, 'syncState: StudySyncState', 'sync state setting parameter')
require(settings, 'onOpenDebugWorkbench', 'settings Debug Workbench callback')
require(settings, 'open_debug_workbench', 'settings Debug Workbench button')
debug_screen = read("feature/settings/src/main/java/com/mikeyphw/studyflow/feature/settings/DebugWorkbenchScreen.kt")
require(debug_screen, 'onOpenRuntimeSmokeWorkbench', 'runtime smoke compatibility action')
require(debug_screen, 'debug_open_runtime_smoke', 'runtime smoke compatibility button')

app = read("app/src/main/java/com/mikeyphw/studyflow/StudyFlowApp.kt")
require(app, 'import com.mikeyphw.studyflow.core.domain.RuntimeSmokeWorkbench', 'runtime smoke workbench import')
require(app, 'import com.mikeyphw.studyflow.core.model.RuntimeSmokeProbeKind', 'runtime smoke kind import')
require(app, 'StudyFlowDebugProbeRegistry(', 'shared behavioral probe registry')
require(app, 'DebugProbeId.WIDGET_REFRESH', 'widget probe mapping')
require(app, 'DebugProbeId.REMINDER_RECONCILIATION', 'reminder probe mapping')
require(app, 'DebugProbeId.BACKUP_DRY_RUN', 'backup probe mapping')
require(app, 'DebugProbeId.SEARCH_QUERY', 'search probe mapping')
require(app, 'DebugProbeId.RECOMMENDATION_QUERY', 'recommendation probe mapping')
require(app, 'DebugProbeId.SYNC_FOUNDATION', 'checkpoint probe mapping')
require(app, 'registry.run(probeId)', 'shared behavioral probe execution')
require(app, 'catch (cancelled: java.util.concurrent.CancellationException)', 'cancellation propagation')
require(app, 'catch (fatal: Error)', 'fatal error propagation')
require(app, 'markBlocked(kind', 'probe failure is blocked, not silently passed')
require(app, 'result?.status == DebugProbeStatus.PASSED', 'only behavioral PASS evidence can pass runtime smoke')

registry = read("app/src/main/java/com/mikeyphw/studyflow/StudyFlowDebugProbeRegistry.kt")
for needle, label in [
    ('reminderDeliveryCoordinator.reconcilePlan(planId)', 'reminder behavioral probe'),
    ('StudyFlowWidgetUpdateCoordinator.refreshAllAndAwait(appContext)', 'awaited widget behavioral probe'),
    ('File.createTempFile("debug-backup-canary-"', 'streamed backup canary'),
    ('manageStudyPlanPortability.exportPlan(', 'backup export path'),
    ('manageStudyPlanPortability.inspectBackup(input)', 'backup integrity path'),
    ('manageStudyPlanPortability.dryRunImport(', 'backup dry-run path'),
    ('dryRun.databaseRowsChanged == 0', 'zero-write dry-run assertion'),
    ('manageStudyDiscovery.search(plan.name)', 'search behavioral canary'),
    ('manageStudyDiscovery.recommendations(planId)', 'recommendation behavioral canary'),
    ('manageStudyCloudSync.inspectLocalChanges()', 'checkpoint behavioral canary'),
]:
    require(registry, needle, label)
forbid(app, 'Open the backup center and use restore dry-run before import writes.', 'fake backup dry-run pass')

tests = read("core/domain/src/test/kotlin/com/mikeyphw/studyflow/core/domain/RuntimeSmokeWorkbenchTest.kt")
require(tests, 'initialReportIncludesEverySafeProbe', 'initial report test')
require(tests, 'completedReportCanSatisfyDeviceSmokeGate', 'completed report test')
require(tests, 'blockedProbePreventsReleaseSeal', 'blocked report test')

en = read("feature/settings/src/main/res/values/strings.xml")
pt = read("feature/settings/src/main/res/values-pt-rBR/strings.xml")
require(en, 'runtime_smoke_title', 'English runtime smoke strings')
require(pt, 'runtime_smoke_title', 'Portuguese runtime smoke strings')

docs = read("docs/quality/PHASE_27_RUNTIME_SMOKE_WORKBENCH.md")
require(docs, 'real backup inspect and dry-run', 'r2 backup promise audit')
require(docs, 'blocked on exceptions', 'r2 blocked-exception promise audit')

print('PHASE_27_CONTRACT: PASSED (runtime smoke workbench r3, phase-forward)')

if __name__ == '__main__':
    sys.exit(0)
