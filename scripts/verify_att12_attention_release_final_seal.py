#!/usr/bin/env python3
"""ATT12 final roadmap reconciliation verifier.

This verifier deliberately checks production wiring in addition to historical ATT source-contract markers.
ATT12 exists because a route/model can be correct while an actual platform call site remains unreachable.
"""
from __future__ import annotations

import re
import tomllib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def text(path: str) -> str:
    file = ROOT / path
    assert file.is_file(), f"Missing required ATT12 file: {path}"
    return file.read_text(encoding="utf-8")


def require(path: str, *tokens: str) -> str:
    data = text(path)
    for token in tokens:
        assert token in data, f"{path}: missing {token!r}"
    return data


def fail(message: str) -> None:
    raise AssertionError(message)


def reject(path: str, *tokens: str) -> str:
    data = text(path)
    for token in tokens:
        assert token not in data, f"{path}: stale/forbidden claim {token!r}"
    return data


# ATT01 — shared ledger/domain + SFLP notification/media foundation.
require(
    "core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowAttention.kt",
    "StudyFlowAttentionOccurrence",
    "StudyFlowAttentionLedger",
    "StudyFlowAttentionDeliveryState",
    "StudyFlowAttentionOccurrenceId",
)
require(
    "core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowNotificationMediaSync.kt",
    "StudyFlowNotificationDelivery",
    "attentionOccurrence",
)
require("ATT01_UNIFIED_ATTENTION_DOMAIN_SFLP_FOUNDATION.md", "durable", "SFLP")

# ATT02 — Android delivery/action runtime and truth model.
require(
    "app/src/main/java/com/mikeyphw/studyflow/reminders/StudyReminderDeliveryCoordinator.kt",
    "postNotification",
    "handleNotificationAction",
    "platformCapability",
    "StudyFlowAttentionLedger",
)
require("ATT02_ANDROID_ATTENTION_SYSTEM_2_0.md", "Android", "AlarmManager", "notification", "actions")

# ATT03 — Android widget projection/actions/recovery.
require(
    "app/src/main/java/com/mikeyphw/studyflow/widget/StudyFlowTodayWidgetProvider.kt",
    "AppWidgetProvider",
)
require("ATT03_ANDROID_WIDGET_SUITE_2_0.md", "Today", "Next", "Reviews", "Session", "Compact")

# ATT04 — Desktop process-owned scheduler/tray truth.
require(
    "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopAttentionRuntime.kt",
    "deliverDueEntries",
    "DesktopAttentionRuntimeSnapshot",
    "trayDashboardEnabled",
    "nativeNotificationsSupported",
)
require("ATT04_DESKTOP_NATIVE_ATTENTION_TRAY.md", "while StudyFlow is running", "tray")

# ATT05 — Web runtime, notification center, SW lifecycle, push foundation, generated parity surface.
require(
    "web/src/attention.ts",
    "StudyFlowWebAttentionRuntime",
    "requestBrowserNotifications",
    "subscribePush",
    "ingestRemoteDelivery",
    "updateBadge",
)
require(
    "web/service-worker.js",
    'addEventListener("push"',
    "notificationclick",
    "notificationclose",
    "studyflow:attention-drain",
)
require("web/src/react/App.tsx", "Notification Center", "Optional Web Push foundation")
for generated in (
    "web/runtime/attention.js",
    "web/runtime/main.js",
    "web/runtime/react/App.js",
    "web/dist/runtime/attention.js",
    "web/dist/runtime/main.js",
    "web/dist/runtime/react/App.js",
):
    assert (ROOT / generated).is_file(), f"ATT05/ATT12 generated Web artifact missing: {generated}"

