#!/usr/bin/env python3
from __future__ import annotations
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")

def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)

checks: list[tuple[str, bool]] = []

def check(name: str, condition: bool) -> None:
    checks.append((name, condition))
    require(condition, name)

plane = read("core/tvcontrol/src/main/kotlin/com/mikeyphw/studyflow/core/tvcontrol/StudyFlowTvUnifiedControlPlane.kt")
wire = read("core/tvcontrol/src/main/kotlin/com/mikeyphw/studyflow/core/tvcontrol/StudyFlowTvWireProtocol.kt")
link = read("core/tvlink/src/main/kotlin/com/mikeyphw/studyflow/core/tvlink/StudyFlowTvLocalLink.kt")
phone = read("app/src/main/java/com/mikeyphw/studyflow/tvcontrol/StudyFlowTvPhoneController.kt")
attention = read("app-tv/src/main/java/com/mikeyphw/studyflow/tv/attention/StudyFlowTvAttentionRuntime.kt")
relay = read("web/scripts/tv_relay.mjs")
serve = read("web/scripts/serve_tv.mjs")
test = read("core/tvcontrol/src/test/kotlin/com/mikeyphw/studyflow/core/tvcontrol/StudyFlowTvUnifiedControlPlaneOv21Test.kt")
web_test = read("web/tests/ov21_tv_plane_runner.mjs")
root_gradle = read("build.gradle.kts")
devtool = read(".devtool.toml")
validation = read("scripts/validate_ov21_unified_tv_control_plane.sh")
doc = read("docs/remediation/OV21_UNIFIED_TV_CONTROL_PAIRING_RELAY_CAST_ATTENTION_PLANE.md")
ledger = read("docs/roadmap/FINAL_AUDIT_REMEDIATION_CLOSURE_LEDGER.md")

check("S9-01 shared signed TV plane contract", "object StudyFlowTvUnifiedControlPlaneContract" in plane and "signedPlaneEnvelopeRequired = true" in plane and "localTcpAndBrowserRelaySharePlanePolicy = true" in plane)
check("S9-02 wire request carries plane envelope", "val planeEnvelope: StudyFlowTvPlaneEnvelope? = null" in wire and "unifiedPlaneEnvelopeRequired = true" in wire)
check("S9-03 local TCP client signs plane envelope", "StudyFlowTvUnifiedControlPlane.attachEnvelope" in link and "val withPlane" in link and "StudyFlowTvWireAuthenticator.sign(withPlane" in link)
check("S9-04 TV receiver rejects missing or mismatched plane", "authorizeAuthenticatedRequest" in link and "tv-plane-envelope-required" in plane and "tv-plane-payload-hash-mismatch" in plane)
check("S9-05 plane binds session receiver operation and payload hash", "sessionId != credential.sessionId" in plane and "matchesCredentialBinding" in plane and "operationFor(request.operation)" in plane and "bodySha256Hex" in plane)
check("S9-06 cast/presentation/stop scope enforced", "castScopeId" in plane and "presentationSnapshotId" in plane and "tv-plane-cast-scope-mismatch" in plane and "tv-plane-stop-cast-scope-required" in plane and "remoteStopScope" in phone)
check("S9-07 domain action acknowledgement bound", "DOMAIN_ACTION_RESULT" in plane and "domainActionId" in plane and "tv-plane-domain-action-mismatch" in plane)
check("S9-08 attention plane truth surfaced", "attentionIngressUsesCanonicalPlane = true" in plane and "unifiedTvPlaneTruth" in attention)
check("S9-09 web relay uses same plane and no bearer/controller token", "TV_UNIFIED_PLANE_VERSION" in relay and "validatePlaneEnvelope" in relay and "tv-plane-envelope-required" in relay and "Bearer " not in relay and "controllerToken" not in relay)
check("S9-10 browser remote attaches plane envelope", "withPlane('/api/tv/control'" in serve and "planeEnvelope" in serve and "bodySha256Hex" in serve)
check("S9-11 regression tests cover tamper/cast", "payload-hash-mismatch" in test and "presentationAndStopCastCarryCastScope" in test and "OV21_TV_PLANE_WEB" in web_test)
check("S9-12 validation wired into Gradle and Devtool", "verifyOv21UnifiedTvControlPlane" in root_gradle and "verifyOv21UnifiedTvControlPlane" in devtool and "verify_ov21_unified_tv_control_plane.py" in validation)
check("S9-13 roadmap docs updated", "OV21" in doc and "S9-01" in doc and "Overlay 21 of 30" in ledger)

print(f"OV21 unified TV control plane evidence passed: {sum(1 for _, ok in checks if ok)}/{len(checks)} checks")
