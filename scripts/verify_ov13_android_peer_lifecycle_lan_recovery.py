#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]

checks: list[tuple[str, str, bool]] = []

def text(path: str) -> str:
    p = ROOT / path
    if not p.is_file():
        raise SystemExit(f"missing required file: {path}")
    return p.read_text(encoding="utf-8")

def check(finding: str, message: str, condition: bool) -> None:
    checks.append((finding, message, condition))

app = text("app/src/main/java/com/mikeyphw/studyflow/StudyFlowApplication.kt")
peer = text("app/src/main/java/com/mikeyphw/studyflow/link/AndroidStudyFlowLinkPeer.kt")
nsd = text("app/src/main/java/com/mikeyphw/studyflow/link/AndroidStudyFlowNsdDiscovery.kt")
lan = text("core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowLanDiscovery.kt")
tv = text("core/tvlink/src/main/kotlin/com/mikeyphw/studyflow/core/tvlink/StudyFlowTvLocalLink.kt")
saf = text("app/src/main/java/com/mikeyphw/studyflow/StudyFlowApp.kt")
core = text("core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowPeerLifecycle.kt")
build = text("build.gradle.kts")
# Stable GitHub validation uses the tracked script; the transient artifact hook
# is optional here, and checked only if the transaction created it.
validation = text("scripts/validate_ov13_android_peer_lifecycle_lan_recovery.sh")
artifact_hook = ROOT / ".devtool-artifact/validation/validate.sh"
if artifact_hook.is_file():
    validation += "\n" + artifact_hook.read_text(encoding="utf-8")

check(
    "S5-01",
    "Application startup is no longer an unconditional generic SFLP socket/broadcast start",
    "linkPeer.startIfUserEnabled()" in app
    and "runCatching { linkPeer.start() }" not in app
    and "Application startup must not call linkPeer.start() automatically" in app,
)
check(
    "S5-02",
    "Android peer lifecycle/ready state is owned by persisted peer lifecycle, not only Application",
    "studyflow_sflp_peer_lifecycle" in peer
    and "AndroidStudyFlowPeerLifecycleState" in peer
    and "isLocalPeerEnabled" in peer
    and "lifecycleState" in peer,
)
check(
    "S5-05",
    "NSD failure keeps desired state and schedules restart",
    "desiredStarted" in nsd and "scheduleRestartAfterFailure" in nsd and "DiscoveryRestartDelayMillis" in nsd,
)
check(
    "S5-06",
    "Both Android NSD paths serialize and retry resolution instead of a one-shot callback-only resolve",
    "pendingResolveQueue" in nsd and "retryResolve" in nsd and "pendingResolveQueue" in tv and "retryResolve" in tv,
)
check(
    "S5-07",
    "IPv6 link-local scope identifiers are preserved in endpoint hosts",
    "address.hostAddress" in lan
    and ".substringBefore('%')" not in nsd
    and ".substringBefore('%')" not in peer
    and "fe80::1234%wlan0" in text("core/link/src/test/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowOv13PeerLifecycleLanRecoveryTest.kt"),
)
check(
    "S5-08",
    "Pairing QR refuses loopback fallback and requires externally reachable endpoints",
    "sflp+tcp://127.0.0.1" not in peer
    and "No externally reachable LAN endpoint is available for the pairing QR" in peer
    and "isExternallyReachableEndpoint" in peer,
)
check(
    "S5-10",
    "Generic SFLP LAN sockets require user-facing Android local-network permission first",
    "AndroidStudyFlowLocalNetworkPermission" in peer
    and "NEARBY_WIFI_DEVICES" in peer
    and "Local network permission is required before SFLP sockets start" in peer,
)
check(
    "S5-11",
    "Failed manual sync folder provider setup releases the just-taken SAF grant",
    "syncFolderGrantPersisted" in saf and "releasePersistableUriPermission(uri, permissionFlags)" in saf,
)
check(
    "DS2-07",
    "Android link close/restart reattaches discovery sources instead of permanently detaching registry sources",
    "discoveryRegistry.add(nsdDiscovery)" in peer and "discoveryRegistry.remove(nsdDiscovery)" in peer,
)
check(
    "DS2-39",
    "Peer startup failure remains retryable after Ready through explicit lifecycle/backoff state",
    "StudyFlowPeerLifecycleRecovery" in core
    and "retryAtEpochMillis" in core
    and "lastFailureReason" in nsd,
)
check(
    "OV13-GRADLE-HOOK",
    "Integrated validation task and artifact hook are wired without stopping Gradle daemons",
    "verifyOv13AndroidPeerLifecycleLanRecovery" in build
    and "verify_ov13_android_peer_lifecycle_lan_recovery.py" in validation
    and "--stop" not in validation
    and "gradle --stop" not in validation,
)

failed = [(finding, message) for finding, message, ok in checks if not ok]
if failed:
    for finding, message in failed:
        print(f"FAIL {finding}: {message}")
    raise SystemExit(1)

for finding, message, _ in checks:
    print(f"PASS {finding}: {message}")
print(f"OV13 source evidence passed: {len(checks)}/{len(checks)} checks")
