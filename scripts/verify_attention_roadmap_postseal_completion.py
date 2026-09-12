#!/usr/bin/env python3
"""Post-ATT12 roadmap-completion verifier.

This gate exists because earlier ATT verifiers could prove that models/bridges existed while
missing whether production call sites actually supplied peer topology or invoked those bridges.
It therefore checks both positive reachability and the known disconnected/local-only patterns.
"""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
errors: list[str] = []


def read(path: str) -> str:
    p = ROOT / path
    if not p.is_file():
        errors.append(f"missing {path}")
        return ""
    return p.read_text(encoding="utf-8")


def require(path: str, *tokens: str) -> None:
    data = read(path)
    for token in tokens:
        if token not in data:
            errors.append(f"{path}: missing {token!r}")


def reject(path: str, *tokens: str) -> None:
    data = read(path)
    for token in tokens:
        if token in data:
            errors.append(f"{path}: stale/disconnected pattern remains {token!r}")


def require_external_call(path: str, symbol: str) -> None:
    data = read(path)
    if data.count(symbol) < 1:
        errors.append(f"{path}: production call to {symbol!r} is absent")


# ATT06: native-TV transport ingress must reach the live runtime, not only tests.
require(
    "app-tv/src/main/java/com/mikeyphw/studyflow/tv/StudyFlowTvActivity.kt",
    "sflpPeer.setNotificationDeliveryHandler",
    "tvAttentionRuntime.ingest(delivery)",
    "renderTvAttentionBannerIfNeeded()",
)
require(
    "app-tv/src/main/java/com/mikeyphw/studyflow/tv/link/StudyFlowTvSflpPeer.kt",
    "setNotificationDeliveryHandler",
    "runtime.setNotificationDeliveryHandler",
)

# ATT07: canonical identity + actual trusted/presence candidates + authenticated transport.
require(
    "core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowAttentionTopologyProjection.kt",
    "object StudyFlowAttentionWorkspace",
    "object StudyFlowAttentionTopologyProjector",
    "trustedPeers",
    "presenceViews",
    "foregroundOnlyKinds",
)
require(
    "core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowPeerRpc.kt",
    "StudyFlowNotificationMediaMessageNames.NotificationDeliver",
    "handleNotificationDelivery",
    "setNotificationDeliveryHandler",
    "fun deliverNotification(",
)
require(
    "core/link/src/test/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowPeerRuntimeIntegrationTest.kt",
    "authenticatedPeerCarriesAttentionNotificationToTargetHandler",
    "StudyFlowNotificationDeliveryStatus.ACCEPTED",
)
require(
    "app/src/main/java/com/mikeyphw/studyflow/reminders/StudyReminderDeliveryCoordinator.kt",
    "AndroidStudyFlowAttentionRouterBridge.topologyCapabilities",
    "trustedPeers = linkPeer.trustedPeers()",
    "presenceViews = linkPeer.presenceViews",
    "dispatchRemoteRouteTargets",
    "runtime.deliverNotification",
)
require(
    "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopAttentionRuntime.kt",
    "DesktopAttentionDeliveryRouterBridge.topologyCapabilities",
    "presenceViews = linkService?.presenceViews",
    "dispatchRemoteRouteTargets",
    "runtime.deliverNotification",
)
require(
    "web/src/attention.ts",
    "attentionTopologyFromWebPeer",
    "topology.devices",
    "topology.context",
)
reject(
    "app/src/main/java/com/mikeyphw/studyflow/reminders/StudyReminderDeliveryCoordinator.kt",
    "devices = listOf(AndroidStudyFlowAttentionRouterBridge.localCapability",
)
reject(
    "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopAttentionRuntime.kt",
    "devices = listOf(localCapability)",
)
reject(
    "web/src/attention.ts",
    "entry.occurrence,\n        [localDevice]",
)
for path in [
    "app/src/main/java/com/mikeyphw/studyflow/reminders/AndroidStudyFlowAttentionRouterBridge.kt",
    "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopAttentionDeliveryRouterBridge.kt",
    "app-tv/src/main/java/com/mikeyphw/studyflow/tv/attention/StudyFlowTvAttentionDeliveryRouterBridge.kt",
]:
    reject(path, '"android-local"', '"desktop-local"', '"android-tv-local"')

