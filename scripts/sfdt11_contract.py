#!/usr/bin/env python3
"""SFDT11: fail-closed, source-pinned, GitHub-only StudyFlow cutover policy.

Does not build, sign, mutate branch protections, approve cutover, or run Gradle.
"""
from __future__ import annotations

import argparse
from hashlib import sha1, sha256
from pathlib import Path
import re
import subprocess
import sys
import tomllib

ROOT = Path(__file__).resolve().parents[1]
BASE = '4a592aa413a8581eb9ba5276d904dceabb6b5a3e'
DEVTOOL = '0ebd22c17ac3058704012a1e03ee3265328b0659'
# Git blob object IDs from an independent GitHub tree read of BASE; these paths
# cannot silently drift under a green SFDT11 policy gate.
FROZEN_BLOBS = {
    '.devtool.toml': '631f84e994ef6e8676726b558fa6c94718e92597',
    '.github/workflows/studyflow-ci.yml': 'b1fd1ea990fe9450288b3fd649d4855d025c5541',
    '.github/workflows/studyflow-hosted-sign.yml': '48c62104b0f2e4df26ecd041ffc75993da820f06',
    '.github/workflows/studyflow-devtool-shadow.yml': 'ee86f20237e47d3c1dc47ce28cab56a9a8297dc6',
    '.github/workflows/studyflow-devtool-native-expanded.yml': '99c19a7fd5b45d3bcaff00ccc040af9054a3cfca',
    '.github/workflows/studyflow-devtool-native-coverage.yml': '61ddb65581356761570d5d68c08cdb1771334a32',
    '.github/workflows/studyflow-devtool-sfdt10-debug-shadow.yml': 'ed3975b3fe0a748f3a65448b88a6457a4d51097a',
    '.github/workflows/studyflow-devtool-sfdt10-r2-compare.yml': '6792c9db4dbf2de33c911b5d020d30738d6aae99',
    'scripts/sfdt07_compare_runs.py': '023685b0897f2d58d82d90ab3222b02c0ae4cd58',
    'scripts/sfdt08_compare_coverage.py': '0dc9d61e9afc16668f544c9d2fec8b6607deb82b',
    'scripts/sfdt10_r2_compare.py': 'b58218cf0699512301125d216b5fd462129bf7c1',
    'web/package.json': '7ba6699525a72cc9784cbb3b8133b78d6fe53ff8',
    'web/package-lock.json': '19787979b8fcdba298099211802c60f72e4d16de',
    'settings.gradle.kts': 'ccd4b06305ca376c35772df1be70ff181d88c81a',
    'desktopApp/build.gradle.kts': '2e6d371939f715d3636fb4d9cb5f50e05d0fd8c4',
}
NEW_WORKFLOWS = (
    '.github/workflows/studyflow-devtool-sfdt11-source-gate.yml',
    '.github/workflows/studyflow-devtool-sfdt11-rollup.yml',
)
NEW_SCRIPTS = (
    'scripts/sfdt11_contract.py', 'scripts/test_sfdt11_contract.py',
    'scripts/sfdt11_rollup.py', 'scripts/test_sfdt11_rollup.py',
    'scripts/sfdt11_wave.py', 'scripts/test_sfdt11_wave.py',
)

class PolicyError(ValueError):
    pass


def need(ok: bool, message: str) -> None:
    if not ok:
        raise PolicyError(message)


def git_blob(path: Path, *, repo: Path = ROOT) -> str:
    proc = subprocess.run(['git', '-C', str(repo), 'hash-object', '--', str(path)],
                          capture_output=True, text=True, timeout=12, check=False)
    need(proc.returncode == 0 and re.fullmatch('[0-9a-f]{40}\\n?', proc.stdout) is not None,
         'Cannot hash source member: ' + str(path))
    return proc.stdout.strip()


# SFDT12-R2 explicit exception: one exact, reviewed npm package manager pin.
# Reconstruct the old Git blob and compare with the original SFDT11 frozen source.
# This is not an allowlist for any other package/script/release policy drift.
def verify_web_manager_amendment(amended: bytes) -> None:
    line = b'  "packageManager": "npm@10.9.4",\n'
    need(amended.count(line) == 1, 'Missing/duplicate reviewed npm pin')
    need(b'  "private": true,\n' + line in amended,
         'Reviewed npm pin moved from approved location')
    addition = b' && npm run lint && npm run build && npm run release:check && python3 scripts/ci_native_pytest_junit.py'
    need(amended.count(addition) == 1, 'Missing/duplicate reviewed SFDT19 native Web test suffix')
    old_package = amended.replace(addition, b'', 1)
    old_blob = sha1(b'blob ' + str(len(old_package)).encode('ascii') + b'\x00' + old_package).hexdigest()
    need(old_blob == 'e05e557473c267a52dd671a074b815b30512349f',
         'SFDT19 change includes unauthorized Web package mutations')
    previous = old_package.replace(line, b'', 1)
    blob = sha1(b'blob ' + str(len(previous)).encode('ascii') + b'\x00' + previous).hexdigest()
    need(blob == '6ec3e0f9ff815d3649f48f3eb090d9fb97012718',
         'Npm pin amendment includes changes beyond the reviewed insertion')


