#!/usr/bin/env python3
from __future__ import annotations

import json
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

REQUIRED = [
    "SFLP04_05_IDENTITY_TRANSPORT.md",
    "docs/architecture/SFLP04_05_IDENTITY_TRANSPORT.md",
    "core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowDeviceIdentity.kt",
    "core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowPairing.kt",
    "core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowTransport.kt",
    "core/link/src/test/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowDeviceIdentityTrustTest.kt",
    "core/link/src/test/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowTransportTest.kt",
    "core/link/src/test/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowTrustTransportGoldenTest.kt",
    "protocol/sflp/v1/sflp-trust-transport.schema.json",
    "protocol/sflp/v1/golden/pairing-request.json",
    "protocol/sflp/v1/golden/pairing-grant.json",
    "protocol/sflp/v1/golden/reconnect-challenge.json",
    "protocol/sflp/v1/golden/transport-endpoint.json",
    "core/link/src/test/resources/sflp/v1/pairing-request.json",
    "core/link/src/test/resources/sflp/v1/pairing-grant.json",
    "core/link/src/test/resources/sflp/v1/reconnect-challenge.json",
    "core/link/src/test/resources/sflp/v1/transport-endpoint.json",
]


def fail(message: str) -> None:
    print(f"SFLP04_05_IDENTITY_TRANSPORT: FAIL\n- {message}", file=sys.stderr)
    raise SystemExit(1)


def require_text(path: str, needles: list[str]) -> str:
    target = ROOT / path
    if not target.is_file():
        fail(f"missing required file: {path}")
    text = target.read_text(encoding="utf-8")
    for needle in needles:
        if needle not in text:
            fail(f"{path} missing contract token: {needle}")
    return text


def run_predecessor() -> None:
    result = subprocess.run(
        [sys.executable, "scripts/verify_sflp02_03_journal_reconciliation.py"],
        cwd=ROOT,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        check=False,
    )
    if result.returncode != 0:
        fail("SFLP02/SFLP03 predecessor seal failed:\n" + result.stdout[-4000:])


def validate_json() -> None:
    for relative in REQUIRED:
        if relative.endswith(".json"):
            try:
                json.loads((ROOT / relative).read_text(encoding="utf-8"))
            except Exception as exc:
                fail(f"invalid JSON {relative}: {exc}")
    golden_names = [
        "pairing-request.json",
        "pairing-grant.json",
        "reconnect-challenge.json",
        "transport-endpoint.json",
    ]
    for name in golden_names:
        protocol = (ROOT / "protocol/sflp/v1/golden" / name).read_bytes()
        test_resource = (ROOT / "core/link/src/test/resources/sflp/v1" / name).read_bytes()
        if protocol != test_resource:
            fail(f"golden fixture drift between protocol and JVM test resource: {name}")


def validate_identity_trust() -> None:
    identity = require_text(
        "core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowDeviceIdentity.kt",
        [
            "interface StudyFlowDeviceIdentityStore",
            "class StudyFlowDeviceIdentityManager",
            "interface StudyFlowTrustStore",
            "class StudyFlowTrustManager",
            "StudyFlowTrustState.REVOKED",
            "PairingOfferLifetimeMillis = 120_000L",
            "PairingAttemptLimit = 10",
            "ReconnectChallengeLifetimeMillis = 30_000L",
            "HmacSHA256",
            "verifyReconnectProof",
            "MessageDigest.isEqual",
        ],
    )
    pairing = require_text(
        "core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowPairing.kt",
        [
            "class StudyFlowPairingAuthority",
            "class StudyFlowPairingClient",
            "object StudyFlowPairingQrCodec",
            "StudyFlowPairingRejection.ATTEMPT_LIMIT",
            "StudyFlowPairingRejection.SELF_PAIRING",
            "StudyFlowTrustSecret(randomHex(32, secureRandom))",
            "StudyFlowPairingMessageNames",
        ],
    )
    if "phone" in identity.lower() or "tvDeviceName" in pairing:
        fail("SFLP04 core identity/trust contract must not encode phone/TV roles")