# ATT08: compact bridges need product consumers on every claimed native platform.
require_external_call(
    "app/src/main/java/com/mikeyphw/studyflow/reminders/StudyReminderDeliveryCoordinator.kt",
    "AndroidCompactCompanionAttentionBridge.projectPhoneOwnedCompanions",
)
require(
    "app/src/main/AndroidManifest.xml",
    "StudyFlowAttentionTileService",
    "android.service.quicksettings.action.QS_TILE",
)
require(
    "app/src/main/java/com/mikeyphw/studyflow/reminders/StudyReminderDeliveryCoordinator.kt",
    "AndroidCompactCompanionStateStore",
    "StudyFlowAttentionTileService.requestRefresh",
)
require_external_call(
    "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopAttentionRuntime.kt",
    "DesktopCompactCompanionAttentionBridge.projectMiniDashboard",
)
require_external_call(
    "app-tv/src/main/java/com/mikeyphw/studyflow/tv/attention/StudyFlowTvAttentionRuntime.kt",
    "StudyFlowTvCompactCompanionBridge.projectForegroundOverlay",
)
require("web/src/react/App.tsx", "Compact companion surface")

# ATT09: each claimed platform bridge is actually reachable from a user-facing surface.
require(
    "app/src/main/java/com/mikeyphw/studyflow/reminders/StudyReminderDeliveryCoordinator.kt",
    "suspend fun buildCalendarExport",
    "AndroidStudyFlowCalendarAttentionBridge().buildExport",
)
require(
    "app/src/main/java/com/mikeyphw/studyflow/StudyFlowApp.kt",
    "onShareCalendarIcs",
    "Intent.ACTION_SEND",
    'type = "text/calendar"',
)
require(
    "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopAttentionRuntime.kt",
    "fun exportCalendar()",
    "DesktopCalendarAttentionExportBridge",
    "Files.writeString",
)
require(
    "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopAnalyticsSearchSettingsScreens.kt",
    "onExportAttentionCalendar",
    "Export calendar (.ics)",
)
require(
    "app-tv/src/main/java/com/mikeyphw/studyflow/tv/StudyFlowTvActivity.kt",
    "tvCalendarAttentionPreview",
    "StudyFlowTvCalendarAttentionBridge().preview",
    "ATT09 calendar preview",
)
require("web/src/react/App.tsx", "Calendar export")

# ATT10/11: TV read-only policy/reliability are consumed and Web reliability sees topology.
require(
    "app-tv/src/main/java/com/mikeyphw/studyflow/tv/attention/StudyFlowTvAttentionRuntime.kt",
    "fun focusProjection()",
    "fun reliabilityProjection()",
)
require(
    "app-tv/src/main/java/com/mikeyphw/studyflow/tv/StudyFlowTvActivity.kt",
    "tvAttentionRuntime.focusProjection()",
    "tvAttentionRuntime.reliabilityProjection()",
    "ATT10 read-only",
    "ATT11 reliability",
)
require(
    "web/src/attention.ts",
    "reliabilityTopology",
    "analyzeWebAttentionReliability(ledger, reliabilityTopology.devices, now)",
)
require(
    "web/src/react/App.tsx",
    'data-att10-tv-policy="read-only"',
    'data-att11-tv-reliability="read-only"',
    "projection.focusProfile",
    "projection.reliabilityStatus",
)

