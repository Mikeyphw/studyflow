#!/usr/bin/env python3
"""Safe offline SFDT22 receipt triage. Never display raw Android data or unknown JSON keys.

Usage: python3 -B scripts/sfdt22_failure_triage.py SFDT22_EVIDENCE.json
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import sys

POINTS = ('after_create', 'after_subject', 'after_topic', 'session_route', 'before_session_start')
STATES = frozenset(('present', 'missing', 'lookup_failed', 'ambiguous', 'matched', 'mismatched'))
PHASES = frozenset(('host', 'device', 'fresh', 'apks', 'install', 'installed_apk',
                    'plan', 'subjects', 'session', 'practice', 'force_stop',
                    'post_restart', 'screenshots', 'complete'))


def safe_token(value: object, allowed: set[str] | frozenset[str], default: str = 'unavailable') -> str:
    return value if isinstance(value, str) and value in allowed else default


def report(data: dict) -> str:
    if not isinstance(data, dict):
        raise ValueError('receipt root must be an object')
    raw = data.get('boundaries') or []
    if not isinstance(raw, list) or len(raw) > len(POINTS):
        raise ValueError('invalid bounded checkpoint count')
    boundaries = []
    for index, record in enumerate(raw):
        if (not isinstance(record, dict) or record.get('point') != POINTS[index] or
                not isinstance(record.get('state'), str) or record.get('state') not in STATES):
            raise ValueError('invalid checkpoint ordering/category')
        boundaries.append((record['point'], record['state']))
    ok = {'present', 'matched'}
    good = [p for p, s in boundaries if s in ok]
    bad = [(p, s) for p, s in boundaries if s not in ok]
    result = safe_token(data.get('overall'),
                        {'NOT_RUN', 'FAILED_OR_NOT_RUN', 'PASS_HOSTED_REAL_STUDY_JOURNEYS',
                         'DIAGNOSTIC_PASS_NOT_RELEASE_QUALIFIED'}, 'unknown')
    phase = safe_token(data.get('failure_phase'), PHASES)
    reason_allowed = {'unavailable', 'session_start_plan_settings_missing',
                      'session_start_plan_settings_lookup_failed', 'session_start_persistence_failed',
                      'session_start_failed', 'compose_wait_timeout', 'test_failed',
                      'unexpected_runner_status', 'runner_nonzero_exit',
                      'boundary_session_route_mismatched'} | {
                          'boundary_' + p + '_' + s for p in POINTS
                          for s in ('missing', 'lookup_failed', 'ambiguous', 'mismatched')}
    reason = safe_token(data.get('failure_reason_code'), reason_allowed)
    lines = [
        'SFDT22_SAFE_TRIAGE',
        'overall=' + result,
        'release_qualified=false',
        'full_hosted_gate=' + ('passed' if result == 'PASS_HOSTED_REAL_STUDY_JOURNEYS' else 'not_passed'),
        'failure_phase=' + phase,
        'reason=' + reason,
        'last_known_good=' + (good[-1] if good else 'none'),
        'first_known_bad=' + (bad[0][0] if bad else 'none'),
    ]
    for p, state in boundaries:
        lines.append('checkpoint.' + p + '=' + state)
    if bad:
        lines.append('localization=check_production_state_transition_between_last_known_good_and_first_known_bad')
    elif result == 'FAILED_OR_NOT_RUN':
        lines.append('localization=missing_checkpoint_or_failure_not_yet_instrumented')
    else:
        lines.append('localization=no_failed_checkpoints')
    lines.append('note=No raw logs, plan IDs, names, database contents, or runtime stack traces inspected')
    return '\n'.join(lines)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('receipt', type=Path)
    args = parser.parse_args()
    try:
        target = args.receipt
        if target.is_symlink() or not target.is_file() or target.stat().st_size > 20_000:
            raise ValueError('missing, symlinked or oversized receipt')
        data = json.loads(target.read_text(encoding='utf-8'))
        print(report(data))
        return 0
    except (OSError, ValueError, UnicodeError, TypeError) as exc:
        print('SFDT22_SAFE_TRIAGE_REFUSED: ' + type(exc).__name__, file=sys.stderr)
        return 2


if __name__ == '__main__':
    raise SystemExit(main())
