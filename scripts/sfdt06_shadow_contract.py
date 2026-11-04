#!/usr/bin/env python3
"""SFDT-06: machine-readable, no-Gradle StudyFlow shadow/legacy coverage contract.

This file can audit/plan; it never runs local Gradle, installs dependencies, signs,
or claims the legacy CI actually passed. Run identity is supplied by GitHub only.
"""
from __future__ import annotations

import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import sys
import tomllib

ROOT = Path(__file__).resolve().parents[1]
SHADOW_WORKFLOW = '.github/workflows/studyflow-devtool-shadow.yml'
LEGACY_WORKFLOW = '.github/workflows/studyflow-ci.yml'
STAGES = {
    'ci-actions-contract': ('scripts/devtool_actions_contract.py', []),
    'ci-source-contract': ('scripts/verify_studyflow_actions.py', ['--source-only']),
    'ci-sfdt-source-audit': ('scripts/sfdt01_source_contract.py', ['--check']),
    'ci-github-gradle-policy': ('scripts/studyflow_ci_gradle_profile.py', ['--check-source']),
}
SHADOW = 'ci-source'
SCOPE_MAP = {
    'source': ('studyflow_android', 'studyflow-ci.yml#source'),
    'web': ('studyflow_web', 'studyflow-ci.yml#web'),
    'desktop': ('studyflow_desktop', 'studyflow-ci.yml#desktop'),
    'phone': ('studyflow_android', 'studyflow-ci.yml#android[app]'),
    'tv': ('studyflow_tv', 'studyflow-ci.yml#android[app-tv]'),
    'quality': ('studyflow_quality', 'studyflow-ci.yml#deep-contracts[quality-contracts]'),
    'parity': ('studyflow_parity', 'studyflow-ci.yml#deep-contracts[parity-contracts]'),
}
DEEP = {
    'android-contracts': 'studyflow_android',
    'tv-contracts': 'studyflow_tv',
    'desktop-contracts': 'studyflow_desktop',
    'quality-contracts': 'studyflow_quality',
    'parity-contracts': 'studyflow_parity',
}
SHA40 = re.compile(r'[a-f0-9]{40}\Z')

class ShadowContractError(ValueError):
    pass


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ShadowContractError(message)


def _regular(root: Path, rel: str) -> Path:
    path = root / rel
    require(path.is_file() and not path.is_symlink() and path.resolve().is_relative_to(root.resolve()),
            f'Unreviewed or missing source: {rel}')
    return path