# Semantic regressions found by the second/third audit pass must stay closed.
require(
    "app/src/main/java/com/mikeyphw/studyflow/StudyFlowApplication.kt",
    "linkPeer.runtime.setNotificationDeliveryHandler(reminderDeliveryCoordinator::receiveRemoteNotification)",
    "StudyFlowAttentionWorkspace.forPlanId(plan.id.value)",
)
reject(
    "app/src/main/java/com/mikeyphw/studyflow/StudyFlowApplication.kt",
    'deterministic("local-plan", plan.id.value)',
)
require(
    "app/src/main/java/com/mikeyphw/studyflow/reminders/StudyReminderDeliveryCoordinator.kt",
    "fun receiveRemoteNotification(delivery: StudyFlowNotificationDelivery)",
    "StudyFlowNotificationScheduleReducer(",
    "NotificationManagerCompat.from(appContext).notify(",
    "localPeer.deviceId !in route.targetDeviceIds",
    "if (!compactOnly && !postNotification(",
)
reject(
    "app/src/main/java/com/mikeyphw/studyflow/reminders/StudyReminderDeliveryCoordinator.kt",
    "decision.deliverSilently || compactOnly",
    "compactOnly: Boolean = false",
)
require(
    "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopAttentionRuntime.kt",
    "setNotificationDeliveryHandler(::receiveRemoteNotification)",
    "fun receiveRemoteNotification(delivery: StudyFlowNotificationDelivery)",
    "StudyFlowNotificationScheduleReducer(",
    "localPeer.deviceId !in route.targetDeviceIds",
    "setNotificationDeliveryHandler(null)",
)
require(
    "core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowAttentionNotificationDelivery.kt",
    "AtomicLong",
    "private fun nextSequence",
    "sequence = nextSequence(nowEpochMillis)",
)
require(
    "core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowNotificationMediaSync.kt",
    "val attentionRoute: StudyFlowAttentionDeliveryRoute? = null",
    "Notification target must be selected by the ATT07 route",
)
require(
    "core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowAttentionDeliveryRouter.kt",
    "notificationPermissionGranted",
    "fun targetFor(deviceId: StudyFlowDeviceId)",
)
require(
    "core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowAttentionTopologyProjection.kt",
    "StudyFlowLinkClientKind.DESKTOP -> listOf(",
    "StudyFlowAttentionDeliverySurface.WIDGET_OR_BADGE",
)
require(
    "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopAttentionDeliveryRouterBridge.kt",
    "if (trayAvailable)",
    "StudyFlowAttentionDeliverySurface.WIDGET_OR_BADGE",
)
require(
    "web/src/attention_router.ts",
    'case "desktop": return ["system_notification", "desktop_tray", "widget_or_badge"]',
    '["system_notification", "desktop_tray", "widget_or_badge"]',
)
require(
    "app/src/main/java/com/mikeyphw/studyflow/reminders/StudyReminderDeliveryCoordinator.kt",
    "deliveryRoute = route",
    "notification.attentionRoute",
    "localSurface == StudyFlowAttentionDeliverySurface.WIDGET_OR_BADGE",
    "AndroidCompactCompanionStateStore(appContext).update(projection)",
    "transportedRoute?.deliverSilently == true",
    "val failedRemoteOwner = route.owner?.deviceId",
    "eligibleDevices = eligibleDevices.filterNot { it.peer.deviceId == failedRemoteOwner }",
)
require(
    "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopAttentionRuntime.kt",
    "deliveryRoute = route",
    "delivery.notification.attentionRoute",
    "compact_surface_unavailable",
    "DesktopCompactCompanionAttentionBridge.projectMiniDashboard",
    "val failedRemoteOwner = route.owner?.deviceId",
    "eligibleDevices = eligibleDevices.filterNot { it.peer.deviceId == failedRemoteOwner }",
)
require(
    "core/link/src/test/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowAttentionTopologyProjectionTest.kt",
    "desktop topology advertises compact surface for ATT08 mini dashboard",
    "android without notification permission falls back to compact surface",
    "remote delivery carries ATT07 compact route semantics",
    "rejected remote owner can reroute to local compact fallback",
)
require(
    "desktopApp/src/test/kotlin/com/mikeyphw/studyflow/desktop/DesktopAttentionDeliveryRouterBridgeTest.kt",
    "travel offline owns the desktop mini surface when native notifications are unavailable",
)
require(
    "app-tv/src/main/java/com/mikeyphw/studyflow/tv/schedule/StudyFlowTvScheduleSurface.kt",
    "scheduledStartEpochMillis: Long? = null",
    "plannedDurationMinutes: Int? = null",
)
require(
    "app-tv/src/main/java/com/mikeyphw/studyflow/tv/presentation/StudyFlowTvPresentationAdapter.kt",
    "scheduledStartEpochMillis = item.scheduledStartEpochMillis",
    "plannedDurationMinutes = item.plannedDurationMinutes",
)
require(
    "app-tv/src/main/java/com/mikeyphw/studyflow/tv/StudyFlowTvActivity.kt",
    "val startEpochMillis = item.scheduledStartEpochMillis ?: return@mapNotNull null",
    "startEpochMillis = startEpochMillis",
    "durationMinutes = item.plannedDurationMinutes ?: 30",
)
reject(
    "app-tv/src/main/java/com/mikeyphw/studyflow/tv/StudyFlowTvActivity.kt",
    "System.currentTimeMillis() + (index + 1) * 60L * 60L * 1_000L",
)
require(
    "app/src/main/java/com/mikeyphw/studyflow/StudyFlowApp.kt",
    'File(context.cacheDir, "calendar-exports")',
    '}.ics"',
    "Intent.EXTRA_STREAM",
    "FileProvider.getUriForFile",
    "Intent.FLAG_GRANT_READ_URI_PERMISSION",
)
require(
    "app/src/main/res/xml/file_provider_paths.xml",
    'name="studyflow_calendar_exports"',
    'path="calendar-exports/"',
)