# ATT06 — native TV and Web TV must be reachable production foreground surfaces, not model-only code.
require(
    "app-tv/src/main/java/com/mikeyphw/studyflow/tv/attention/StudyFlowTvAttentionSurface.kt",
    "maximumActions = 3",
    "MOVE_TO_PHONE",
    "SNOOZE",
    "DISMISS",
)
require(
    "app-tv/src/main/java/com/mikeyphw/studyflow/tv/StudyFlowTvActivity.kt",
    "banner.actions.forEach",
    "handleTvAttentionAction",
    "action.tvAttentionLabel()",
    "result.handledOnTv",
)
require(
    "web/src/main.tsx",
    "new StudyFlowWebAttentionRuntime",
    "new StudyFlowWebTvAttentionRuntime",
    "installStudyFlowWebTvAttentionBridge",
)
require(
    "web/src/react/App.tsx",
    "WebTvAttentionBanner",
    'data-att12-tv-attention="foreground-only"',
)
require(
    "web/src/attention.ts",
    'this.peer.identity.clientKind === "web_tv"',
    '"foreground_banner" as const',
    'this.peer.identity.clientKind !== "web_tv"',
)
require("web/src/tv_attention.ts", "foreground-only", "designatedNotifierFallback")
require("ATT06_TV_ATTENTION_EXPERIENCE.md", "foreground", "D-pad")

# ATT07 — shared router PLUS delivery-edge enforcement of explicit owner/mirror targets.
require(
    "core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowAttentionDeliveryRouter.kt",
    "StudyFlowAttentionDeliveryRouter",
    "targetDeviceIds",
    "StudyFlowAttentionHandoffRequest",
    "mirrors",
)
require(
    "app/src/main/java/com/mikeyphw/studyflow/reminders/AndroidStudyFlowAttentionRouterBridge.kt",
    "fun isLocalTarget",
    "route.deliverable && localDeviceId in route.targetDeviceIds",
)
require(
    "app/src/main/java/com/mikeyphw/studyflow/reminders/StudyReminderDeliveryCoordinator.kt",
    "AndroidStudyFlowAttentionRouterBridge.isLocalTarget(route, localPeer.deviceId)",
)
require(
    "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopAttentionDeliveryRouterBridge.kt",
    "fun isLocalTarget",
    "route.deliverable && localDeviceId in route.targetDeviceIds",
)
require(
    "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopAttentionRuntime.kt",
    "DesktopAttentionDeliveryRouterBridge.isLocalTarget(route, localPeer.deviceId)",
)
require(
    "web/src/attention_router.ts",
    "studyFlowAttentionRouteTargetsDevice",
    "route.targetDeviceIds.includes(deviceId)",
)
require(
    "web/src/attention.ts",
    "studyFlowAttentionRouteTargetsDevice(route, this.peer.identity.deviceId)",
    "explicitTargets.includes(this.peer.identity.deviceId)",
)
require(
    "core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowAttentionTopologyProjection.kt",
    "StudyFlowAttentionWorkspace",
    "StudyFlowAttentionTopologyProjector",
    "trustedPeers",
    "presenceViews",
)
require(
    "core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowPeerRpc.kt",
    "StudyFlowNotificationMediaMessageNames.NotificationDeliver",
    "notificationDeliveryHandler",
    "fun deliverNotification",
)
require(
    "app-tv/src/main/java/com/mikeyphw/studyflow/tv/StudyFlowTvActivity.kt",
    "setNotificationDeliveryHandler",
    "tvAttentionRuntime.ingest(delivery)",
)

# ATT08 — compact surfaces remain parent-owned, privacy-safe projections.
require(
    "core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowCompactCompanionAttention.kt",
    "StudyFlowCompactCompanionProjector",
    "maxActions",
    "parentDeviceId",
)
require("ATT08_COMPACT_COMPANION_ATTENTION.md", "phone-owned", "Sensitive-text hiding", "mirrors attached to a capable parent device")
require("web/src/react/App.tsx", "Compact companion surface")
require("app/src/main/java/com/mikeyphw/studyflow/reminders/StudyReminderDeliveryCoordinator.kt", "AndroidCompactCompanionAttentionBridge.projectPhoneOwnedCompanions")
require("desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopAttentionRuntime.kt", "DesktopCompactCompanionAttentionBridge.projectMiniDashboard")
require("app-tv/src/main/java/com/mikeyphw/studyflow/tv/attention/StudyFlowTvAttentionRuntime.kt", "StudyFlowTvCompactCompanionBridge.projectForegroundOverlay")
require("app/src/main/AndroidManifest.xml", "StudyFlowAttentionTileService", "android.service.quicksettings.action.QS_TILE")