def audit(root: Path) -> dict:
    """No network or Gradle. Report that every old lane remains required."""
    config = tomllib.loads(_regular(root, '.devtool.toml').read_text(encoding='utf-8'))
    targets = config.get('targets', {})
    required_targets = {'studyflow_android', 'studyflow_desktop', 'studyflow_web', 'studyflow_tv',
                        'studyflow_release', 'studyflow_tv_release', 'studyflow_quality', 'studyflow_parity'}
    require(set(targets) == required_targets, 'StudyFlow eight-target mapping missing or extended')
    jobs = targets['studyflow_android'].get('jobs', {})
    steps = targets['studyflow_android'].get('workflows', {}).get(SHADOW)
    require(isinstance(steps, list) and len(steps) == 4, 'Native source DAG must preserve four reviewed stages')
    recorded = set()
    for step in steps:
        name = str(step.get('ref', ''))[4:] if isinstance(step, dict) else ''
        require(name in STAGES and name not in recorded, 'Native source DAG contains unknown or duplicate job')
        recorded.add(name)
    require(recorded == set(STAGES), 'Native source DAG lost existing job')
    fingerprints = {}
    for name, (rel, args) in STAGES.items():
        job = jobs.get(name, {})
        require(job.get('command') == ['python3', rel, *args], f'Source argv drift: {name}')
        require(not job.get('outputs') and not job.get('gradle_tasks') and not job.get('shell_command'),
                f'Source stage cannot write outputs or invoke Gradle: {name}')
        meta = job.get('metadata', {})
        digest = hashlib.sha256(_regular(root, rel).read_bytes()).hexdigest()
        require(meta.get('read_only') is True and meta.get('ci_python_script_sha256') == digest
                and meta.get('ci_python_version') == '3.12'
                and meta.get('ci_python_script_args', []) == args,
                f'Native Python provider SHA/arguments mismatch: {name}')
        fingerprints[name] = digest
    legacy = _regular(root, LEGACY_WORKFLOW).read_text(encoding='utf-8')
    require('  source:\n' in legacy and '  web:\n' in legacy and '  desktop:\n' in legacy
            and '  android:\n' in legacy and '  deep-contracts:\n' in legacy,
            'Existing fast/deep CI jobs may not be removed during shadow migration')
    for task in DEEP:
        require(f'          - {task}' in legacy, f'Existing deep matrix omitted {task}')
    require('environment: studyflow-signing' in _regular(root, '.github/workflows/studyflow-hosted-sign.yml').read_text(encoding='utf-8'),
            'Protected signer boundary changed')
    shadow = _regular(root, SHADOW_WORKFLOW).read_text(encoding='utf-8')
    required = [
        'workflow_dispatch:', 'expected_sha:', 'contents: read',
        'test "$(git rev-parse HEAD)" = "$GITHUB_SHA"',
        'test "$EXPECTED_SHA" = "$GITHUB_SHA"',
        'devtool.actions_graph_export', 'scripts/sfdt06_native_shadow.py',
        'if: always()', 'if-no-files-found: error',
        'persist-credentials: false', 'retention-days: 14',
    ]
    require(all(t in shadow for t in required), 'Shadow workflow lost source/DAG/receipt integrity controls')
    require('pull_request:' not in shadow and 'push:' not in shadow and 'schedule:' not in shadow,
            'Shadow must be manual-only until hosted equivalence is accepted')
    require('continue-on-error:' not in shadow and 'permissions:\n  contents: read\n' in shadow,
            'Shadow workflow cannot hide errors or acquire signing permissions')
    require(not any(t in shadow for t in ('secrets.', 'id-token: write', 'environment: studyflow-signing', './gradlew',
                                         'devtool actions android release', 'release-sign')), 'Shadow lacks signing/Gradle authority')
    # The existing deep profiles remain canonical task-owner inventories; no invocation here.
    module_path = _regular(root, 'scripts/studyflow_ci_gradle_profile.py')
    spec = importlib.util.spec_from_file_location('sfdt06_legacy_profiles', module_path)
    require(spec is not None and spec.loader is not None, 'Cannot load existing deep profile owner')
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    previous_root = module.ROOT
    module.ROOT = root
    try:
        profiles = {name: module.profile_plan(name) for name in DEEP}
    finally:
        module.ROOT = previous_root
    for name, plan in profiles.items():
        require(plan.get('target') == DEEP[name] and plan.get('tasks') and plan.get('qualified_release') is False,
                f'Deep task owner/protection drift: {name}')
    return {
        'schema_version': 1,
        'kind': 'studyflow.sfdt06.shadow-coverage',
        'source_only': True,
        'gradle_executed': False,
        'shadow_native_source': 'SOURCE_CONFIG_VERIFIED_NOT_HOSTED',
        'legacy_hosted_results': 'NOT_OBSERVED',
        'cutover_authorized': False,
        'release_qualified': False,
        'source_nodes': [step['ref'][4:] for step in steps],
        'source_script_sha256': fingerprints,
        'profiles': {name: {'target': plan['target'], 'operation': plan['operation'],
                            'tasks': plan['tasks'], 'task_count': len(plan['tasks']),
                            'sha256': hashlib.sha256(json.dumps(plan['tasks'], sort_keys=True).encode()).hexdigest(),
                            'state': 'LEGACY_ONLY_NOT_EQUIVALENT'} for name, plan in profiles.items()},
        'scopes': {name: {'target': target, 'legacy': old,
                          'shadow': 'NATIVE_SOURCE_ONLY' if name == 'source' else 'NOT_ADOPTED',
                          'legacy_required': True} for name, (target, old) in SCOPE_MAP.items()},
    }


def main(argv: list[str] | None = None) -> int:
    p = argparse.ArgumentParser()
    p.add_argument('--check', action='store_true')
    p.add_argument('--json', action='store_true')
    p.add_argument('--out', type=Path)
    opts = p.parse_args(argv)
    try:
        record = audit(ROOT)
        if opts.out:
            dest = opts.out.resolve()
            require(not dest.is_relative_to(ROOT.resolve()), 'Coverage output must remain outside tracked checkout')
            require(not dest.is_symlink(), 'Symlinked coverage output refused')
            dest.parent.mkdir(parents=True, exist_ok=True)
            dest.write_text(json.dumps(record, sort_keys=True, indent=2) + '\n', encoding='utf-8')
    except (ShadowContractError, OSError, ValueError, TypeError, KeyError) as exc:
        print(f'SFDT06_SOURCE_REFUSED: {exc}', file=sys.stderr)
        return 2
    if opts.json:
        print(json.dumps(record, sort_keys=True))
    else:
        print('SFDT06_COVERAGE_SOURCE_PASS: 4 native source nodes; 7 legacy scopes; 5 deep profiles preserved; Gradle NOT_RUN')
    return 0

if __name__ == '__main__':
    raise SystemExit(main())