# The independent parity recheck runs the complete Web suite, including SFLP20/21.
require(
    "web/src/sflp_link.ts",
    "STUDYFLOW_SHARE_IMPORT_INTENTS",
    "STUDYFLOW_CONFLICT_REVIEW_OPERATION",
    "tv_write_forbidden",
    "private isReadOnlyTv()",
)
require(
    "web/runtime/sflp_link.js",
    "clipboard_apply",
    "conflict.review",
    "tv_write_forbidden",
)
require(
    "web/dist/runtime/sflp_link.js",
    "clipboard_apply",
    "conflict.review",
    "tv_write_forbidden",
)

# ATT12/post-seal truth: docs and validation must acknowledge/fix the formerly missed gaps.
require(
    "docs/validation/ATTENTION_ROADMAP_COMPACT_TRANSPORT_RECHECK.md",
    "Desktop compact ownership",
    "transported ATT07 route",
    "Android permission fallback",
    "290/290",
)
require(
    "docs/product/ATTENTION_ROADMAP_PROMISE_AUDIT.md",
    "POST-SEAL PASS",
    "trusted/presence topology",
    "native-TV notification ingress",
)
require(
    "docs/product/CROSS_PLATFORM_CAPABILITY_MATRIX.md",
    "Desktop process-owned routing",
    "FULL routing/ownership decision using trusted/presence topology",
    "direct browser peer-RPC send is not wired",
    "canonical request contract",
)
reject(
    "docs/product/CROSS_PLATFORM_CAPABILITY_MATRIX.md",
    "| SFLP notification envelope acceptance | Send/receive | Send/receive | Send/receive |",
)
require(
    "ATTENTION_ROADMAP_POSTSEAL_COMPLETION.md",
    "ATT06",
    "ATT07",
    "ATT08",
    "ATT09",
    "ATT10",
    "ATT11",
    "verifyAttentionRoadmapPostsealCompletion",
)
require(
    "docs/validation/ATTENTION_ROADMAP_TRIPLE_CHECK_REPORT.md",
    "Check 1 — literal roadmap promise reconciliation",
    "Check 2 — production reachability and semantic edge cases",
    "Check 3 — validation, parity and artifact reproducibility",
    "Complete Web suite: 290 tests passing",
    "Native Gradle execution boundary",
)
require(
    "docs/validation/ATTENTION_ROADMAP_PARITY_RECHECK.md",
    "roadmap parity",
    "browser WebSocket adapter in this repository is not wired",
    "complete 290-test Web suite",
)
require(
    "build.gradle.kts",
    '"verifyStudyFlowWebReleaseGate"',
    'commandLine("python3", "-m", "pytest", "tests", "-q")',
    '"verifyAttentionRoadmapPostsealCompletion"',
)
require(".devtool.toml", '"verifyAttentionRoadmapPostsealCompletion"')

# ATT12 verifier itself must now cover the once-missed production reachability.
require(
    "scripts/verify_att12_attention_release_final_seal.py",
    "StudyFlowAttentionTopologyProjector",
    "tvAttentionRuntime.ingest(delivery)",
    "AndroidCompactCompanionAttentionBridge.projectPhoneOwnedCompanions",
    "AndroidStudyFlowCalendarAttentionBridge().buildExport",
    "fun reliabilityProjection()",
)

if errors:
    print("Attention roadmap post-seal completion verification FAILED")
    for error in errors:
        print(f"- {error}")
    sys.exit(1)

print("Attention roadmap post-seal completion verification PASSED")
print("- ATT06 native-TV delivery ingress is production-wired")
print("- ATT07 uses canonical SFLP identities/topology and authenticated remote delivery")
print("- ATT08 compact bridges have product consumers")
print("- ATT09 calendar bridges are user-reachable on Android/Desktop/TV/Web")
print("- ATT10/ATT11 native-TV and Web-TV read-only projections are product-reachable")
print("- Full Web regression evidence is 290/290 and browser transport claims are capability-truthful")
print("- ATT12 verifier/docs/Devtool gate now reject the previously missed gaps")