# ATT09 — portable calendar attention export, privacy and TV preview-only truth.
require(
    "core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowCalendarAttention.kt",
    "VALARM",
    "VEVENT",
    "StudyFlowCalendar",
)
require("ATT09_CALENDAR_SCHEDULE_EXPORT_ATTENTION.md", "user-initiated", "busy-only")
require("web/src/react/App.tsx", "Calendar export")
require("app/src/main/java/com/mikeyphw/studyflow/reminders/StudyReminderDeliveryCoordinator.kt", "AndroidStudyFlowCalendarAttentionBridge().buildExport")
require("app/src/main/java/com/mikeyphw/studyflow/StudyFlowApp.kt", "buildCalendarExport", "text/calendar")
require("desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopAttentionRuntime.kt", "DesktopCalendarAttentionExportBridge", "Files.writeString")
require("app-tv/src/main/java/com/mikeyphw/studyflow/tv/StudyFlowTvActivity.kt", "StudyFlowTvCalendarAttentionBridge().preview", "ATT09 calendar preview")

# ATT10 — focus-policy profiles/outcomes + canonical policy audit.
require(
    "core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowAttentionFocusPolicy.kt",
    "StudyFlowAttentionQuietWindow",
    "StudyFlowAttentionFocusProfile",
    "DEFER_UNTIL_QUIET_END",
    "ESCALATE_MIRROR",
    "REQUIRE_USER_ACTION",
)
require("ATT10_QUIET_HOURS_FOCUS_PROFILES_ESCALATION_POLICY.md", "timezone", "ATT01", "ATT07")
require("app-tv/src/main/java/com/mikeyphw/studyflow/tv/attention/StudyFlowTvAttentionRuntime.kt", "fun focusProjection()", "StudyFlowTvAttentionDeliveryRouterBridge.focusProjection")

# ATT11 — deterministic reliability evidence/export on the ATT01 ledger.
require(
    "core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowAttentionReliabilityEvidence.kt",
    "StudyFlowAttentionReliabilityAnalyzer",
    "dismissedCount",
    "snoozedCount",
    "deferredCount",
)
require("ATT11_NOTIFICATION_RELIABILITY_EVIDENCE_AUDIT.md", "JSON", "CSV", "ATT01")
require("app-tv/src/main/java/com/mikeyphw/studyflow/tv/attention/StudyFlowTvAttentionRuntime.kt", "fun reliabilityProjection()", "StudyFlowTvAttentionDeliveryRouterBridge.reliabilityProjection")
require("web/src/attention.ts", "reliabilityTopology", "analyzeWebAttentionReliability(ledger, reliabilityTopology.devices")

# ATT12 docs / reconciliation artifacts.
require("ATT12_ATTENTION_RELEASE_FINAL_SEAL.md", "Reconciliation fixes", "verifyAtt12AttentionReleaseFinalSeal")
require("docs/product/ATTENTION_USER_GUIDE.md", "Android TV and Web TV", "Cross-device ownership", "Reliability and audit evidence")
audit = require("docs/product/ATTENTION_ROADMAP_PROMISE_AUDIT.md", "ATT01", "ATT12", "Gaps found and folded into ATT12")
for item in range(1, 13):
    assert f"ATT{item:02d}" in audit, f"ATT12 promise audit does not cover ATT{item:02d}"
require("docs/validation/ATT12_ATTENTION_FINAL_VALIDATION.md", "verifyAtt12AttentionReleaseFinalSeal", "--task verifyAtt01UnifiedAttentionFoundation")

# Canonical matrix must contain current truth and must not retain known stale/malformed claims.
matrix = require(
    "docs/product/CROSS_PLATFORM_CAPABILITY_MATRIX.md",
    "Native reminder/background delivery",
    "ATT06 TV foreground-attention truth",
    "ATT12 final Attention release-seal truth",
    "The post-seal completion gate verifies production reachability",
)
for stale in (
    "FULL (ATT05 foreground + optional Push foundation)",
    "Web/TV equivalents remain later roadmap work",
    "| Compact companion attention | Android wearable/lock-screen/quick-tile, Desktop mini, Web compact, TV remote overlay |",
):
    assert stale not in matrix, f"Capability matrix retained stale ATT claim: {stale}"

