#!/usr/bin/env python3
from __future__ import annotations

import json
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
FAILURES: list[str] = []


def read(relative: str) -> str:
    path = ROOT / relative
    if not path.is_file():
        FAILURES.append(f"missing required file: {relative}")
        return ""
    return path.read_text(encoding="utf-8")


def require(condition: bool, message: str) -> None:
    if not condition:
        FAILURES.append(message)


def load_json(relative: str) -> dict:
    try:
        value = json.loads(read(relative))
    except json.JSONDecodeError as exc:
        FAILURES.append(f"invalid JSON in {relative}: {exc}")
        return {}
    require(isinstance(value, dict), f"{relative} must contain a JSON object")
    return value if isinstance(value, dict) else {}


implementation = read("SFLP01_PROTOCOL_BASELINE.md")
architecture = read("docs/architecture/SFLP01_PROTOCOL_BASELINE.md")
roadmap = read("docs/roadmap/CROSS_PLATFORM_PARITY_CONNECTIVITY_ROADMAP.md")
matrix = read("docs/product/CROSS_PLATFORM_CAPABILITY_MATRIX.md")
settings = read("settings.gradle.kts")
root_gradle = read("build.gradle.kts")
devtool = read(".devtool.toml")
module_gradle = read("core/link/build.gradle.kts")
protocol = read("core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowLinkProtocol.kt")
codec = read("core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowLinkCodec.kt")
protocol_test = read("core/link/src/test/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowLinkProtocolTest.kt")
golden_test = read("core/link/src/test/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowLinkCodecGoldenTest.kt")
changelog = read("CHANGELOG.md")

require(":core:link" in settings, "settings.gradle.kts does not include :core:link")
require("studyflow.kotlin.library.serialization" in module_gradle, "core:link is not a serialization JVM module")
require("kotlinx.serialization.core" in module_gradle, "core:link lacks serialization core dependency")
require("kotlinx.serialization.json" in module_gradle, "core:link lacks serialization JSON dependency")
require("junit4" in module_gradle, "core:link lacks JUnit test dependency")

for token in (
    "StudyFlowLinkProtocolVersion",
    "StudyFlowLinkProtocolSupport",
    "StudyFlowWorkspaceId",
    "StudyFlowDeviceId",
    "StudyFlowEntityId",
    "StudyFlowOperationId",
    "StudyFlowMessageId",
    "StudyFlowEntityRevision",
    "StudyFlowLinkCapability",
    "StudyFlowLinkPeerDescriptor",
    "StudyFlowLinkMessageType",
    "StudyFlowLinkEnvelope",
    "StudyFlowLinkError",
    "StudyFlowLinkCapabilities",
):
    require(token in protocol, f"protocol model missing {token}")

for client_kind in ("ANDROID", "DESKTOP", "WEB", "ANDROID_TV", "WEB_TV"):
    require(client_kind in protocol, f"protocol client kind missing {client_kind}")

for capability in (
    "workspace.read",
    "workspace.write",
    "session.execute",
    "session.present",
    "device.control",
    "device.presentation_sink",
    "device.presentation_source",
    "text_input.provide",
    "library.read",
    "goal.complete",
    "question.answer",
):
    require(capability in protocol, f"v1 capability vocabulary missing {capability}")

for message_name in ("link.hello", "link.ping", "link.capabilities_changed"):
    require(message_name in protocol, f"baseline link message missing {message_name}")

for error_code in (
    "link.incompatible_protocol",
    "link.unsupported_capability",
    "link.invalid_message",
    "link.unauthorized",
    "link.conflict",
    "link.unavailable",
    "link.internal_error",
):
    require(error_code in protocol, f"baseline error code missing {error_code}")

require("ignoreUnknownKeys = true" in codec, "codec must permit additive minor-version envelope fields")
require("encodeDefaults = true" in codec, "codec must encode stable default fields")
require("Unsupported SFLP protocol major" in codec, "codec must reject unsupported protocol majors")
require("StudyFlowLinkProtocol.negotiate" in protocol_test, "protocol tests do not cover version negotiation")
require("goldenMessagesRoundTripSemantically" in golden_test, "codec tests do not consume golden vectors")

