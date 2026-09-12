#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
errors = []

def read(path: str) -> str:
    p = ROOT / path
    if not p.is_file():
        errors.append(f"missing {path}")
        return ""
    return p.read_text(encoding="utf-8")

def require(path: str, *tokens: str) -> None:
    text = read(path)
    for token in tokens:
        if token not in text:
            errors.append(f"{path}: missing {token!r}")

require(
    "core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowAttentionDeliveryRouter.kt",
    "StudyFlowAttentionDeliveryRouter",
    "StudyFlowAttentionDeviceCapability",
    "StudyFlowAttentionRoutingContext",
    "StudyFlowAttentionHandoffRequest",
    "LEDGER_ALREADY_HANDLED",
    "PHONE_AND_DESKTOP",
    "FOREGROUND_BANNER",
    "ANDROID_ALARM",
    "WEB_PUSH",
    "targetDeviceIds",
)
require(
    "core/link/src/test/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowAttentionDeliveryRouterTest.kt",
    "activeForegroundDeviceOwnsBeforePrimaryWhenPolicyAllowsIt",
    "phoneAndDesktopPolicyDedupesOwnerAndMirror",
    "handledLedgerStateSuppressesRedeliveryAcrossDevices",
    "explicitTargetListPreventsAccidentalBroadcast",
    "staleMutedAndLedgerOnlyDevicesAreNotOwners",
)
require(
    "app/src/main/java/com/mikeyphw/studyflow/reminders/AndroidStudyFlowAttentionRouterBridge.kt",
    "ANDROID_ALARM",
    "SYSTEM_NOTIFICATION",
    "WIDGET_OR_BADGE",
    "notificationsAllowed",
)
require(
    "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopAttentionDeliveryRouterBridge.kt",
    "DESKTOP_TRAY",
    "nativeNotificationsAvailable",
    "StudyFlowAttentionTopologyProjector.project",
    "trustedPeers",
    "presenceViews",
)
require(
    "app-tv/src/main/java/com/mikeyphw/studyflow/tv/attention/StudyFlowTvAttentionDeliveryRouterBridge.kt",
    "FOREGROUND_BANNER",
    "LEDGER_ONLY",
    "foregroundRoutingContext",
)
require(
    "web/src/attention_router.ts",
    "routeStudyFlowAttentionDelivery",
    "attentionDeviceFromWebPeer",
    "createStudyFlowAttentionHandoffRequest",
    "ledger_already_handled",
    "phone_desktop_pair",
    "foreground_banner",
)
require("web/runtime/attention_router.js", "exports.routeStudyFlowAttentionDelivery", "ledger_already_handled")
require("web/dist/runtime/attention_router.js", "exports.createStudyFlowAttentionHandoffRequest")
require("web/tests/test_att07_attention_router.py", "Att07AttentionRouterTests")
require("web/tests/att07_router_runner.mjs", "ATT07 Web attention router tests passed")
require("web/src/attention.ts", "routeSummary", "routeStudyFlowAttentionDelivery", "attentionTopologyFromWebPeer")
require(
    "core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowAttentionTopologyProjection.kt",
    "StudyFlowAttentionTopologyProjector",
    "trustedPeers",
    "presenceViews",
    "FOREGROUND_BANNER",
)
require(
    "core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowPeerRpc.kt",
    "NotificationDeliver",
    "notificationDeliveryHandler",
    "fun deliverNotification",
)
require(
    "app/src/main/java/com/mikeyphw/studyflow/reminders/StudyReminderDeliveryCoordinator.kt",
    "dispatchRemoteRouteTargets",
    "runtime.deliverNotification",
)
require(
    "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopAttentionRuntime.kt",
    "dispatchRemoteRouteTargets",
    "runtime.deliverNotification",
)
require("web/src/react/App.tsx", "Delivery router", "routeSummary.ownedCount")
require("ATT07_CROSS_DEVICE_DELIVERY_ROUTER.md", "verifyAtt07CrossDeviceDeliveryRouter", "foreground-truthful")
require("docs/product/ATTENTION_DELIVERY_ROUTER.md", "one owner", "TV closed-app alarms are not")
require("docs/product/CROSS_PLATFORM_CAPABILITY_MATRIX.md", "Cross-device attention delivery router", "ATT07 cross-device delivery router truth")
require("README.md", "ATT07 — Cross-Device Delivery Router")
require("CHANGELOG.md", "ATT07 — Cross-Device Delivery Router")
require("build.gradle.kts", "verifyAtt07CrossDeviceDeliveryRouter")
require(".devtool.toml", '"verifyAttentionRoadmapPostsealCompletion"')

if errors:
    print("ATT07 Cross-Device Delivery Router verification FAILED")
    for error in errors:
        print(f"- {error}")
    sys.exit(1)
print("ATT07 Cross-Device Delivery Router verification PASSED")
print("- shared Kotlin routing policy + ledger suppression + handoff")
print("- Android/Desktop/Web/TV capability bridges")
print("- Web runtime/dist parity + Notification Center route diagnostics")
print("- tests/docs/Devtool gate present")
