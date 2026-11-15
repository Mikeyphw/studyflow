#!/usr/bin/env python3
"""SFDT21 HF32: hosted Gradle APK packaging with bounded, allowlisted diagnostics.

No raw Gradle/Android output enters the uploaded evidence or CI output.
This runner never accepts arbitrary shell commands, changes Gradle tasks,
ignores nonzero exits, builds releases, or claims physical qualification.
"""
from __future__ import annotations
import argparse
from collections import deque
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile

TASKS = (':app:assembleDebug', ':app:assembleDebugAndroidTest')
BUCKETS = (
    ('disk_full', re.compile(r'No space left on device|ENOSPC|disk is full', re.I)),
    ('out_of_memory', re.compile(r'OutOfMemoryError|Java heap space|GC overhead limit exceeded', re.I)),
    ('permission_denied', re.compile(r'AccessDeniedException|Permission denied|EACCES', re.I)),
    ('archive_corrupt', re.compile(r'ZipException|invalid (?:zip|central directory)|corrupt zip', re.I)),
    ('missing_input', re.compile(r'NoSuchFileException|FileNotFoundException|does not exist', re.I)),
    ('daemon_unavailable', re.compile(r'Gradle build daemon disappeared|DaemonDisappearedException', re.I)),
    ('incremental_splitter', re.compile(r'IncrementalSplitterRunnable', re.I)),
    ('package_task', re.compile(r':app:packageDebug\b.*FAILED|Execution failed for task .?:app:packageDebug', re.I)),
)


def classify(lines: str, returncode: int) -> dict:
    """Only fixed enums and booleans are derived from volatile untrusted output."""
    bucket = 'passed' if returncode == 0 else 'unclassified_failure'
    if returncode:
        for candidate, pattern in BUCKETS:
            if pattern.search(lines):
                bucket = candidate
                break
    return {
        'schema': 1,
        'overall': 'PASSED' if returncode == 0 else 'FAILED_OR_NOT_RUN',
        'phase': 'debug_apk_packaging',
        'category': bucket,
        'gradle_exit_nonzero': returncode != 0,
        'stacktrace_present': 'Exception' in lines or 'Caused by:' in lines,
        'package_task_failed': bool(re.search(r':app:packageDebug\s+FAILED|Execution failed for task .?:app:packageDebug', lines)),
        'hosted_emulator_only': True,
        'physical_device_qualified': False,
        'release_qualified': False,
        'publication_allowed': False,
        'raw_logs_retained': False,
    }


def write_receipt(out_dir: Path, data: dict) -> None:
    data_bytes = (json.dumps(data, sort_keys=True, separators=(',', ':')) + '\n').encode('ascii')
    if len(data_bytes) > 2048:
        raise ValueError('SFDT21_HF32_REFUSED_OVERSIZED_DIAGNOSTIC')
    if out_dir.is_symlink() or not out_dir.is_dir():
        raise ValueError('SFDT21_HF32_REFUSED_OUTPUT_DIRECTORY')
    with tempfile.NamedTemporaryFile(dir=out_dir, prefix='.sfdt21-hf32-', delete=False) as file:
        temp = Path(file.name)
        file.write(data_bytes)
        file.flush()
        os.fsync(file.fileno())
    os.replace(temp, out_dir / 'SFDT21_PACKAGE_DIAGNOSTIC.json')


def hosted_preflight(out_dir: Path, tasks: list[str]) -> None:
    if tasks != list(TASKS):
        raise ValueError('SFDT21_HF32_REFUSED_TASK_ALLOWLIST')
    if os.environ.get('GITHUB_ACTIONS') != 'true' or os.environ.get('RUNNER_OS') != 'Linux':
        raise ValueError('SFDT21_HF32_REFUSED_NON_HOSTED_RUNNER')
    if os.environ.get('GITHUB_REPOSITORY') != 'Mikeyphw/studyflow':
        raise ValueError('SFDT21_HF32_REFUSED_REPOSITORY')
    root = os.environ.get('RUNNER_TEMP')
    if not root or not out_dir.resolve().is_relative_to(Path(root).resolve()):
        raise ValueError('SFDT21_HF32_REFUSED_OUTPUT_OUTSIDE_RUNNER_TEMP')
    if out_dir.is_symlink() or not out_dir.is_dir():
        raise ValueError('SFDT21_HF32_REFUSED_OUTPUT_DIRECTORY')
    if not Path('gradlew').is_file():
        raise ValueError('SFDT21_HF32_REFUSED_NO_WRAPPER')


def run(out_dir: Path) -> int:
    cmd = ['./gradlew', '--no-daemon', '--max-workers=2', '--console=plain',
           '--stacktrace', *TASKS]
    tail: deque[str] = deque(maxlen=4000)
    with subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                          text=True, errors='replace', bufsize=1) as proc:
        assert proc.stdout is not None
        for line in proc.stdout:
            # Max bytes per line: do not retain unbounded input from tools.
            tail.append(line[:700])
            # Task progress only; redact the raw exception, paths, stack frames,
            # instrumentation text, credentials, and unknown free-form output.
            if re.match(r'^> Task :app:(?:packageDebug|compileDebugAndroidTestKotlin|assembleDebug)\b', line):
                print('SFDT21_HF32_BUILD_TASK ' + line.split()[2].split(':')[-1], flush=True)
        rc = proc.wait()
    summary = classify(''.join(tail), rc)
    write_receipt(out_dir, summary)
    print('SFDT21_HF32_PACKAGE_' + ('PASS' if rc == 0 else 'REFUSED') +
          ' category=' + summary['category'] +
          ' stacktrace_present=' + str(summary['stacktrace_present']).lower() +
          ' package_task_failed=' + str(summary['package_task_failed']).lower(), flush=True)
    return 0 if rc == 0 else (rc if 1 <= rc <= 125 else 2)


def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument('--output-dir', type=Path, required=True)
    p.add_argument('--task', action='append', required=True)
    args = p.parse_args()
    try:
        hosted_preflight(args.output_dir, args.task)
        return run(args.output_dir)
    except (OSError, ValueError) as e:
        # Do not dump exception strings (which might include file paths).
        print('SFDT21_HF32_REFUSED host_preflight_or_io', flush=True)
        return 2


if __name__ == '__main__':
    sys.exit(main())