def verify_frozen(observed: dict[str, str], *, expected: dict[str,str] = FROZEN_BLOBS) -> None:
    need(set(observed) == set(expected), 'Reviewed source inventory not complete')
    for path, digest in expected.items():
        need(observed[path] == digest, 'Reviewed source changed: ' + path)


def policy_workflow(text: str, *, rollup: bool) -> None:
    need('  contents: read\n' in text, 'Missing read-only contents permission')
    need(not any(s in text for s in ('id-token: write', 'actions: write', 'secrets.',
           'environment: studyflow-signing', 'gh release', 'npm publish',
           'gh pr merge', 'git push', 'continue-on-error:')),
         'Unreviewed privilege or failure-suppression in SFDT11 workflow')
    if rollup:
        need('  workflow_dispatch:\n' in text and '  actions: read\n' in text,
             'Rollup must be manually dispatched with actions:read')
        need(not any('  '+x+':' in text for x in ('push','pull_request','schedule')),
             'Rollup gained automatic or scheduled trigger')
    else:
        need('  pull_request:\n' in text and '  push:\n' in text and '    branches: [main]\n' in text,
             'Source contract must run on PR and main push')
        need('  actions: read\n' not in text,
             'Source-only PR/push contract must not acquire actions permission')
        need('  workflow_dispatch:\n' not in text,
             'Source-only PR/push contract must not be a dispatch endpoint')
    need('actions/checkout@d23441a48e516b6c34aea4fa41551a30e30af803' in text,
         'Unpinned checkout in SFDT11 workflow')


def provider_truth(cfg: dict) -> dict:
    targets = cfg.get('targets') or {}
    android = targets.get('studyflow_android', {}).get('jobs', {})
    desktop = targets.get('studyflow_desktop', {}).get('jobs', {})
    web = android.get('sfdt08-web-full')
    desk = desktop.get('sfdt08-desktop-fast')
    need(isinstance(web,dict) and isinstance(desk,dict), 'Missing reviewed Web/Desktop bridge jobs')
    need(web.get('metadata',{}).get('ci_toolchain') == 'python-shell' and
         desk.get('metadata',{}).get('ci_toolchain') == 'python-shell',
         'Web/Desktop bridge incorrectly promoted to first-class provider')
    need(web.get('command') == ['python3', 'scripts/sfdt08_hosted_bridge.py', 'web-full'] and
         desk.get('command') == ['python3', 'scripts/sfdt08_hosted_bridge.py', 'desktop-fast'],
         'Web/Desktop reviewed execution bridge changed')
    phone = android.get('sfdt08-phone-release-jvm') or {}
    need(phone.get('metadata',{}).get('ci_toolchain') == 'android-gradle' and
         phone.get('gradle_tasks') == [':app:testReleaseUnitTest'],
         'First-class Android unsigned Release JVM task changed')
    return {'web': 'reviewed-python-shell-bridge-not-first-class-node',
            'desktop': 'reviewed-python-shell-bridge-not-first-class-jvm',
            'phone_release_jvm': 'first-class-android-gradle',
            'provider_cutover': False}


def source_check(root: Path = ROOT) -> dict:
    need(root.is_dir() and not root.is_symlink(), 'Unreviewed source root')
    observed: dict[str,str] = {}
    for rel in FROZEN_BLOBS:
        path = root / rel
        need(path.is_file() and not path.is_symlink(), 'Missing/symlinked frozen source: ' + rel)
        observed[rel] = git_blob(path, repo=root)
    verify_frozen(observed)
    verify_web_manager_amendment((root / 'web/package.json').read_bytes())
    for rel in (*NEW_WORKFLOWS, *NEW_SCRIPTS):
        path = root / rel
        need(path.is_file() and not path.is_symlink(), 'Missing/symlinked SFDT11 source: ' + rel)
    for index, rel in enumerate(NEW_WORKFLOWS):
        policy_workflow((root / rel).read_text(encoding='utf-8'), rollup=index == 1)
    cfg = tomllib.loads((root / '.devtool.toml').read_text(encoding='utf-8'))
    providers = provider_truth(cfg)
    need(DEVTOOL in (root / '.github/workflows/studyflow-devtool-sfdt10-debug-shadow.yml').read_text(),
         'Native Android pinned Devtool runtime drifted')
    return {'kind': 'studyflow.sfdt11.source-boundary', 'status': 'SOURCE_POLICY_PASS',
            'frozen_members': len(observed), 'providers': providers,
            'legacy_ci_required': True, 'cutover_authorized': False,
            'release_qualified': False, 'device_qualified': False}


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument('--source-check', action='store_true', required=True)
    args = ap.parse_args()
    try:
        report = source_check()
        print('SFDT11_SOURCE_POLICY_PASS:', report['frozen_members'],
              'unchanged reviewed files; legacy rollback bridges pinned; new native providers require hosted qualification')
    except (PolicyError, OSError, ValueError, TypeError, KeyError,
            subprocess.TimeoutExpired) as exc:
        print('SFDT11_SOURCE_POLICY_REFUSED:', str(exc)[:360], file=sys.stderr)
        return 2
    return 0

if __name__ == '__main__':
    raise SystemExit(main())
