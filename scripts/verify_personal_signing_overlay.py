#!/usr/bin/env python3
"""Fast source contract + executable regression suite for StudyFlow personal signing."""
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
required = [
    'app/build.gradle.kts', 'app-tv/build.gradle.kts',
    'tools/setup-personal-signing.sh', 'tools/print-personal-signing-fingerprint.sh',
    'gradle/studyflow-personal-signing.properties.example',
    'scripts/test_personal_signing_overlay.py', 'STUDYFLOW_PERSONAL_SIGNING.md', '.gitignore',
]
missing = [p for p in required if not (ROOT / p).is_file()]
if missing:
    print('STUDYFLOW_PERSONAL_SIGNING: FAIL')
    for p in missing: print(f'- missing {p}')
    raise SystemExit(1)

result = subprocess.run(
    [sys.executable, str(ROOT / 'scripts/test_personal_signing_overlay.py')],
    cwd=ROOT,
    check=False,
)
if result.returncode:
    print('STUDYFLOW_PERSONAL_SIGNING: FAIL')
    raise SystemExit(result.returncode)
print('STUDYFLOW_PERSONAL_SIGNING: PASS')
