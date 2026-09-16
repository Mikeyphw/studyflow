#!/usr/bin/env python3
from __future__ import annotations
import json
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
errors: list[str] = []

def need(condition: bool, message: str) -> None:
    if not condition:
        errors.append(message)

def text(relative: str) -> str:
    path = ROOT / relative
    need(path.is_file(), f"missing {relative}")
    return path.read_text(encoding="utf-8") if path.is_file() else ""

schema_path = ROOT / "protocol/presence/v1/presence.schema.json"
need(schema_path.is_file(), "presence schema missing")
if schema_path.is_file():
    schema = json.loads(schema_path.read_text())
    need(schema.get("title") == "StudyFlow PRESENCE01 peer/activity presence", "presence schema title drifted")
    availability = schema.get("properties", {}).get("availability", {}).get("enum", [])
    need(availability == ["online", "away", "offline"], "presence availability wire values drifted")

checks = {
    "core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowPresence.kt": [
        "StudyFlowPresenceStatus", "STALE", "DefaultPresenceStaleAfterMillis = 45_000L",
        "DefaultPresenceExpiryMillis = 180_000L", "StudyFlowPresenceSessionId", "StudyFlowPresenceInstanceId",
        "StudyFlowPresenceActivityHint", "visibleTo(peer", "StudyFlowRouteTrace", "canVisit(localDeviceId)",
        "multiple", # documentation/comment anchor is intentionally not relied upon semantically
    ],
    "core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowPeerRpc.kt": [
        "StudyFlowPresenceMessageNames.Exchange", "handlePresence", "exchangePresence",
        "runCatching { exchangePresenceWith", "presencePublisher.ensureOnline", "presenceRegistry.ingest",
    ],
    "app/src/main/java/com/mikeyphw/studyflow/link/AndroidStudyFlowLinkPeer.kt": ["fun updatePresence(", "fun presenceViews("],
    "app-tv/src/main/java/com/mikeyphw/studyflow/tv/link/StudyFlowTvSflpPeer.kt": ["fun updatePresence(", "fun presenceViews("],
    "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopStudyFlowLinkService.kt": [
        "fun updatePresence(", "fun presenceViews(", "presence = workspaceId?.let(::presenceViews)",
    ],
    "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopAnalyticsSearchSettingsScreens.kt": [
        "Live StudyFlow presence", "Presence is ephemeral and optional; sync correctness never depends on it.",
    ],
    "web/src/presence.ts": [
        "StudyFlowWebPresenceRuntime", 'StudyFlowPresenceStatus=StudyFlowPresenceAvailability|"stale"',
        "staleAfterMillis:45_000", "expiryMillis:180_000", "visitedDeviceIds.includes(this.localDevice.deviceId)",
        "visibilityCapabilities", "sessionStartedAtEpochMillis",
    ],
    "web/src/sflp_link.ts": ["readonly presence:StudyFlowWebPresenceRuntime", "updatePresence(", "presenceViews(", "acceptPresenceExchange("],
    "core/link/src/test/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowPresenceTest.kt": [
        "onlineAwayOfflineStaleAndExpiryAreDistinct", "multipleInstancesOfOnePeerCoexistAndActivityHintIsCapabilityFiltered",
        "duplicateOldSequenceAndLoopAreRejectedWhileNewReconnectSessionSupersedesOldSession", "recordsForPeerNeverEchoBackThroughVisitedRoute",
    ],
    "core/link/src/test/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowPresenceRpcIntegrationTest.kt": [
        "authenticatedReconnectExchangesPresenceAfterDurableReconciliation", "threePeerGossipRelaysTrustedPresenceWithoutEchoLoop",
    ],
    "web/tests/test_presence01.py": ["test_web_presence_runtime_behavior", "test_presence_is_ephemeral_and_web_peer_exposes_it"],
    "web/tests/test_wtv06_07_history_control_relay.py": [
        "def _start_relay_receiver(self):",
        "for attempt in range(1, 5):",
        "deadline = time.monotonic() + 12.0",
        "if proc.poll() is not None:",
        "TV relay did not become ready after retries",
    ],
}
for relative, tokens in checks.items():
    source = text(relative)
    for token in tokens:
        if token == "multiple":
            continue
        need(token in source, f"{relative} missing {token}")

# Presence must stay out of Web persisted snapshot.
sflp = text("web/src/sflp_link.ts")
start = sflp.find("export interface SflpWebPeerSnapshot")
end = sflp.find("}", start)
if start >= 0 and end > start:
    need("presence" not in sflp[start:end].lower(), "Web persisted SFLP snapshot must not contain presence")

# Durable reconcile must happen before optional/non-fatal presence exchange.
rpc = text("core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowPeerRpc.kt")
reconcile = rpc.find("workspaceRuntime.reconnect.reconcile")
presence = rpc.find("runCatching { exchangePresenceWith", reconcile)
need(reconcile >= 0 and presence > reconcile, "presence exchange must run after durable reconciliation")

remaining = json.loads(text("docs/roadmap/CROSS_PLATFORM_REMAINING_WORK.json"))
tracks = {item.get("id"): item for item in remaining.get("remaining_tracks", [])}
need(tracks.get("PRESENCE01", {}).get("status") == "complete", "PRESENCE01 must be complete")
need(tracks.get("PRESENCE01", {}).get("completed_through") == "PRESENCE01", "PRESENCE01 completed_through missing")
need(
    tracks.get("PARITY04", {}).get("status") in {"planned", "complete"},
    "PARITY04 must remain planned or be complete",
)
need(tracks.get("PARITY04", {}).get("prerequisites") == ["DOV24", "TVOV31", "WEB20", "PRESENCE01"], "PARITY04 prerequisite drift")
need(tracks.get("REL-FINAL", {}).get("status") == "pending_execution", "REL-FINAL must remain pending execution")

roadmap = text("docs/roadmap/CROSS_PLATFORM_PARITY_CONNECTIVITY_ROADMAP.md")
need("**PRESENCE01 is implemented.**" in roadmap, "roadmap does not mark PRESENCE01 implemented")
need("Presence exchange runs only after authenticated reconnect and durable reconciliation" in roadmap, "roadmap missing non-fatal ordering")
matrix = text("docs/product/CROSS_PLATFORM_CAPABILITY_MATRIX.md")
need("PRESENCE01 is **SUPPORTED**" in matrix, "capability matrix does not mark Presence supported")
need(
    "PARITY04 is the next cross-platform acceptance seal" in matrix
    or "PARITY04 is **SUPPORTED / SEALED**" in matrix,
    "capability matrix must preserve the PARITY04 transition",
)

if errors:
    print("PRESENCE01_PEER_ACTIVITY_PRESENCE: FAIL")
    for error in errors:
        print("-", error)
    sys.exit(1)
print("PRESENCE01_PEER_ACTIVITY_PRESENCE: PASS")