# Devtool primary Android lane uses the terminal Attention seal instead of rerunning focused ATT gates.
# Focused ATT01-ATT12 tasks remain registered in Gradle for diagnosis, while default validation stays deduplicated.
devtool = text(".devtool.toml")
config = tomllib.loads(devtool)
android_target = config["targets"]["studyflow_android"]
assert android_target.get("runner") == "android", "studyflow_android must use the canonical Android runner"
assert config.get("workspace", {}).get("default_target") == "studyflow_android", "workspace default must be Android"
test_lane = android_target.get("tasks", {}).get("test", [])
unit_lane = android_target.get("validation", {}).get("phases", {}).get("unit-test", [])
assert test_lane.count("verifyAttentionRoadmapPostsealCompletion") == 1, "Default Devtool test lane must contain the terminal Attention seal exactly once"
assert unit_lane.count("verifyAttentionRoadmapPostsealCompletion") == 1, "Resumable unit-test lane must contain the terminal Attention seal exactly once"
attention_tasks = [
    "verifyAtt01UnifiedAttentionFoundation",
    "verifyAtt02AndroidAttentionSystem",
    "verifyAtt03AndroidWidgetSuite",
    "verifyAtt04DesktopAttentionRuntime",
    "verifyAtt05WebPwaAttentionRuntime",
    "verifyAtt06TvAttentionExperience",
    "verifyAtt07CrossDeviceDeliveryRouter",
    "verifyAtt08CompactCompanionAttention",
    "verifyAtt09CalendarScheduleExportAttention",
    "verifyAtt10QuietHoursFocusProfilesEscalationPolicy",
    "verifyAtt11NotificationReliabilityEvidenceAudit",
    "verifyAtt12AttentionReleaseFinalSeal",
]
for task in attention_tasks:
    assert task not in test_lane, f"Focused Attention task must not duplicate the default lane: {task}"

# Gradle final seal and every predecessor task must exist.
gradle = text("build.gradle.kts")
for task in attention_tasks:
    assert f'"{task}"' in gradle, f"Gradle task missing from final roadmap: {task}"
for task in (
    "verifyAtt12AttentionReleaseContracts",
    "verifyAtt12WebAttentionReleaseTests",
    "verifyAtt12WebRuntimeParity",
    "verifyAtt12WebReleaseValidation",
    "verifyAtt12WebReleaseIntegrity",
    "verifySflp18Sflp19NotificationMediaContracts",
    "verifyWeb20SharedSemanticGoldenVectors",
):
    assert f'"{task}"' in gradle, f"ATT12 final seal wiring missing {task}"


# ATT12 v2 compile/test closure discovered by the real Termux final seal.
require(
    "app/build.gradle.kts",
    "implementation(libs.kotlinx.serialization.json)",
)
require(
    "app-tv/src/main/java/com/mikeyphw/studyflow/tv/link/StudyFlowTvAttentionLedgerStorage.kt",
    "override fun writeEncodedSnapshot(encoded: String) {",
)
require(
    "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopStudyFlowAttentionLedgerStorage.kt",
    "override fun writeEncodedSnapshot(encoded: String) {",
)
require(
    "core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowAttentionDeliveryRouter.kt",
    "deliverySurfaces: List<StudyFlowAttentionDeliverySurface> = defaultAttentionSurfacesFor(clientKind)",
)
require(
    "core/domain/src/test/kotlin/com/mikeyphw/studyflow/core/domain/StudyReminderPlannerPhase29BHardeningTest.kt",
    "dailySummaryUsesNextLocalMidnightInsteadOfUtcBucket",
    "expectedLocalSummary",
)
require(
    "core/domain/src/test/kotlin/com/mikeyphw/studyflow/core/domain/StudyReminderPlannerBh08LateReconciliationTest.kt",
    "StudyReminderPolicy().plannedStudyLeadMinutes",
)
# ATT12 v3/v4 closure from the authoritative Termux final-seal runs.
require(
    "build.gradle.kts",
    'tasks.register<Exec>("verifyAttentionWebRuntimeParity")',
    'dependsOn("verifyAttentionWebRuntimeParity")',
    'environment("STUDYFLOW_EXTERNAL_RUNTIME_PARITY", "1")',
    'commandLine("python3", "-m", "unittest", "discover", "-s", "tests", "-p", "test_*.py")',
    'tasks.matching { it.name == \"testDebugUnitTest\" }.configureEach {',
)
reject(
    "build.gradle.kts",
    'environment("NODE_OPTIONS", "--max-old-space-size=512")',
)
require(
    "app-tv/src/test/java/com/mikeyphw/studyflow/tv/attention/StudyFlowTvAttentionSurfaceTest.kt",
    'workspaceId = StudyFlowWorkspaceId("workspace-tv-attention")',
)
for removed in ("StudyFlowAttentionSource", "StudyFlowAttentionTrigger", "StudyFlowNotificationKind"):
    assert removed not in text("app-tv/src/test/java/com/mikeyphw/studyflow/tv/attention/StudyFlowTvAttentionSurfaceTest.kt"), (
        f"ATT12 v3 TV test still references retired model token {removed}"
    )
