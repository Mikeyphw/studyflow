#!/usr/bin/env python3
"""SFDT-07 reviewed Python-provider adapter: run the real Web pytest suite.

A pinned script is necessary because Devtool's current Node provider rejects
StudyFlow's custom npm composite scripts and Web's nested project root. This
runs ONLY pytest, not the npm test/lint/build/package pipeline. No local run.
"""
from __future__ import annotations

import os
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]


def main() -> int:
    if (os.environ.get('GITHUB_ACTIONS') != 'true' or
            os.environ.get('RUNNER_OS') != 'Linux' or
            os.environ.get('RUNNER_ARCH') != 'X64'):
        print('SFDT07_WEB_REFUSED: GitHub Linux runner required', file=sys.stderr)
        return 2
    temp = os.environ.get('RUNNER_TEMP', '')
    if not temp or Path(temp).resolve().is_relative_to(ROOT.resolve()):
        print('SFDT07_WEB_REFUSED: external runner temp required', file=sys.stderr)
        return 2
    report_dir = Path(temp).resolve() / 'sfdt07'
    report_dir.mkdir(parents=True, exist_ok=True)
    report = report_dir / 'web-pytest-junit.xml'
    if report.exists() or report.is_symlink():
        print('SFDT07_WEB_REFUSED: existing test report', file=sys.stderr)
        return 2
    cmd = [sys.executable, '-m', 'pytest', 'tests', '-q', '--junitxml=' + str(report)]
    result = subprocess.run(cmd, cwd=ROOT / 'web', check=False)
    if result.returncode != 0:
        print(f'SFDT07_WEB_PYTEST_FAILED: {result.returncode}', file=sys.stderr)
        return result.returncode or 1
    if not report.is_file() or report.is_symlink() or not report.stat().st_size:
        print('SFDT07_WEB_REFUSED: pytest succeeded without JUnit report', file=sys.stderr)
        return 2
    print('SFDT07_WEB_PYTEST_HOSTED_PASS: JUnit saved outside checkout')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
