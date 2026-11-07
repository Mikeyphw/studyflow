#!/usr/bin/env python3
"""SFDT20 operator audit of post-merge main CI and effective required checks.

Read-only: uses the authenticated gh CLI, never modifies branch protection.
"""
from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
from pathlib import Path

REPO = 'Mikeyphw/studyflow'
CI_NAME = 'StudyFlow Devtool CI'
SECONDARY = {
    'StudyFlow SFDT11 immutable source and trust policy':
        'SFDT11 fail-closed source, CI and signing boundaries',
    'StudyFlow CI': 'Actions integration source contract',
}
REQUIRED_CONTEXTS = {'Devtool CI gate', *SECONDARY.values()}
REQUIRED_JOBS = {
    'Devtool source and safety', 'Devtool phone', 'Devtool tv',
    'Devtool desktop', 'Devtool web', 'Devtool CI gate',
}
SHA40 = re.compile(r'[0-9a-f]{40}\Z')


class Refused(ValueError):
    pass


def need(ok: bool, msg: str):
    if not ok:
        raise Refused(msg)


def api(path: str) -> dict | list:
    p = subprocess.run(['gh', 'api', path], capture_output=True, text=True,
                       timeout=45, check=False)
    need(p.returncode == 0,
         'GitHub API unavailable or forbidden for ' + path.split('?', 1)[0] +
         '; verify gh auth status and repository permissions')
    return json.loads(p.stdout)


def audit(ci_runs: dict, jobs: dict, sha: str, protections: dict | None,
          rules: list | None, *, secondary_jobs: dict[str, dict]) -> dict:
    runs = ci_runs.get('workflow_runs', [])
    matches = [r for r in runs if r.get('name') == CI_NAME
               and r.get('event') == 'push' and r.get('head_sha') == sha
               and r.get('head_branch') == 'main']
    need(bool(matches), 'No main push-triggered native Devtool run exists at exact merge SHA')
    last = sorted(matches, key=lambda r: (r.get('run_attempt',0), r.get('id',0)))[-1]
    need(last.get('status') == 'completed' and last.get('conclusion') == 'success',
         'Post-merge native Devtool run did not pass')
    actual = {j.get('name') for j in jobs.get('jobs', [])
              if j.get('status') == 'completed' and j.get('conclusion') == 'success'}
    need(REQUIRED_JOBS <= actual, 'Post-merge CI missing successful source/native/gate jobs')
    for workflow, job_name in SECONDARY.items():
        candidates = [r for r in runs if r.get('name') == workflow and
                      r.get('event') == 'push' and r.get('head_sha') == sha and
                      r.get('head_branch') == 'main']
        need(bool(candidates), 'Missing main push workflow: ' + workflow)
        selected = max(candidates, key=lambda r:(r.get('run_attempt',0),r.get('id',0)))
        need(selected.get('status') == 'completed' and selected.get('conclusion') == 'success',
             'Main push source workflow not green: ' + workflow)
        matching_jobs = secondary_jobs.get(workflow,{}).get('jobs',[])
        need(any(j.get('name') == job_name and j.get('status') == 'completed' and
                 j.get('conclusion') == 'success' for j in matching_jobs),
             'Main push source job not green: ' + job_name)
    expected = 'Devtool CI gate'
    protections = protections or {}
    contexts = set()
    req = protections.get('required_status_checks') or {}
    contexts.update(req.get('contexts') or [])
    contexts.update(c.get('context') for c in req.get('checks') or [] if isinstance(c, dict))
    for rule in rules or []:
        if not isinstance(rule, dict) or rule.get('type') != 'required_status_checks':
            continue
        context_rule = rule.get('parameters') or {}
        contexts.update(c.get('context') for c in context_rule.get('required_status_checks', []) if isinstance(c, dict))
    contexts.discard(None)
    return {'schema': 'studyflow.sfdt20.main-audit.v1', 'source_sha': sha,
            'run_id': last['id'], 'run_attempt': last.get('run_attempt', 1),
            'run_url': last.get('html_url'),
            'qualified_jobs': sorted(REQUIRED_JOBS),
            'secondary_source_checks': dict(SECONDARY),
            'postmerge_main_ci': 'PASS',
            'main_requires_devtool_ci_gate': expected in contexts,
            'main_requires_sfdt11': SECONDARY['StudyFlow SFDT11 immutable source and trust policy'] in contexts,
            'main_requires_legacy_source': SECONDARY['StudyFlow CI'] in contexts,
            'expected_required_contexts': sorted(REQUIRED_CONTEXTS),
            'observed_required_contexts': sorted(contexts),
            'missing_required_contexts': sorted(REQUIRED_CONTEXTS - contexts),
            'protection_state': 'PASS' if REQUIRED_CONTEXTS <= contexts else 'UNVERIFIED_OR_MISSING'}