schema = load_json("protocol/sflp/v1/sflp-envelope.schema.json")
require(schema.get("$schema", "").endswith("2020-12/schema"), "SFLP schema must use JSON Schema 2020-12")
require(schema.get("type") == "object", "SFLP schema root must be an object")
required = set(schema.get("required", []))
for field in (
    "protocol",
    "messageId",
    "workspaceId",
    "senderDeviceId",
    "type",
    "name",
    "sentAtEpochMillis",
    "payload",
):
    require(field in required, f"SFLP schema does not require {field}")

golden_paths = {
    "request": "protocol/sflp/v1/golden/hello-request.json",
    "response": "protocol/sflp/v1/golden/hello-response.json",
    "event": "protocol/sflp/v1/golden/capabilities-event.json",
    "error": "protocol/sflp/v1/golden/incompatible-protocol-error.json",
}
for expected_type, relative in golden_paths.items():
    message = load_json(relative)
    require(message.get("type") == expected_type, f"{relative} has wrong message type")
    require(message.get("protocol") == {"major": 1, "minor": 0}, f"{relative} is not SFLP 1.0")
    for field in ("messageId", "workspaceId", "senderDeviceId", "name", "sentAtEpochMillis", "payload"):
        require(field in message, f"{relative} missing {field}")
    if expected_type in {"response", "error"}:
        require(bool(message.get("correlationId")), f"{relative} must carry correlationId")
    else:
        require("correlationId" not in message, f"{relative} must not carry correlationId")

for name in golden_paths.values():
    root_bytes = (ROOT / name).read_bytes()
    mirrored = ROOT / "core/link/src/test/resources/sflp/v1" / Path(name).name
    require(mirrored.is_file(), f"missing mirrored Kotlin test vector for {name}")
    if mirrored.is_file():
        require(root_bytes == mirrored.read_bytes(), f"mirrored Kotlin test vector drifted from {name}")

require("SFLP01 Protocol baseline — COMPLETE" in roadmap, "roadmap does not mark SFLP01 complete")
require("No transport or replication is claimed yet" in roadmap, "roadmap overclaims SFLP01 connectivity")
require("platform-neutral SFLP v1 envelope/capability contract" in matrix, "capability matrix ignores SFLP01")
require("**not yet** provide a universal cross-client synchronization mesh" in matrix, "matrix overclaims mesh")
require("No transport, trust store, operation replication" in architecture, "architecture boundary is incomplete")
require("SFLP01 does not claim device discovery" in implementation, "implementation note overclaims delivery")
require("SFLP01 — platform-neutral StudyFlow Link Protocol baseline" in changelog, "changelog lacks SFLP01")

require(
    'tasks.register<Exec>("verifySflp01ProtocolBaseline")' in root_gradle,
    "root Gradle SFLP01 verifier task missing",
)
require('dependsOn(":core:link:test")' in root_gradle, "SFLP01 Gradle verifier must depend on :core:link:test")
require('"verifySflp01ProtocolBaseline"' in root_gradle, "checkAll does not retain the SFLP01 verifier")
require('"verifySflp24FinalReleaseSeal"' in devtool, "Devtool must retain the terminal SFLP seal")
require(":core:link:test" in devtool, "Devtool does not retain core:link tests")
require(":core:link:compileKotlin" not in devtool, "Devtool should rely on test task dependencies instead of an explicit compile phase")

# Preserve the already-established parity baseline while SFLP starts layering on top of it.
parity = subprocess.run(
    [sys.executable, str(ROOT / "scripts/verify_parity01_03_cross_platform_baseline.py")],
    cwd=ROOT,
    text=True,
    capture_output=True,
)
if parity.returncode != 0:
    FAILURES.append("PARITY01-PARITY03 regressed under SFLP01:\n" + (parity.stdout + parity.stderr).strip())

# New Kotlin/KTS code must honor the existing source-quality ceiling.
for relative in (
    "build.gradle.kts",
    "core/link/build.gradle.kts",
    "core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowLinkProtocol.kt",
    "core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowLinkCodec.kt",
    "core/link/src/test/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowLinkProtocolTest.kt",
    "core/link/src/test/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowLinkCodecGoldenTest.kt",
):
    for line_number, line in enumerate(read(relative).splitlines(), start=1):
        if len(line) > 120:
            FAILURES.append(f"{relative}:{line_number} exceeds 120 characters ({len(line)})")

if FAILURES:
    print("SFLP01_PROTOCOL_BASELINE: FAIL")
    for failure in FAILURES:
        print(f"- {failure}")
    raise SystemExit(1)

print("SFLP01_PROTOCOL_BASELINE: PASS")