require(
    "core/link/src/test/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowCompactCompanionAttentionTest.kt",
    "StudyFlowCompactCompanionProjector(staleAfterMillis = 5_000L)",
)
for path in (
    "app/src/test/java/com/mikeyphw/studyflow/reminders/AndroidStudyFlowAttentionReliabilityEvidenceContractTest.kt",
    "app/src/test/java/com/mikeyphw/studyflow/widget/StudyFlowWidgetAtt03SourceContractTest.kt",
):
    assert "Files.readString" not in text(path), f"{path}: Android unit test must not use Files.readString"
    require(path, "Files.readAllBytes")
require(
    "desktopApp/build.gradle.kts",
    'configurations.named("testRuntimeClasspath")',
    'exclude(group = "org.jetbrains.skiko")',
    'systemProperty("java.awt.headless", "true")',
)
require(
    "app/src/main/java/com/mikeyphw/studyflow/widget/StudyFlowWidgetModels.kt",
    ").filter { it >= nowEpochMillis + MinimumRefreshDelayMillis }",
)
require(
    "web/tests/test_w14_typescript_migration.py",
    "STUDYFLOW_EXTERNAL_RUNTIME_PARITY",
    "Avoid launching a second tsc",
)

# ATT12 v5 closure from the fourth authoritative Termux final-seal run.
require(
    "app-tv/src/test/java/com/mikeyphw/studyflow/tv/settings/StudyFlowTvSettingsSurfaceTest.kt",
    "StudyFlowTvSettingsSurface.cardCount",
    "StudyFlowTvSettingsSurface.availableActions(state).size",
)
reject(
    "app-tv/src/test/java/com/mikeyphw/studyflow/tv/settings/StudyFlowTvSettingsSurfaceTest.kt",
    "assertEquals(4, cards.size)",
    "assertEquals(8, StudyFlowTvSettingsSurface.focusItems(state).size)",
)
require(
    "desktopApp/src/test/kotlin/com/mikeyphw/studyflow/desktop/StudyFlowDesktopWorkflowScreensTest.kt",
    'assertFalse(settings.deferredDesktopIntegrations.contains("Native file pickers"))',
    "OS-specific rich notification action buttons",
    "Launch-on-login background reminders",
)
require(
    "web/scripts/compile_runtime.mjs",
    "runtime-parity-manifest.json",
    "verifySealedManifest",
    "result.error?.code === \"ENOENT\"",
    "result.signal",
    "sealed manifest fallback",
)
require(
    "web/runtime-parity-manifest.json",
    '"schemaVersion": 1',
    '"typescriptVersion": "5.8.3"',
    '"sources"',
    '"runtime"',
)


# ATT12 v6 closure from the fifth authoritative Termux final-seal run.
require(
    "docs/validation/ATT12_ATTENTION_FINAL_VALIDATION.md",
    "studyflow_att12_attention_release_final_seal_v6.tar.gz",
    "SFLP08/09",
    "120-character Kotlin source contract",
)
require(
    "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopAnalyticsSearchSettingsScreens.kt",
    '"ATT11 analytics are deterministic projections of the ATT01 ledger; " +',
    "StudyFlowAttentionReliabilityExport.toJson(reliability)",
    "StudyFlowAttentionReliabilityExport.toCsv(reliability)",
)
for line_number, line in enumerate(
    text("desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopAnalyticsSearchSettingsScreens.kt").splitlines(),
    start=1,
):
    if len(line) > 120:
        fail(f"ATT12 v6 Desktop Attention source exceeds 120 characters at line {line_number}: {len(line)}")

print("ATT12 Attention release final seal contracts passed")
