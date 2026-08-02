#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def require(path: str, needle: str) -> None:
    text = (ROOT / path).read_text(encoding="utf-8")
    if needle not in text:
        raise SystemExit(f"{path}: missing {needle!r}")


def forbid(path: str, needle: str) -> None:
    text = (ROOT / path).read_text(encoding="utf-8")
    if needle in text:
        raise SystemExit(f"{path}: forbidden {needle!r}")


require(
    "core/diagnostics/src/main/kotlin/com/mikeyphw/studyflow/core/diagnostics/DiagnosticTimeline.kt",
    "object DiagnosticTimelineReader",
)
require(
    "core/diagnostics/src/main/kotlin/com/mikeyphw/studyflow/core/diagnostics/DebugWorkbenchShell.kt",
    "object DebugWorkbenchShellPolicy",
)
require(
    "feature/settings/src/main/java/com/mikeyphw/studyflow/feature/settings/DebugWorkbenchScreen.kt",
    "fun DebugWorkbenchScreen(",
)
require(
    "feature/settings/src/main/java/com/mikeyphw/studyflow/feature/settings/DebugWorkbenchScreen.kt",
    "onExportSupportBundle",
)
require(
    "app/src/main/java/com/mikeyphw/studyflow/StudyFlowDebugWorkbenchActions.kt",
    "File(context.cacheDir, \"support-bundles\")",
)
require(
    "app/src/main/java/com/mikeyphw/studyflow/StudyFlowDebugWorkbenchActions.kt",
    "withContext(ioContext)",
)
require(
    "feature/settings/src/main/java/com/mikeyphw/studyflow/feature/settings/DebugWorkbenchScreen.kt",
    "onOpenRuntimeSmokeWorkbench",
)
require(
    "app/src/main/res/xml/file_provider_paths.xml",
    'path="support-bundles/"',
)
require(
    "core/navigation/src/main/kotlin/com/mikeyphw/studyflow/core/navigation/StudyFlowRoutes.kt",
    "data object DebugWorkbenchRoute",
)
forbid(
    "feature/settings/src/main/java/com/mikeyphw/studyflow/feature/settings/DebugWorkbenchScreen.kt",
    "onClick = {}",
)
forbid(
    "feature/settings/src/main/java/com/mikeyphw/studyflow/feature/settings/DebugWorkbenchScreen.kt",
    "Icons.",
)

require(
    "feature/settings/build.gradle.kts",
    'implementation(project(":core:diagnostics"))',
)
require(
    "core/diagnostics/src/main/kotlin/com/mikeyphw/studyflow/core/diagnostics/DiagnosticTimeline.kt",
    "DiagnosticRedactor.redactDetails",
)
require(
    "core/diagnostics/src/main/kotlin/com/mikeyphw/studyflow/core/diagnostics/DiagnosticTimeline.kt",
    "MaxDepth = 8",
)
print("DEBUG_WORKBENCH_D2_VERIFY: PASSED")
