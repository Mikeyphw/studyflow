#!/usr/bin/env python3
"""Produce real pytest JUnit bytes for the Devtool-owned native Node test.

The producer only writes a fresh report after the actual Web regression suite;
no synthetic success, skipped-suite promotion, or report reuse is accepted.
"""
from pathlib import Path
import shutil
import sys
import xml.etree.ElementTree as ET

import pytest

ROOT = Path(__file__).resolve().parents[1]
DIST = ROOT / 'dist' / 'test-results.xml'
NATIVE = ROOT / 'build' / 'test-results' / 'native-node'


def main() -> int:
    for path in (DIST, NATIVE):
        if path.exists() or path.is_symlink():
            print('SFDT19_WEB_JUNIT_REFUSED: stale report path: ' + str(path), file=sys.stderr)
            return 2
    DIST.parent.mkdir(parents=True, exist_ok=True)
    status = pytest.main(['-q', str(ROOT / 'tests'), '--junitxml=' + str(DIST)])
    if status != 0:
        return int(status)
    if not DIST.is_file() or DIST.is_symlink():
        print('SFDT19_WEB_JUNIT_REFUSED: no pytest report', file=sys.stderr)
        return 2
    try:
        root = ET.parse(DIST).getroot()
        cases = list(root.iter('testcase'))
        if not cases or any(any(child.tag in ('failure', 'error') for child in case) for case in cases):
            raise ValueError('zero/failed pytest cases')
        NATIVE.mkdir(parents=True, exist_ok=False)
        dest = NATIVE / 'web-pytest-junit.xml'
        with DIST.open('rb') as inp, dest.open('xb') as out:
            shutil.copyfileobj(inp, out)
        if dest.stat().st_size != DIST.stat().st_size:
            raise ValueError('JUnit native copy byte count mismatch')
    except (ET.ParseError, OSError, ValueError) as exc:
        print('SFDT19_WEB_JUNIT_REFUSED: ' + str(exc), file=sys.stderr)
        return 2
    print('SFDT19_WEB_NATIVE_JUNIT_PASS: executed=', len(cases))
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
