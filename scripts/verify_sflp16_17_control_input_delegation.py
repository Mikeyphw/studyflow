#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]

REQUIRED = [
    'core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowControlInputDelegation.kt',
    'core/link/src/test/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowControlInputDelegationTest.kt',
    'app/src/main/java/com/mikeyphw/studyflow/link/AndroidStudyFlowControlInputBridge.kt',
    'desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopControlInputDelegationController.kt',
    'app-tv/src/main/java/com/mikeyphw/studyflow/tv/link/StudyFlowTvControlInputReceiver.kt',
    'web/runtime/sflp_link.js',
    'web/src/sflp_link.ts',
    'protocol/sflp/v1/sflp-control-input.schema.json',
    'protocol/sflp/v1/golden/control-command.json',
    'protocol/sflp/v1/golden/input-event.json',
    'SFLP16_17_CONTROL_INPUT_DELEGATION.md',
    'docs/architecture/SFLP16_17_CONTROL_INPUT_DELEGATION.md',
]

NEEDLES = {
    'core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowControlInputDelegation.kt': [
        'StudyFlowControlLease', 'StudyFlowControlCommand', 'StudyFlowInputDelegationOffer',
        'StudyFlowInputEvent', 'bad_lease_token', 'stale_sequence', 'wrong_target',
        'control.command', 'input.delegation_offer', 'input.event', 'WorkspaceWrite !in target.capabilities',
    ],
    'web/runtime/sflp_link.js': [
        'StudyFlowControlInputRuntime', 'rememberControlLease', 'applyControlCommand', 'acceptInputDelegation',
    ],
    'build.gradle.kts': ['verifySflp16Sflp17ControlInputDelegation', 'verifySflp14Sflp15LiveSessionProjection'],
    'docs/roadmap/CROSS_PLATFORM_PARITY_CONNECTIVITY_ROADMAP.md': ['SFLP16 + SFLP17', 'COMPLETE'],
}


def fail(msg):
    print(f'SFLP16_17_CONTROL_INPUT_DELEGATION: FAIL\n- {msg}')
    sys.exit(1)

for rel in REQUIRED:
    if not (ROOT / rel).exists():
        fail(f'missing {rel}')

for rel, needles in NEEDLES.items():
    text = (ROOT / rel).read_text()
    missing = [n for n in needles if n not in text]
    if missing:
        fail(f'{rel} missing {missing}')

schema = (ROOT / 'protocol/sflp/v1/sflp-control-input.schema.json').read_text()
for name in ['control.lease_grant', 'control.command_ack', 'input.event_ack']:
    if name not in schema:
        fail(f'schema missing {name}')

print('SFLP16_17_CONTROL_INPUT_DELEGATION: PASS')
