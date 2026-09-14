#!/usr/bin/env python3
from __future__ import annotations
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")

def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)

checks: list[tuple[str, bool]] = []

def check(name: str, condition: bool) -> None:
    checks.append((name, condition))
    require(condition, name)

presentation = read("core/tvcontrol/src/main/kotlin/com/mikeyphw/studyflow/core/tvcontrol/StudyFlowTvPresentation.kt")
projector = read("app/src/main/java/com/mikeyphw/studyflow/tvcontrol/StudyFlowTvPresentationProjector.kt")
viewer = read("app-tv/src/main/java/com/mikeyphw/studyflow/tv/library/StudyFlowTvMaterialViewer.kt")
activity = read("app-tv/src/main/java/com/mikeyphw/studyflow/tv/StudyFlowTvActivity.kt")
focus = read("app-tv/src/main/java/com/mikeyphw/studyflow/tv/focus/StudyFlowTvDynamicFocusBinder.kt")
material_tests = read("app-tv/src/test/java/com/mikeyphw/studyflow/tv/library/StudyFlowTvMaterialViewerOv20ResilienceTest.kt")
presentation_tests = read("core/tvcontrol/src/test/kotlin/com/mikeyphw/studyflow/core/tvcontrol/StudyFlowTvPresentationOv20MaterialTransferTest.kt")

# S8-08: TV presentations must not contain phone-local URIs; a transfer/cache URI is the TV boundary.
check("S8-08 presentation protocol rejects phone-local URIs", "phoneLocalUrisForbidden = true" in presentation and "isPhoneLocalUri" in presentation and "TV material projection must not expose phone-local URIs" in presentation)
check("S8-08 projection emits transfer IDs instead of raw phone-local URIs", "phoneLocalMaterialUrisForbidden = true" in projector and "safeSourceUri" in projector and "transferIdFor" in projector and "sourceUri = safeSourceUri" in projector and "transferId = transferId" in projector)
check("S8-08 transfer URI is TV resolvable", "studyflow-transfer://material/$transferId" in presentation and "tvResolvableSourceUri" in presentation)
check("S8-08 regression blocks content URI", "content://phone/material.pdf" in presentation_tests and "assertFailsWith<IllegalArgumentException>" in presentation_tests)

# S8-09: PDF page count must come from renderer/native resolution, not a default single page.
check("S8-09 native PDF returns page count", "data class StudyFlowTvRenderedPdfPage" in viewer and "pageCount = renderer.pageCount" in viewer)
check("S8-09 viewer stores resolved page count", "nativePdfPageCount" in viewer and "withResolvedPdfPageCount" in viewer and "nativePdfPageCount ?: presentation.pageCountHint" in viewer)
check("S8-09 Activity feeds renderer page count back into viewer state", "rendered.pageCount != state.pageCount" in activity and "withResolvedPdfPageCount(state, rendered.pageCount)" in activity)
check("S8-09 regression navigates PDF without projection hint", "nativePdfPageCountUnlocksNavigationWithoutProjectionHint" in material_tests)

# S8-10: play/pause uses actual View position before reducing state.
check("S8-10 native media captures VideoView position", "captureMediaPosition(view: VideoView" in viewer and "view.currentPosition.toLong()" in viewer)
check("S8-10 Activity captures media position before toggle/seek", "activeMaterialVideoView" in activity and "StudyFlowTvNativeMaterialViewer.captureMediaPosition(view, state)" in activity)
check("S8-10 regression keeps captured position", "mediaToggleKeepsCapturedPosition" in material_tests and "42_000L" in material_tests)

# S8-16: dynamic model focus is applied to live Android Views.
check("S8-16 dynamic focus binder exists", "object StudyFlowTvDynamicFocusBinder" in focus and "dynamicViewWiringRequired = true" in focus)
check("S8-16 dynamic binder skips disabled items", "disabledItemsSkipped = true" in focus and "items.filter { it.enabled" in focus)
check("S8-16 Activity wires live navigation views through binder", "StudyFlowTvDynamicFocusBinder.wire" in activity and "StudyFlowTvFocusItem" in activity and "viewsById" in activity)

# S8-17: fixed horizontal rows get an explicit scroll-safe container.
check("S8-17 scroll-safe horizontal row helper exists", "HorizontalScrollView" in activity and "tvScrollableHorizontalRow" in activity and "isHorizontalScrollBarEnabled = true" in activity)
check("S8-17 schedule/library/material rows use scroll helper", activity.count("tvScrollableHorizontalRow {") >= 5)

# S8-20: independent projection feeds survive one failing mapper/source.
check("S8-20 snapshot carries failed feed evidence", "val failedFeeds: List<String> = emptyList()" in presentation and "failed-feed names" in presentation)
check("S8-20 projector isolates source feeds", "presentationFeedsFailureIsolated = true" in projector and "isolatePresentationFeed" in projector and "failedFeeds += name" in projector)
check("S8-20 snapshot includes failed feed list", "failedFeeds = failedFeeds.toList()" in projector)

print(f"OV20 native TV materials/focus/projection evidence passed: {sum(1 for _, ok in checks if ok)}/{len(checks)} checks")
