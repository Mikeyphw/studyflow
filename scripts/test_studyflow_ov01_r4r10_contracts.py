#!/usr/bin/env python3
"""R4R10 regression: full SFLP08/09 inventory, early CI, fail-closed mutations.

No local Gradle, signing, network or physical-device requirements.
"""
from __future__ import annotations
import contextlib
import io
import re
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
import verify_sflp08_09_android_desktop_integration as verifier

TOUCHED_KOTLIN = [
    "core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowPeerRpc.kt",
    "app/src/main/java/com/mikeyphw/studyflow/link/AndroidStudyFlowLinkStateStore.kt",
    "app/src/main/java/com/mikeyphw/studyflow/link/AndroidStudyFlowLinkPeer.kt",
    "app/src/main/java/com/mikeyphw/studyflow/link/AndroidStudyFlowNsdDiscovery.kt",
    "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopStudyFlowLinkPersistence.kt",
    "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopStudyFlowLinkService.kt",
    "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopAppState.kt",
    "app/src/main/java/com/mikeyphw/studyflow/StudyFlowApplication.kt",
]

def check(v: bool, why: str) -> None:
    if not v:
        raise AssertionError(why)

def run(path: str) -> None:
    p = subprocess.run([sys.executable, "scripts/" + path], cwd=ROOT, text=True,
                       capture_output=True, timeout=80)
    check(p.returncode == 0, path + ': ' + (p.stderr + p.stdout)[-3500:])

def main() -> None:
    candidates = verifier.formatting_candidates()
    check(len(candidates) == len(set(candidates)), "duplicate SFLP08/09 formatting candidates")
    check(all(p in candidates for p in TOUCHED_KOTLIN), "newly repaired owner missing from verifier inventory")
    for p in candidates:
        path = ROOT / p
        check(path.is_file(), 'SFLP formatting inventory file absent: ' + p)
        over = [(i, len(l)) for i,l in enumerate(path.read_text().splitlines(), 1) if len(l)>120]
        check(not over, f'{p}: first overlength: {over[:3]}')
    workflow=(ROOT/'.github/workflows/studyflow-ci.yml').read_text()
    check('python3 scripts/sflp08_09_formatting_preflight.py' in workflow,
          'missing early preflight from hosted source gate')
    check('python3 scripts/test_studyflow_ov01_r4r10_contracts.py' in workflow,
          'missing R4R10 negative regression from hosted source gate')
    check(workflow.index('python3 scripts/sflp08_09_formatting_preflight.py') <
          workflow.index('python3 scripts/test_studyflow_ov01_r4r10_contracts.py'),
          'early SFLP source gate must precede the full R4R10 regression')
    check('SF-DT-52' in (ROOT/'docs/ci/DEVTOOL_IMPROVEMENT_LEDGER.md').read_text(),
          'cumulative improvement ledger lost')

    # The same inventory must fail on source drift BEFORE a Gradle job begins.
    prior_root, prior_fn = verifier.ROOT, verifier.formatting_candidates
    try:
        with tempfile.TemporaryDirectory(prefix='studyflow-r4r10-negative-') as tmp:
            sample=Path(tmp)/'bad.kt';sample.write_text('val example = "'+'x'*121+'"\n')
            verifier.ROOT=Path(tmp)
            verifier.formatting_candidates=lambda:['bad.kt']
            error=io.StringIO()
            with contextlib.redirect_stderr(error):
                try: verifier.validate_line_lengths()
                except SystemExit as ex: check(ex.code == 1, 'bad formatting did not fail closed')
                else: raise AssertionError('overlong source was accepted')
            check('bad.kt:1:' in error.getvalue(), 'missing exact line evidence in error')
            sample.unlink()
            with contextlib.redirect_stderr(error):
                try:verifier.validate_line_lengths()
                except SystemExit as ex: check(ex.code == 1, 'missing format owner did not fail closed')
                else:raise AssertionError('missing format owner accepted')
    finally:
        verifier.ROOT, verifier.formatting_candidates=prior_root, prior_fn

    rpc=(ROOT/TOUCHED_KOTLIN[0]).read_text()
    check('StudyFlowLinkErrorCodes.UnsupportedCapability' in rpc and
          'StudyFlowLinkErrorCodes.IncompatibleProtocol' in rpc and
          'StudyFlowRouteKind.DIRECT' in rpc, 'RPC security/capability invariants missing')
    service=(ROOT/'desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopStudyFlowLinkService.kt').read_text()
    check('"No shareable Desktop LAN endpoint is available; " +\n                "pair by entering this desktop from the other device or add a reachable relay/manual endpoint."' in service,
          'pairing prompt changed while formatting')
    check('"pending=$pending endpoints=${endpoints.size} " +\n                "trusted=${runtime.trustManager.rememberedPeers().size}"' in service,
          'health message changed while formatting')
    run('sflp08_09_formatting_preflight.py')
    run('verify_sflp08_09_android_desktop_integration.py')
    print('STUDYFLOW_OV01_R4R10_CONTRACTS: PASS (full candidate list, two fail-closed mutations, no Gradle)')

if __name__=='__main__':main()
