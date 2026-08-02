#!/usr/bin/env python3
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def require(path: str, needle: str) -> None:
    if needle not in read(path):
        raise SystemExit(f"{path}: missing {needle!r}")


def forbid(path: str, needle: str) -> None:
    if needle in read(path):
        raise SystemExit(f"{path}: forbidden {needle!r}")

model = "core/diagnostics/src/main/kotlin/com/mikeyphw/studyflow/core/diagnostics/DebugProbes.kt"
registry = "app/src/main/java/com/mikeyphw/studyflow/StudyFlowDebugProbeRegistry.kt"
panel = "feature/settings/src/main/java/com/mikeyphw/studyflow/feature/settings/DebugProbePanel.kt"
app = "app/src/main/java/com/mikeyphw/studyflow/StudyFlowApp.kt"

for probe in (
    "RECORDER_HEALTH", "NOTIFICATION_HEALTH", "WIDGET_HEALTH", "PROCESS_EXIT_HISTORY",
    "BACKUP_CONFIGURATION", "FILE_PROVIDER_CONFIGURATION", "REMINDER_RECONCILIATION",
    "WIDGET_REFRESH", "BACKUP_DRY_RUN", "SEARCH_QUERY", "RECOMMENDATION_QUERY",
    "SYNC_FOUNDATION",
):
    require(model, probe)
require(model, "val safeSuiteIds")
require(model, "descriptor.safety == DebugProbeSafety.READ_ONLY")
require(registry, "getHistoricalProcessExitReasons")
require(registry, "areNotificationsEnabled")
require(registry, "getAppWidgetIds")
require(registry, "resolveContentProvider")
require(registry, "databaseRowsChanged == 0")
require(registry, "withTimeout")
forbid(registry, ".description")
forbid(registry, "traceInputStream")
require(panel, "fun DebugProbePanel(")
forbid(panel, ".name)")
forbid(panel, "onClick = {}")
require(app, "DebugProbeCatalog.safeSuiteIds.forEach")
require(app, "probeContent = {")
require(app, "composable<RuntimeSmokeRoute>")
require("feature/settings/build.gradle.kts", 'implementation(project(":core:diagnostics"))')

for folder in ("values", "values-pt-rBR"):
    ET.parse(ROOT / f"feature/settings/src/main/res/{folder}/strings.xml")

print("DEBUG_WORKBENCH_D3_VERIFY: PASSED")
