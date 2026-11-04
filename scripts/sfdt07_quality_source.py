#!/usr/bin/env python3
"""SFDT-07 source-only quality subset through a real Devtool Python node.

This retains all Gradle-backed quality/parity work in the authoritative CI.
"""
from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
VERIFIERS = (
    'test_studyflow_ov01_r4_contracts.py',
    'test_sfdt03_ownership_contract.py',
    'test_sfdt06_shadow_contract.py',
    'test_sfdt06_same_sha_comparison.py',
)


def main() -> int:
    if (os.environ.get('GITHUB_ACTIONS') != 'true' or
            os.environ.get('RUNNER_OS') != 'Linux' or
            os.environ.get('RUNNER_ARCH') != 'X64'):
        print('SFDT07_QUALITY_REFUSED: reviewed GitHub host required', file=sys.stderr)
        return 2
    temp = os.environ.get('RUNNER_TEMP', '')
    if not temp or Path(temp).resolve().is_relative_to(ROOT.resolve()):
        print('SFDT07_QUALITY_REFUSED: external temporary evidence root required', file=sys.stderr)
        return 2
    observed = []
    for name in VERIFIERS:
        source = ROOT / 'scripts' / name
        if not source.is_file() or source.is_symlink():
            print('SFDT07_QUALITY_REFUSED: missing source verifier ' + name, file=sys.stderr)
            return 2
        check = subprocess.run([sys.executable, '-B', str(source)], cwd=ROOT, check=False,
                               capture_output=True, text=True, timeout=150)
        if check.returncode:
            print('SFDT07_QUALITY_FAILED: ' + name + ': ' +
                  (check.stdout + check.stderr)[-1200:], file=sys.stderr)
            return check.returncode
        observed.append({'script': name, 'sha256': hashlib.sha256(source.read_bytes()).hexdigest(),
                         'exit_code': 0})
    output = Path(temp).resolve() / 'sfdt07' / 'quality-source-checks.json'
    output.parent.mkdir(parents=True, exist_ok=True)
    if output.exists() or output.is_symlink():
        print('SFDT07_QUALITY_REFUSED: evidence already exists', file=sys.stderr)
        return 2
    output.write_text(json.dumps({'schema_version': 1,
        'kind': 'studyflow.sfdt07.source-quality-subset', 'verifiers': observed,
        'gradle_executed': False, 'full_quality_equivalence': False,
        'release_qualified': False}, sort_keys=True, indent=2) + '\n')
    print(f'SFDT07_QUALITY_SOURCE_PASS: {len(observed)} source verifiers; not full Gradle quality')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
