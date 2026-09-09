#!/usr/bin/env python3
from __future__ import annotations

import json
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
WEB = ROOT / "web"

REQUIRED = [
    "SFLP10_11_WEB_TV_GENERALIZATION.md",
    "docs/architecture/SFLP10_11_WEB_TV_GENERALIZATION.md",
    "core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowClientProfiles.kt",
    "core/link/src/test/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowClientProfilesTest.kt",
    "web/src/sflp_link.ts",
    "web/runtime/sflp_link.js",
    "web/dist/runtime/sflp_link.js",
    "web/tests/test_sflp10_web_link.py",
    "app-tv/src/main/java/com/mikeyphw/studyflow/tv/link/StudyFlowTvSflpPeer.kt",
    "app-tv/src/main/java/com/mikeyphw/studyflow/tv/link/StudyFlowTvSflpStatePersistence.kt",
    "app-tv/src/test/java/com/mikeyphw/studyflow/tv/StudyFlowTvSflpGeneralizationContractTest.kt",
    "protocol/sflp/v1/sflp-web-peer.schema.json",
    "protocol/sflp/v1/golden/web-peer-snapshot.json",
    "core/link/src/test/resources/sflp/v1/web-peer-snapshot.json",
]

def fail(message: str) -> None:
    print(f"SFLP10_11_WEB_TV_GENERALIZATION: FAIL\n- {message}", file=sys.stderr)
    raise SystemExit(1)

def text(path: str) -> str:
    target = ROOT / path
    if not target.is_file():
        fail(f"missing required file: {path}")
    return target.read_text(encoding="utf-8")

def require(path: str, needles: list[str]) -> str:
    value = text(path)
    for needle in needles:
        if needle not in value:
            fail(f"{path} missing contract token: {needle}")
    return value

def run_predecessor() -> None:
    result = subprocess.run(
        [sys.executable, "scripts/verify_sflp08_09_android_desktop_integration.py"],
        cwd=ROOT,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        check=False,
    )
    if result.returncode != 0:
        fail("SFLP08/SFLP09 predecessor seal failed:\n" + result.stdout[-5000:])

def validate_web_peer() -> None:
    require("web/src/main.tsx", ["installStudyFlowWebSflpPeer", "web_tv", "web"])
    source = require(
        "web/src/sflp_link.ts",
        [
            "StudyFlowWebLinkPeer",
            "StudyFlowWebPeerPersistence",
            "StudyFlowWebSocketTransportAdapter",
            "StudyFlowWebManualDiscovery",
            "studyflow.sflp.web.peer.v1",
            "workspace.sync",
            "device.presentation_sink",
            "pairFromQr",
            "rememberManualEndpoint",
            "appendLocalMutation",
            "applyRemoteOperations",
            "operationsAfter",
            "frontier",
            "acknowledge",
            "revoke",
            "forget",
        ],
    )
    for forbidden in ["firebase", "supabase", "google-analytics", "segment.io"]:
        if forbidden in source.lower():
            fail(f"Web SFLP peer introduced forbidden backend dependency: {forbidden}")
    require("web/service-worker.js", ["./runtime/sflp_link.js"])
    require("web/dist/service-worker.js", ["./runtime/sflp_link.js"])
    runtime = ROOT / "web/runtime/sflp_link.js"
    dist = ROOT / "web/dist/runtime/sflp_link.js"
    if runtime.read_bytes() != dist.read_bytes():
        fail("Web SFLP runtime differs between web/runtime and web/dist/runtime")
    check = subprocess.run(["node", "--check", str(runtime)], cwd=WEB, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, check=False)
    if check.returncode != 0:
        fail("web/runtime/sflp_link.js syntax failed:\n" + check.stdout[-2000:])

