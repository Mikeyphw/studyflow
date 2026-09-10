#!/usr/bin/env python3
from pathlib import Path
import json
import sys

ROOT = Path(__file__).resolve().parents[1]

REQUIRED = [
    'core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowAccountBackupValidation.kt',
    'core/link/src/test/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowAccountBackupValidationTest.kt',
    'core/link/src/test/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowAccountBackupGoldenTest.kt',
    'app/src/main/java/com/mikeyphw/studyflow/link/AndroidStudyFlowAccountBackupBridge.kt',
    'desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopAccountBackupValidationController.kt',
    'app-tv/src/main/java/com/mikeyphw/studyflow/tv/link/StudyFlowTvAccountBackupReceiver.kt',
    'web/src/sflp_link.ts',
    'web/runtime/sflp_link.js',
    'web/dist/runtime/sflp_link.js',
    'web/tests/test_sflp22_23_account_backup_validation.py',
    'protocol/sflp/v1/sflp-account-backup-release.schema.json',
    'protocol/sflp/v1/golden/account-backup-release-plan.json',
    'core/link/src/test/resources/sflp/v1/account-backup-release-plan.json',
    'SFLP22_23_ACCOUNT_BACKUP_VALIDATION.md',
    'docs/architecture/SFLP22_23_ACCOUNT_BACKUP_VALIDATION.md',
]

NEEDLES = {
    'core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowAccountBackupValidation.kt': [
        'account.handoff_offer',
        'backup.restore_plan',
        'release.validation_plan',
        'StudyFlowSflp23ReleaseGate',
        'verifySflp22Sflp23AccountBackupValidation',
        'tvClass && purpose != StudyFlowAccountHandoffPurpose.AUDIT',
    ],
    'core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowDomainOperation.kt': [
        'account.handoff_accepted',
        'backup.bundle_offered',
        'backup.restore_planned',
        'release.validation_recorded',
    ],
    'build.gradle.kts': [
        'verifySflp22Sflp23AccountBackupValidation',
        'verifySflp20Sflp21ShareImportConflict',
        'scripts/verify_sflp22_23_account_backup_validation.py',
    ],
    'web/runtime/sflp_link.js': [
        'StudyFlowAccountBackupReleaseRuntime',
        'exports.StudyFlowAccountBackupReleaseRuntime',
        'tv_restore_forbidden',
        'verifySflp22Sflp23AccountBackupValidation',
    ],
    'web/src/sflp_link.ts': [
        'export class StudyFlowAccountBackupReleaseRuntime',
        'StudyFlowAccountHandoffPurpose',
        'StudyFlowBackupRestoreMode',
    ],
    'README.md': ['SFLP22/SFLP23 account backup and release validation'],
    'CHANGELOG.md': ['SFLP22-23'],
    'docs/product/CROSS_PLATFORM_CAPABILITY_MATRIX.md': ['SFLP22/SFLP23 account backup and validation capability rows'],
}


def fail(message: str) -> None:
    print(f'SFLP22_23_ACCOUNT_BACKUP_VALIDATION: FAIL\n- {message}', file=sys.stderr)
    raise SystemExit(1)

for rel in REQUIRED:
    if not (ROOT / rel).exists():
        fail(f'missing {rel}')

for rel, needles in NEEDLES.items():
    text = (ROOT / rel).read_text(encoding='utf-8')
    missing = [needle for needle in needles if needle not in text]
    if missing:
        fail(f'{rel} missing {missing}')

schema = json.loads((ROOT / 'protocol/sflp/v1/sflp-account-backup-release.schema.json').read_text(encoding='utf-8'))
wire_names = set(schema['properties']['name']['enum'])
for wire_name in [
    'account.handoff_offer',
    'account.handoff_accept',
    'account.handoff_reject',
    'backup.bundle_offer',
    'backup.restore_plan',
    'backup.restore_ack',
    'release.validation_plan',
    'release.validation_result',
]:
    if wire_name not in wire_names:
        fail(f'schema missing {wire_name}')

golden = json.loads((ROOT / 'protocol/sflp/v1/golden/account-backup-release-plan.json').read_text(encoding='utf-8'))
resource = json.loads((ROOT / 'core/link/src/test/resources/sflp/v1/account-backup-release-plan.json').read_text(encoding='utf-8'))
if golden != resource:
    fail('protocol golden and core test resource diverged')
required_tasks = golden['payload']['requiredTasks']
if required_tasks[0] != 'verifySflp22Sflp23AccountBackupValidation':
    fail('final release gate must start with SFLP22/SFLP23 verifier')
if ':app:testReleaseUnitTest' not in required_tasks:
    fail('final release gate missing Android release unit tests')

build = (ROOT / 'build.gradle.kts').read_text(encoding='utf-8')
sflp20_marker = 'tasks.register<Exec>("verifySflp20Sflp21ShareImportConflict") {'
sflp20_start = build.find(sflp20_marker)
if sflp20_start < 0:
    fail('missing verifySflp20Sflp21ShareImportConflict task definition')
sflp20_end = build.find('\n}', sflp20_start)
if sflp20_end < 0:
    fail('could not isolate verifySflp20Sflp21ShareImportConflict task definition')
sflp20_block = build[sflp20_start:sflp20_end]
if '"verifySflp22Sflp23AccountBackupValidation"' in sflp20_block:
    fail('detected inverted dependency cycle from SFLP20/SFLP21 back to SFLP22/SFLP23')

runtime = (ROOT / 'web/runtime/sflp_link.js').read_text(encoding='utf-8')
if 'export class StudyFlowAccountBackupReleaseRuntime' in runtime:
    fail('runtime still mixes CommonJS and ES module class exports')

print('SFLP22_23_ACCOUNT_BACKUP_VALIDATION: PASS')
