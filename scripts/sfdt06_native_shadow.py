#!/usr/bin/env python3
"""Run only the reviewed 4-node source workflow through Devtool's actual EXO DAG.

Only a manually dispatched GitHub Actions Linux job can execute. Never Gradle,
signing, or substitute standalone ad-hoc shell jobs for native run_node.
"""
from __future__ import annotations
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
from datetime import datetime, timezone

ROOT = Path(__file__).resolve().parents[1]
NODE_NAMES = {'ci-actions-contract', 'ci-source-contract', 'ci-sfdt-source-audit', 'ci-github-gradle-policy'}
SHA40 = re.compile(r'[a-f0-9]{40}\Z')


def _git(root: Path, *args: str) -> str:
    return subprocess.run(['git','-C',str(root),*args], text=True, capture_output=True,
                          timeout=15, check=True).stdout.strip()


def _identity(env: dict[str, str], root: Path) -> dict:
    if (env.get('GITHUB_ACTIONS') != 'true' or env.get('GITHUB_EVENT_NAME') != 'workflow_dispatch'
        or env.get('CI') != 'true' or env.get('RUNNER_OS') != 'Linux'
        or env.get('RUNNER_ARCH') != 'X64'):
        raise ValueError('Source/host/dispatch/run identity not authorized for native shadow')
    head = _git(root, 'rev-parse', 'HEAD')
    tree = _git(root, 'rev-parse', 'HEAD^{tree}')
    expected = env.get('EXPECTED_SHA', '')
    runtime = env.get('DEVTOOL_SOURCE_SHA', '')
    if (env.get('GITHUB_ACTIONS') != 'true' or env.get('GITHUB_EVENT_NAME') != 'workflow_dispatch'
        or env.get('CI') != 'true' or env.get('RUNNER_OS') != 'Linux'
        or env.get('RUNNER_ARCH') != 'X64' or env.get('GITHUB_WORKSPACE', '') != str(root)
        or not SHA40.fullmatch(expected) or not SHA40.fullmatch(runtime)
        or env.get('GITHUB_SHA', '') != head or expected != head or not SHA40.fullmatch(tree)
        or not env.get('GITHUB_RUN_ID','').isdecimal() or not env.get('GITHUB_RUN_ATTEMPT','').isdecimal()
        or int(env.get('GITHUB_RUN_ID', '0')) < 1 or int(env.get('GITHUB_RUN_ATTEMPT', '0')) < 1):
        raise ValueError('Source/host/dispatch/run identity not authorized for native shadow')
    if _git(root, 'status', '--porcelain', '--untracked-files=normal'):
        raise ValueError('Consumer source is not clean')
    return {'source_commit': head, 'source_tree': tree, 'runtime_commit': runtime,
            'run_id': int(env['GITHUB_RUN_ID']), 'attempt': int(env['GITHUB_RUN_ATTEMPT']),
            'repository': env.get('GITHUB_REPOSITORY', ''),
            'qualified_release': False, 'legacy_equivalent': False}


def _select(graph: dict) -> list[dict]:
    jobs = graph.get('jobs', [])
    if not isinstance(jobs, list) or len(jobs) != 4:
        raise ValueError('Native source graph must have exactly four EXO nodes')
    keys = [row.get('job') for row in jobs]
    if set(keys) != NODE_NAMES or len(set(keys)) != 4:
        raise ValueError('Native graph source identity differs from reviewed jobs')
    for row in jobs:
        if (row.get('provider') != 'python-shell' or row.get('outputs') or
            row.get('runner') != 'ubuntu-24.04' or row.get('handoffs')):
            raise ValueError('Native source node wants unsupported provider/output/runner')
    return jobs


def execute(root: Path, out: Path, env: dict[str, str] | None = None) -> dict:
    env = dict(os.environ if env is None else env)
    out = out.resolve()
    if out.is_relative_to(root.resolve()) or out.is_symlink():
        raise ValueError('Shadow receipt must be outside the consumer repository')
    from devtool.actions_graph_export import compile_graph, run_node
    record = {'schema_version': 1, 'kind': 'studyflow.sfdt06.native-source-shadow',
              'status': 'FAILED_INCOMPLETE', 'source': {}, 'nodes': [],
              'test_report_verified': False, 'legacy_equivalent': False,
              'signing_qualified': False, 'release_qualified': False,
              'completed_at': None}
    try:
        identity = _identity(env, root)
        record['source'] = identity
        graph = compile_graph(root, target='studyflow_android', workflow='ci-source',
                              devtool_sha=identity['runtime_commit'])
        jobs = _select(graph)
        if graph.get('consumer_commit') != identity['source_commit'] or graph.get('runtime_sha') != identity['runtime_commit']:
            raise ValueError('Frozen native graph differs from consumer or Devtool source')
        record['graph_sha256'] = graph['graph_sha256']
        runner_temp = Path(env['RUNNER_TEMP']).resolve()
        if not runner_temp.is_dir() or runner_temp.is_relative_to(root.resolve()):
            raise ValueError('Runner temporary directory unavailable or inside checkout')
        for row in jobs:
            result = run_node(root, target='studyflow_android', workflow='ci-source',
                              devtool_sha=identity['runtime_commit'], graph_sha=graph['graph_sha256'],
                              job_id=row['id'], inputs=runner_temp/'rm06-inputs',
                              outputs=runner_temp/'rm06-output')
            node_evidence = result.get('typed_node_evidence', {})
            if (result.get('status') != 'LOCAL_JOB_SUCCEEDED' or
                node_evidence.get('source_commit') != identity['source_commit'] or
                node_evidence.get('graph_sha256') != graph['graph_sha256'] or
                node_evidence.get('job_id') != row['id'] or
                node_evidence.get('release_qualified') is not False):
                raise ValueError('Native source node missing its bound success receipt')
            record['nodes'].append({'id': row['id'], 'job': row['job'], 'status': 'NATIVE_NODE_PASSED',
                                    'evidence_sha256': node_evidence['receipt_sha256']})
        record['status'] = 'NATIVE_SOURCE_SHADOW_PASSED'
        return record
    except Exception as exc:
        record['status'] = 'FAILED_INCOMPLETE'
        record['error'] = f'{type(exc).__name__}: {str(exc)[:240]}'
        raise
    finally:
        record['completed_at'] = datetime.now(timezone.utc).isoformat()
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(json.dumps(record, sort_keys=True, indent=2) + '\n', encoding='utf-8')


def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument('--execute', action='store_true')
    p.add_argument('--out', required=True, type=Path)
    opts = p.parse_args()
    if not opts.execute:
        print('SFDT06_REFUSED: --execute is GitHub-hosted only', file=sys.stderr)
        return 2
    try:
        record = execute(ROOT, opts.out)
    except Exception as exc:
        print(f'SFDT06_NATIVE_REFUSED: {type(exc).__name__}: {str(exc)[:180]}', file=sys.stderr)
        return 2
    print(f"SFDT06_NATIVE_SOURCE_SHADOW_PASS: {len(record['nodes'])} EXO nodes, same GitHub source/run, not legacy equivalence")
    return 0

if __name__ == '__main__':
    raise SystemExit(main())