def validate_android_tv_peer() -> None:
    require("app-tv/build.gradle.kts", ['implementation(project(":core:link"))'])
    require(
        "app-tv/src/main/java/com/mikeyphw/studyflow/tv/StudyFlowTvBuildContract.kt",
        [
            "StudyFlowTvSflpGeneralizationBuildContract",
            "androidTvGenericSflpPeerIntroduced",
            "androidTvClientKind = \"android_tv\"",
            "legacyPhoneTvTransportRemainsCompatibilityAdapter",
            "androidTvIsNotPhoneHub",
        ],
    )
    require(
        "app-tv/src/main/java/com/mikeyphw/studyflow/tv/link/StudyFlowTvSflpPeer.kt",
        [
            "StudyFlowLinkClientKind.ANDROID_TV",
            "StudyFlowClientProfiles.capabilitiesFor",
            "StudyFlowPeerRuntime",
            "StudyFlowLanBeaconDiscovery",
            "StudyFlowManualDiscovery",
            "pairFromQr",
            "rememberManualEndpoint",
            "reconcile",
            "revoke",
            "forget",
            "LegacyPhoneTvTransportRemainsCompatibilityAdapter",
            "AndroidTvIsNotPhoneHub",
        ],
    )
    require(
        "app-tv/src/main/java/com/mikeyphw/studyflow/tv/link/StudyFlowTvSflpStatePersistence.kt",
        [
            "StudyFlowDeviceIdentityStore",
            "StudyFlowTrustStore",
            "StudyFlowOperationJournalStore",
            "StudyFlowOutgoingQueueStore",
            "StudyFlowPeerEndpointStore",
            "studyflow-tv-sflp-peer-v1",
        ],
    )
    require(
        "app-tv/src/main/java/com/mikeyphw/studyflow/tv/StudyFlowTvActivity.kt",
        [
            "StudyFlowTvSflpPeer",
            "sflpPeer.start()",
            "sflpPeer.close()",
            "tv_settings_sflp_peer_status_format",
        ],
    )

def validate_profiles_and_goldens() -> None:
    for path in REQUIRED:
        if not (ROOT / path).is_file():
            fail(f"missing required file: {path}")
    profiles = require(
        "core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowClientProfiles.kt",
        ["val Web", "val WebTv", "val AndroidTv", "capabilitiesFor", "DevicePresentationSink", "WorkspaceSync", "DpadInput"],
    )
    if "AndroidTv: List<StudyFlowLinkCapability> = WebTv" not in profiles:
        fail("Android TV profile must build from Web TV presentation/sync profile")
    for relative in ["protocol/sflp/v1/sflp-web-peer.schema.json", "protocol/sflp/v1/golden/web-peer-snapshot.json"]:
        try:
            json.loads((ROOT / relative).read_text(encoding="utf-8"))
        except Exception as exc:
            fail(f"invalid JSON {relative}: {exc}")
    if (ROOT / "protocol/sflp/v1/golden/web-peer-snapshot.json").read_bytes() != (ROOT / "core/link/src/test/resources/sflp/v1/web-peer-snapshot.json").read_bytes():
        fail("web peer golden fixture drift between protocol and JVM resource")

def validate_docs() -> None:
    require("SFLP10_11_WEB_TV_GENERALIZATION.md", ["SFLP10 Web integration", "SFLP11 Android TV generalization", "Android TV is no longer modeled as a phone-owned spoke"])
    require("docs/roadmap/CROSS_PLATFORM_PARITY_CONNECTIVITY_ROADMAP.md", ["SFLP10 | Web integration — delivered", "SFLP11 | Android TV generalization — delivered"])
    require("docs/product/CROSS_PLATFORM_CAPABILITY_MATRIX.md", ["SFLP10/SFLP11 status", "Android is not a required hub"])
    require("CHANGELOG.md", ["SFLP10/SFLP11", "Web + Android TV generic peers"])

def main() -> int:
    run_predecessor()
    validate_profiles_and_goldens()
    validate_web_peer()
    validate_android_tv_peer()
    validate_docs()
    print("SFLP10_11_WEB_TV_GENERALIZATION: PASS")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
