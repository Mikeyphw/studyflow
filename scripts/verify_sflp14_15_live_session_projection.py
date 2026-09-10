#!/usr/bin/env python3
from __future__ import annotations

import json
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
WEB = ROOT / "web"

REQUIRED = [
    "SFLP14_15_LIVE_SESSION_PROJECTION.md",
    "docs/architecture/SFLP14_15_LIVE_SESSION_PROJECTION.md",
    "core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowLiveSessionProjection.kt",
    "core/link/src/test/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowLiveSessionContinuityTest.kt",
    "core/link/src/test/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowPresentationProjectionTest.kt",
    "core/link/src/test/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowLiveProjectionGoldenTest.kt",
    "protocol/sflp/v1/sflp-live-session-projection.schema.json",
    "protocol/sflp/v1/golden/live-session-event.json",
    "protocol/sflp/v1/golden/presentation-projection-frame.json",
    "core/link/src/test/resources/sflp/v1/live-session-event.json",
    "core/link/src/test/resources/sflp/v1/presentation-projection-frame.json",
    "app/src/main/java/com/mikeyphw/studyflow/link/AndroidStudyFlowLiveProjectionBridge.kt",
    "app/src/test/java/com/mikeyphw/studyflow/StudyFlowAndroidLiveProjectionBridgeTest.kt",
    "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopLiveSessionProjectionController.kt",
    "desktopApp/src/test/kotlin/com/mikeyphw/studyflow/desktop/DesktopLiveSessionProjectionControllerTest.kt",
    "app-tv/src/main/java/com/mikeyphw/studyflow/tv/link/StudyFlowTvLiveProjectionBridge.kt",
    "app-tv/src/test/java/com/mikeyphw/studyflow/tv/StudyFlowTvLiveProjectionBridgeTest.kt",
    "web/tests/test_sflp14_15_live_projection.py",
]


def fail(message: str) -> None:
    print(f"SFLP14_15_LIVE_SESSION_PROJECTION: FAIL\n- {message}", file=sys.stderr)
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
        [sys.executable, "scripts/verify_sflp12_13_web_tv_handoff.py"],
        cwd=ROOT,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        check=False,
    )
    if result.returncode != 0:
        fail("SFLP12/SFLP13 predecessor seal failed:\n" + result.stdout[-5000:])


def validate_shared_core() -> None:
    source = require(
        "core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowLiveSessionProjection.kt",
        [
            "StudyFlowLiveSessionSnapshot",
            "StudyFlowLiveSessionEvent",
            "StudyFlowLiveSessionReducer",
            "StudyFlowLiveSessionContinuityController",
            "fencingToken",
            "sessionEpoch",
            "stale_epoch",
            "stale_sequence",
            "already_completed",
            "completionOperationId",
            "StudyFlowPresentationProjectionOffer",
            "StudyFlowPresentationFrame",
            "StudyFlowPresentationProjectionReceiver",
            "projection_write_capability",
            "presentation.projection_offer",
            "presentation.projection_frame",
            "presentation.projection_ack",
            "presentation.projection_control",
            "session.live_complete",
        ],
    )
    if "StudyFlowDomainOperationTypes.SessionCompleted" not in source:
        fail("live completion must remain a journal/domain operation")
    for relative in [
        "protocol/sflp/v1/sflp-live-session-projection.schema.json",
        "protocol/sflp/v1/golden/live-session-event.json",
        "protocol/sflp/v1/golden/presentation-projection-frame.json",
    ]:
        try:
            json.loads((ROOT / relative).read_text(encoding="utf-8"))
        except Exception as exc:
            fail(f"invalid JSON {relative}: {exc}")
    for name, token in [
        ("live-session-event.json", "session.live_cursor"),
        ("presentation-projection-frame.json", "presentation.projection_frame"),
    ]:
        protocol = ROOT / "protocol/sflp/v1/golden" / name
        resource = ROOT / "core/link/src/test/resources/sflp/v1" / name
        if protocol.read_bytes() != resource.read_bytes():
            fail(f"golden fixture drift: {name}")
        if token not in protocol.read_text(encoding="utf-8"):
            fail(f"golden fixture {name} missing {token}")


def validate_platforms() -> None:
    require(
        "app/src/main/java/com/mikeyphw/studyflow/link/AndroidStudyFlowLiveProjectionBridge.kt",
        ["AndroidStudyFlowLiveProjectionBridge", "liveSessionSnapshot", "projectionOffer", "acceptProjectionFrame"],
    )
    require(
        "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopLiveSessionProjectionController.kt",
        ["DesktopLiveSessionProjectionController", "controlLiveSession", "createProjectionOffer", "rememberAck"],
    )
    require(
        "app-tv/src/main/java/com/mikeyphw/studyflow/tv/link/StudyFlowTvLiveProjectionBridge.kt",
        ["StudyFlowTvLiveProjectionBridge", "canObserveLiveSession", "acceptPresentationProjection", "acknowledge"],
    )


def validate_web_runtime() -> None:
    source = require(
        "web/src/sflp_link.ts",
        [
            "StudyFlowWebLiveSessionContinuityController",
            "StudyFlowWebPresentationProjectionController",
            "session.live_cursor",
            "presentation.projection_control",
            "acknowledgeProjectionFrame",
            "createProjectionOffer",
            "acceptProjectionOffer",
            "applyProjectionFrame",
        ],
    )
    for forbidden in ["firebase", "supabase", "google-analytics", "segment.io"]:
        if forbidden in source.lower():
            fail(f"Web SFLP14/SFLP15 introduced forbidden backend dependency: {forbidden}")
    runtime = ROOT / "web/runtime/sflp_link.js"
    dist = ROOT / "web/dist/runtime/sflp_link.js"
    if runtime.read_bytes() != dist.read_bytes():
        fail("Web SFLP runtime differs between web/runtime and web/dist/runtime")
    check = subprocess.run(["node", "--check", str(runtime)], cwd=WEB, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, check=False)
    if check.returncode != 0:
        fail("web/runtime/sflp_link.js syntax failed:\n" + check.stdout[-2000:])
    test = subprocess.run([sys.executable, "-m", "unittest", "web.tests.test_sflp14_15_live_projection"], cwd=ROOT, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, check=False)
    if test.returncode != 0:
        fail("web SFLP14/SFLP15 runtime test failed:\n" + test.stdout[-3000:])


def validate_docs_and_wiring() -> None:
    require("SFLP14_15_LIVE_SESSION_PROJECTION.md", ["SFLP14 live session continuity", "SFLP15 universal presentation projection", "exactly-once completion"])
    require("docs/roadmap/CROSS_PLATFORM_PARITY_CONNECTIVITY_ROADMAP.md", ["SFLP14 Live session continuity", "delivered", "SFLP15 Universal presentation"])
    require("docs/product/CROSS_PLATFORM_CAPABILITY_MATRIX.md", ["SFLP14/SFLP15 status", "TV-class", "reject write-capability projection offers"])
    require("CHANGELOG.md", ["SFLP14/SFLP15", "Live session continuity and universal presentation projection"])
    require("build.gradle.kts", ["verifySflp14Sflp15LiveSessionProjection", "verify_sflp14_15_live_session_projection.py"])


def main() -> int:
    for path in REQUIRED:
        if not (ROOT / path).is_file():
            fail(f"missing required file: {path}")
    run_predecessor()
    validate_shared_core()
    validate_platforms()
    validate_web_runtime()
    validate_docs_and_wiring()
    print("SFLP14_15_LIVE_SESSION_PROJECTION: PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
