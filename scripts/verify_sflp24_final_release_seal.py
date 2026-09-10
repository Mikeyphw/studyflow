#!/usr/bin/env python3
from pathlib import Path
import json
import re
import sys

ROOT = Path(__file__).resolve().parents[1]

SFLP_TASKS = [
    'verifySflp01ProtocolBaseline',
    'verifySflp02Sflp03JournalReconciliation',
    'verifySflp04Sflp05IdentityTransport',
    'verifySflp06Sflp07TopologyOfflineQueue',
    'verifySflp08Sflp09AndroidDesktopIntegration',
    'verifySflp10Sflp11WebTvGeneralization',
    'verifySflp12Sflp13WebTvHandoff',
    'verifySflp14Sflp15LiveSessionProjection',
    'verifySflp16Sflp17ControlInputDelegation',
    'verifySflp18Sflp19NotificationMediaSync',
    'verifySflp20Sflp21ShareImportConflict',
    'verifySflp22Sflp23AccountBackupValidation',
    'verifySflp24FinalReleaseSeal',
]
FINAL_REQUIRED = [
    'verifySflp24FinalReleaseSeal',
    'verifySflp22Sflp23AccountBackupValidation',
    'verifySflp20Sflp21ShareImportConflict',
    'verifySflp18Sflp19NotificationMediaSync',
    'verifySflp16Sflp17ControlInputDelegation',
    'verifySflp14Sflp15LiveSessionProjection',
    'verifySflp12Sflp13WebTvHandoff',
    'verifySflp10Sflp11WebTvGeneralization',
    'verifySflp08Sflp09AndroidDesktopIntegration',
    'verifySflp06Sflp07TopologyOfflineQueue',
    'verifySflp04Sflp05IdentityTransport',
    'verifySflp02Sflp03JournalReconciliation',
    'verifySflp01ProtocolBaseline',
    ':core:link:test',
    ':app-tv:testDebugUnitTest',
    ':desktopApp:test',
    ':app:testReleaseUnitTest',
]
REQUIRED_FILES = [
    'SFLP01_PROTOCOL_BASELINE.md',
    'SFLP02_03_JOURNAL_RECONCILIATION.md',
    'SFLP04_05_IDENTITY_TRANSPORT.md',
    'SFLP06_07_TOPOLOGY_OFFLINE_QUEUE.md',
    'SFLP08_09_ANDROID_DESKTOP_INTEGRATION.md',
    'SFLP10_11_WEB_TV_GENERALIZATION.md',
    'SFLP12_13_WEB_TV_HANDOFF.md',
    'SFLP14_15_LIVE_SESSION_PROJECTION.md',
    'SFLP16_17_CONTROL_INPUT_DELEGATION.md',
    'SFLP18_19_NOTIFICATION_MEDIA_SYNC.md',
    'SFLP20_21_CLIPBOARD_CONFLICT_REVIEW.md',
    'SFLP22_23_ACCOUNT_BACKUP_VALIDATION.md',
    'SFLP24_FINAL_RELEASE_SEAL.md',
    'docs/architecture/SFLP24_FINAL_RELEASE_SEAL.md',
    'core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowFinalReleaseValidationSeal.kt',
    'core/link/src/test/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowFinalReleaseValidationSealTest.kt',
    'core/link/src/test/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowFinalReleaseGoldenTest.kt',
    'protocol/sflp/v1/sflp-final-release-seal.schema.json',
    'protocol/sflp/v1/golden/final-release-validation-plan.json',
    'core/link/src/test/resources/sflp/v1/final-release-validation-plan.json',
]

def fail(message: str) -> None:
    print(f'SFLP24_FINAL_RELEASE_SEAL: FAIL\n- {message}', file=sys.stderr)
    raise SystemExit(1)

for rel in REQUIRED_FILES:
    if not (ROOT / rel).exists():
        fail(f'missing {rel}')

build = (ROOT / 'build.gradle.kts').read_text(encoding='utf-8')
for task in SFLP_TASKS:
    if task not in build:
        fail(f'build.gradle.kts missing {task}')

check_all = re.search(r'tasks\.register\("checkAll"\) \{.*?\n\}', build, re.S)
if not check_all:
    fail('checkAll task not found')
check_text = check_all.group(0)
for task in SFLP_TASKS:
    if f'"{task}"' not in check_text:
        fail(f'checkAll missing {task}')

if 'tasks.register<Exec>("verifySflp24FinalReleaseSeal")' not in build:
    fail('missing verifySflp24FinalReleaseSeal Gradle task')
if 'dependsOn(\n        "verifySflp22Sflp23AccountBackupValidation"' not in build:
    fail('final seal task must depend on the SFLP22/SFLP23 release gate')
if 'verifySflp24FinalReleaseSeal",\n        "verifySflp22Sflp23AccountBackupValidation' in build:
    fail('detected inverted dependency cycle from SFLP22/SFLP23 back to SFLP24')

toml = (ROOT / '.devtool.toml').read_text(encoding='utf-8')
for task in SFLP_TASKS:
    if f'"{task}"' not in toml:
        fail(f'.devtool.toml test lane missing {task}')
if toml.count('"verifySflp24FinalReleaseSeal"') != 1:
    fail('.devtool.toml must name verifySflp24FinalReleaseSeal exactly once')

golden = json.loads((ROOT / 'protocol/sflp/v1/golden/final-release-validation-plan.json').read_text(encoding='utf-8'))
resource = json.loads((ROOT / 'core/link/src/test/resources/sflp/v1/final-release-validation-plan.json').read_text(encoding='utf-8'))
if golden != resource:
    fail('final release protocol golden diverges from core test resource')
required_tasks = golden['payload']['requiredTasks']
if required_tasks != FINAL_REQUIRED:
    fail('final release golden required task order is not canonical')

schema = json.loads((ROOT / 'protocol/sflp/v1/sflp-final-release-seal.schema.json').read_text(encoding='utf-8'))
if schema['properties']['name']['const'] != 'release.validation_final_seal':
    fail('final release schema has the wrong wire name')

core = (ROOT / 'core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowFinalReleaseValidationSeal.kt').read_text(encoding='utf-8')
for needle in [
    'StudyFlowSflp24FinalReleaseGate',
    'verifySflp24FinalReleaseSeal',
    'StudyFlowSflp23ReleaseGate.RequiredSflpTasks.all',
    'coversAllPlatforms',
]:
    if needle not in core:
        fail(f'final core seal missing {needle}')

runtime = (ROOT / 'web/runtime/sflp_link.js').read_text(encoding='utf-8')
if 'export class ' in runtime:
    fail('web runtime still mixes CommonJS and ES-module class exports')

roadmap = (ROOT / 'docs/roadmap/CROSS_PLATFORM_PARITY_CONNECTIVITY_ROADMAP.md').read_text(encoding='utf-8')
for needle in ['SFLP24', 'FINAL RELEASE SEAL', 'final validation command']:
    if needle not in roadmap:
        fail(f'roadmap missing {needle}')

print('SFLP24_FINAL_RELEASE_SEAL: PASS')