def main(argv=None) -> int:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--sha', required=True, help='Exact post-merge main commit SHA')
    p.add_argument('--out', type=Path, default=None)
    args = p.parse_args(argv)
    try:
        need(SHA40.fullmatch(args.sha) is not None, 'Invalid SHA')
        repo = api('repos/' + REPO)
        need(repo.get('default_branch') == 'main', 'Repository default branch changed')
        branch = api('repos/' + REPO + '/branches/main')
        need((branch.get('commit') or {}).get('sha') == args.sha,
             'The specified merge SHA is not current main; audit the new main commit instead')
        runs = api('repos/' + REPO + '/actions/runs?head_sha=' + args.sha + '&event=push&per_page=100')
        matching = [r for r in runs.get('workflow_runs', []) if r.get('name') == CI_NAME]
        need(bool(matching), 'No main push native Devtool workflow run found')
        current = max(matching, key=lambda x: (x.get('run_attempt', 0),x.get('id', 0)))
        jobs = api('repos/' + REPO + '/actions/runs/' + str(current['id']) + '/jobs?per_page=100')
        secondary_jobs = {}
        for workflow in SECONDARY:
            candidates = [r for r in runs.get('workflow_runs', []) if r.get('name') == workflow and
                          r.get('event') == 'push' and r.get('head_sha') == args.sha and
                          r.get('head_branch') == 'main']
            need(bool(candidates), 'Missing post-merge source workflow: ' + workflow)
            matched = max(candidates,key=lambda r:(r.get('run_attempt',0),r.get('id',0)))
            secondary_jobs[workflow] = api('repos/' + REPO + '/actions/runs/' + str(matched['id']) + '/jobs?per_page=100')
        # Branch protection may be implemented through rulesets rather than the
        # classic endpoint. Probe both without hiding API failures as success.
        protections = None
        rules = None
        try:
            protections = api('repos/' + REPO + '/branches/main/protection')
        except Refused:
            pass
        try:
            response = api('repos/' + REPO + '/rules/branches/main')
            rules = response if isinstance(response, list) else response.get('rules', [])
        except Refused:
            pass
        report = audit(runs, jobs, args.sha, protections, rules, secondary_jobs=secondary_jobs)
        if args.out:
            need(not args.out.exists() and not args.out.is_symlink(), 'Refuse overwrite of existing audit report')
            with args.out.open('x', encoding='utf-8') as fp:
                json.dump(report, fp, indent=2, sort_keys=True)
                fp.write('\n')
        print(json.dumps(report, indent=2, sort_keys=True))
        # An unavailable protection API is not evidence of protection.
        if report['missing_required_contexts']:
            print('SFDT20_PROTECTION_NOT_PROVEN: missing/undetected required contexts: ' +
                  ', '.join(report['missing_required_contexts']),file=sys.stderr)
            return 3
        print('SFDT20_MAIN_AUDIT_PASS: postmerge CI and gate protection verified')
        return 0
    except (Refused, ValueError, KeyError, TypeError, OSError) as exc:
        print('SFDT20_MAIN_AUDIT_REFUSED:', str(exc)[:360], file=sys.stderr)
        return 2


if __name__ == '__main__':
    raise SystemExit(main())
