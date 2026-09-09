#!/usr/bin/env python3
from __future__ import annotations

import json
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
WEB = ROOT / "web"

REQUIRED = [
    "SFLP12_13_WEB_TV_HANDOFF.md",
    "docs/architecture/SFLP12_13_WEB_TV_HANDOFF.md",
    "core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowActivityHandoff.kt",
    "core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowWebTvConnectivity.kt",
    "core/link/src/test/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowActivityHandoffTest.kt",
    "core/link/src/test/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowWebTvConnectivityTest.kt",
    "core/link/src/test/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowActivityHandoffGoldenTest.kt",
    "protocol/sflp/v1/sflp-activity-handoff.schema.json",
    "protocol/sflp/v1/golden/activity-handoff-offer.json",
    "protocol/sflp/v1/golden/web-tv-receiver-offer.json",
    "core/link/src/test/resources/sflp/v1/activity-handoff-offer.json",
    "core/link/src/test/resources/sflp/v1/web-tv-receiver-offer.json",
    "web/src/sflp_link.ts",
    "web/runtime/sflp_link.js",
    "web/dist/runtime/sflp_link.js",
    "web/tests/test_sflp12_13_web_tv_handoff.py",
    "app/src/main/java/com/mikeyphw/studyflow/link/AndroidStudyFlowHandoffBridge.kt",
    "app/src/test/java/com/mikeyphw/studyflow/StudyFlowAndroidHandoffBridgeTest.kt",
    "app-tv/src/main/java/com/mikeyphw/studyflow/tv/link/StudyFlowTvSflpHandoffBridge.kt",
    "app-tv/src/test/java/com/mikeyphw/studyflow/tv/StudyFlowTvSflpHandoffBridgeTest.kt",
    "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopActivityHandoffController.kt",
    "desktopApp/src/test/kotlin/com/mikeyphw/studyflow/desktop/DesktopActivityHandoffControllerTest.kt",
]

def fail(message: str) -> None:
    print(f"SFLP12_13_WEB_TV_HANDOFF: FAIL\n- {message}", file=sys.stderr)
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
        [sys.executable, "scripts/verify_sflp10_11_web_tv_generalization.py"],
        cwd=ROOT,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        check=False,
    )
    if result.returncode != 0:
        fail("SFLP10/SFLP11 predecessor seal failed:\n" + result.stdout[-5000:])

def validate_shared_core() -> None:
    require(
        "core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowActivityHandoff.kt",
        [
            "StudyFlowActivitySnapshot",
            "StudyFlowHandoffOffer",
            "StudyFlowActivityHandoffRegistry",
            "StudyFlowHandoffPolicy",
            "activity.handoff_offer",
            "activity.handoff_accept",
            "activity.handoff_resume",
            "activity.handoff_reject",
            "tv_write_capability",
            "missing_capability",
            "oneTimeNonceHex",
            "StudyFlowRemoteInputCommand",
        ],
    )
    require(
        "core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowWebTvConnectivity.kt",
        [
            "StudyFlowWebTvConnectivitySession",
            "studyflow.sflp.web_tv.receiver.v1",
            "projectionSnapshot",
            "acceptsRemoteInput",
            "DevicePresentationSink",
            "DpadInput",
        ],
    )
    for relative in [
        "protocol/sflp/v1/sflp-activity-handoff.schema.json",
        "protocol/sflp/v1/golden/activity-handoff-offer.json",
        "protocol/sflp/v1/golden/web-tv-receiver-offer.json",
    ]:
        try:
            json.loads((ROOT / relative).read_text(encoding="utf-8"))
        except Exception as exc:
            fail(f"invalid JSON {relative}: {exc}")
    pairs = [
        ("activity-handoff-offer.json", "activity.handoff_offer"),
        ("web-tv-receiver-offer.json", "studyflow.sflp.web_tv.receiver.v1"),
    ]
    for name, token in pairs:
        protocol = ROOT / "protocol/sflp/v1/golden" / name
        resource = ROOT / "core/link/src/test/resources/sflp/v1" / name
        if protocol.read_bytes() != resource.read_bytes():
            fail(f"golden fixture drift: {name}")
        if token not in protocol.read_text(encoding="utf-8"):
            fail(f"golden fixture {name} missing {token}")

def validate_platform_bridges() -> None:
    require(
        "app/src/main/java/com/mikeyphw/studyflow/link/AndroidStudyFlowHandoffBridge.kt",
        ["AndroidStudyFlowHandoffBridge", "offerActivityToPeer", "acceptFromPeer", "StudyFlowActivityHandoffCoordinator"],
    )
    require(
        "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopActivityHandoffController.kt",
        ["DesktopActivityHandoffController", "publishHandoff", "acceptHandoff", "StudyFlowActivityHandoffCoordinator"],
    )
    require(
        "app-tv/src/main/java/com/mikeyphw/studyflow/tv/link/StudyFlowTvSflpHandoffBridge.kt",
        ["StudyFlowTvSflpHandoffBridge", "acceptPresentationHandoff", "hasConsumed", "StudyFlowHandoffDecision.Accepted"],
    )

def validate_web_runtime() -> None:
    source = require(
        "web/src/sflp_link.ts",
        [
            "StudyFlowWebTvConnectivityController",
            "studyflow.sflp.web_tv.receiver.v1",
            "StudyFlowWebActivityHandoffController",
            "studyflow.sflp.activity_handoff.v1",
            "presentation_projection",
            "presentation_sink",
            "acceptRemoteInput",
            "acceptProjection",
            "createOffer",
            "acceptOffer",
            "resumePayload",
        ],
    )
    for forbidden in ["firebase", "supabase", "google-analytics", "segment.io"]:
        if forbidden in source.lower():
            fail(f"Web handoff introduced forbidden backend dependency: {forbidden}")
    runtime = ROOT / "web/runtime/sflp_link.js"
    dist = ROOT / "web/dist/runtime/sflp_link.js"
    if runtime.read_bytes() != dist.read_bytes():
        fail("Web SFLP runtime differs between web/runtime and web/dist/runtime")
    check = subprocess.run(["node", "--check", str(runtime)], cwd=WEB, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, check=False)
    if check.returncode != 0:
        fail("web/runtime/sflp_link.js syntax failed:\n" + check.stdout[-2000:])

def validate_docs_and_wiring() -> None:
    require("SFLP12_13_WEB_TV_HANDOFF.md", ["SFLP12 Web TV connectivity", "SFLP13 activity handoff", "TV receivers reject write-capability handoffs"])
    require("docs/roadmap/CROSS_PLATFORM_PARITY_CONNECTIVITY_ROADMAP.md", ["SFLP12 | Web TV connectivity — delivered", "SFLP13 | Activity handoff — delivered"])
    require("docs/product/CROSS_PLATFORM_CAPABILITY_MATRIX.md", ["SFLP12/SFLP13 status", "TV-class clients reject write-capability handoffs"])
    require("CHANGELOG.md", ["SFLP12/SFLP13", "Web TV connectivity and activity handoff"])
    require("build.gradle.kts", ["verifySflp12Sflp13WebTvHandoff", "verify_sflp12_13_web_tv_handoff.py"])

def main() -> int:
    for path in REQUIRED:
        if not (ROOT / path).is_file():
            fail(f"missing required file: {path}")
    run_predecessor()
    validate_shared_core()
    validate_platform_bridges()
    validate_web_runtime()
    validate_docs_and_wiring()
    print("SFLP12_13_WEB_TV_HANDOFF: PASS")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