def validate_transport() -> None:
    transport = require_text(
        "core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowTransport.kt",
        [
            "interface StudyFlowConnection",
            "interface StudyFlowConnectionListener",
            "interface StudyFlowTransport",
            "interface StudyFlowListeningTransport",
            "class StudyFlowTransportRegistry",
            "class StudyFlowTcpTransport",
            "class StudyFlowAdapterTransport",
            "class StudyFlowManualDiscovery",
            "class StudyFlowCompositeDiscovery",
            "object StudyFlowMdnsContract",
            "const val ServiceType = \"_studyflow._tcp.\"",
            "const val DefaultMaxFrameBytes = 1_048_576",
            "StudyFlowTransportScheme.WEBSOCKET",
            "StudyFlowTransportScheme.WEBSOCKET_SECURE",
            "StudyFlowTransportScheme.RELAY",
            "StudyFlowLengthPrefixedFrameCodec",
            "ServerSocket",
            "Socket",
        ],
    )
    forbidden = [
        "android.",
        "androidx.",
        "NsdManager",
        "Binder",
        "WebView",
        "window.",
        "document.",
    ]
    lower = transport.lower()
    for token in forbidden:
        if token.lower() in lower:
            fail(f"platform-specific API leaked into core transport contract: {token}")


def validate_tests() -> None:
    trust_tests = require_text(
        "core/link/src/test/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowDeviceIdentityTrustTest.kt",
        [
            "identityPersistsAcrossRefreshAndUpdatesDescriptorWithoutChangingDeviceId",
            "qrAndPairingCodeFlowsCreateReciprocalRememberedTrust",
            "pairingOfferExpiresAndTenFailedAttemptsInvalidateIt",
            "reconnectProofIsOneTimeAndRevocationPreventsFutureReconnects",
            "freshPairingReplacesRevokedTrustWithANewSecret",
        ],
    )
    transport_tests = require_text(
        "core/link/src/test/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowTransportTest.kt",
        [
            "manualEndpointParserSupportsTcpIpv6WebSocketAndRelay",
            "lengthPrefixedFramesRoundTripAndRejectOversizePayloads",
            "nativeTcpTransportCarriesRealFramedTrafficOverLoopback",
            "adapterTransportSupportsWebSocketAndRelayWithoutPlatformApisInCore",
            "mdnsContractAndCompositeDiscoveryDeduplicateTheSameDevice",
        ],
    )
    if "Thread" not in transport_tests or "CountDownLatch" not in transport_tests:
        fail("native TCP test must exercise an actual loopback client/server exchange")
    if "revoke" not in trust_tests.lower() or "qr" not in trust_tests.lower():
        fail("trust tests must cover QR pairing and revocation")


def validate_wiring_and_truth() -> None:
    gradle = require_text(
        "build.gradle.kts",
        [
            'tasks.register<Exec>("verifySflp04Sflp05IdentityTransport")',
            'dependsOn("verifySflp02Sflp03JournalReconciliation", ":core:link:test")',
            'commandLine("python3", "scripts/verify_sflp04_05_identity_transport.py")',
        ],
    )
    config = require_text(
        ".devtool.toml",
        ["verifySflp24FinalReleaseSeal"],
    )
    roadmap = require_text(
        "docs/roadmap/CROSS_PLATFORM_PARITY_CONNECTIVITY_ROADMAP.md",
        [
            "SFLP04 Device identity and trust — COMPLETE",
            "SFLP05 Transport abstraction — COMPLETE",
            "SFLP06 Multi-peer workspace topology",
        ],
    )
    matrix = require_text(
        "docs/product/CROSS_PLATFORM_CAPABILITY_MATRIX.md",
        [
            "SFLP04/SFLP05 generic identity, trust, pairing",
            "client bindings and multi-peer routing begin in SFLP06-SFLP12",
        ],
    )
    _ = gradle, config, roadmap, matrix


def validate_line_lengths() -> None:
    candidates = [
        "build.gradle.kts",
        *[path for path in REQUIRED if path.endswith(".kt")],
    ]
    errors: list[str] = []
    for relative in candidates:
        path = ROOT / relative
        for number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
            if len(line) > 120:
                errors.append(f"{relative}:{number}:{len(line)}")
    if errors:
        fail("line length >120: " + ", ".join(errors[:20]))


def main() -> None:
    for relative in REQUIRED:
        if not (ROOT / relative).is_file():
            fail(f"missing required file: {relative}")
    run_predecessor()
    validate_json()
    validate_identity_trust()
    validate_transport()
    validate_tests()
    validate_wiring_and_truth()
    validate_line_lengths()
    print("SFLP04_05_IDENTITY_TRANSPORT: PASS")


if __name__ == "__main__":
    main()
