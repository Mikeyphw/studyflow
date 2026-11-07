#!/usr/bin/env python3
"""Fail-closed source audit for StudyFlow Devtool CI activation.

No network, Gradle, signing, or GitHub API mutations.
"""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
LEGACY = ROOT / '.github/workflows/studyflow-ci.yml'
PRIMARY = ROOT / '.github/workflows/studyflow-devtool-primary.yml'
SIGNING = ROOT / '.github/workflows/studyflow-hosted-sign.yml'

class CutoverContractError(ValueError):
    pass

def require(value, why):
    if not value:
        raise CutoverContractError(why)

def job(source, name):
    m = re.search(r'(?ms)^  ' + re.escape(name) + r':\n(.*?)(?=^  [a-z][a-z0-9-]*:\n|\Z)', source)
    require(m is not None, 'Required job missing: ' + name)
    return m.group(1)

def verify(root=ROOT):
    old = (root/'.github/workflows/studyflow-ci.yml').read_text(encoding='utf-8')
    new = (root/'.github/workflows/studyflow-devtool-primary.yml').read_text(encoding='utf-8')
    require('  pull_request:\n' in old and '  push:\n' in old and
            '  workflow_dispatch:\n' in old, 'Legacy rollback entrypoints missing')
    source=job(old,'source')
    require('if:' not in source.split('    steps:',1)[0],
            'Legacy source status must still run on PR and push')
    for lane in ('web', 'desktop', 'android'):
        actual=job(old,lane)
        condition="    if: ${{ github.event_name == 'workflow_dispatch' && (inputs.lane == 'all' || inputs.lane == '"+lane+"') }}"
        require(actual.startswith(condition+'\n'), 'Legacy '+lane+' can run automatically or lost rollback')
        require('      - uses: actions/checkout' in actual or '      - name:' in actual,
                'Legacy '+lane+' build removed rather than kept for rollback')
    require(job(old,'deep-contracts').startswith("    if: ${{ github.event_name == 'workflow_dispatch' && inputs.lane == 'deep' }}\n"),
            'Deep rollback must remain opt-in')
    require('  pull_request:\n' in new and '  push:\n    branches: [main]\n' in new,
            'Devtool primary CI lost PR/push trigger')
    require('  gate:\n    name: Devtool CI gate\n' in new,
            'Devtool CI aggregate status missing')
    for lane in ('phone','tv','desktop','web'):
        require('          - lane: '+lane+'\n' in new, 'Devtool lane missing: '+lane)
    require('  contents: read\n' in old and '  contents: read\n' in new,
            'CI permissions drifted')
    require('secrets.' not in old and 'secrets.' not in new and
            'environment: studyflow-signing' not in new,
            'Signing credentials entered routine CI')
    require((root/'.github/workflows/studyflow-hosted-sign.yml').is_file(),
            'Protected signing workflow missing')
    return True

if __name__ == '__main__':
    try:
        verify()
    except (OSError,CutoverContractError) as exc:
        print('STUDYFLOW_CI_CUTOVER_REFUSED:', str(exc),file=sys.stderr)
        raise SystemExit(2)
    print('STUDYFLOW_CI_CUTOVER_SOURCE_PASS: Devtool required; legacy builds dispatch-only')
