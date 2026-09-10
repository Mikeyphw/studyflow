#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
REQUIRED = [
    'core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowNotificationMediaSync.kt',
    'core/link/src/test/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowNotificationMediaSyncTest.kt',
    'app/src/main/java/com/mikeyphw/studyflow/link/AndroidStudyFlowNotificationMediaBridge.kt',
    'app/src/test/java/com/mikeyphw/studyflow/StudyFlowAndroidNotificationMediaBridgeTest.kt',
    'desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopNotificationMediaSyncController.kt',
    'desktopApp/src/test/kotlin/com/mikeyphw/studyflow/desktop/DesktopNotificationMediaSyncControllerTest.kt',
    'app-tv/src/main/java/com/mikeyphw/studyflow/tv/link/StudyFlowTvNotificationMediaReceiver.kt',
    'app-tv/src/test/java/com/mikeyphw/studyflow/tv/StudyFlowTvNotificationMediaReceiverTest.kt',
    'web/runtime/sflp_link.js',
    'web/src/sflp_link.ts',
    'web/tests/test_sflp18_19_notification_media.py',
    'protocol/sflp/v1/sflp-notification-media.schema.json',
    'protocol/sflp/v1/golden/notification-delivery.json',
    'protocol/sflp/v1/golden/material-stream-offer.json',
    'SFLP18_19_NOTIFICATION_MEDIA_SYNC.md',
    'docs/architecture/SFLP18_19_NOTIFICATION_MEDIA_SYNC.md',
]
NEEDLES = {
    'core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowNotificationMediaSync.kt': [
        'StudyFlowNotificationPayload', 'StudyFlowNotificationDeliveryAck', 'StudyFlowScheduleReminderChange',
        'StudyFlowMaterialStreamOffer', 'StudyFlowMaterialChunkAck', 'notification.deliver',
        'schedule.notification_change', 'material.stream_offer', 'material.chunk_ack', 'LibraryImport',
        'unexpected_offset', 'stale_sequence',
    ],
    'core/link/src/test/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowNotificationMediaSyncTest.kt': [
        'notificationDeliveryIsTargetedMonotonicAndDuplicateSafe', 'materialStreamingOfferIsReadOnlyAndChunkAcknowledged',
        'tvRejectsMaterialOffersThatRequireWrites',
    ],
    'web/runtime/sflp_link.js': ['StudyFlowNotificationMediaRuntime', 'applyNotificationDelivery', 'acceptMaterialStream', 'applyMaterialChunk'],
    'build.gradle.kts': ['verifySflp18Sflp19NotificationMediaSync', 'verifySflp16Sflp17ControlInputDelegation'],
    'docs/roadmap/CROSS_PLATFORM_PARITY_CONNECTIVITY_ROADMAP.md': ['SFLP18 + SFLP19', 'COMPLETE', 'SFLP20'],
}


def fail(msg):
    print('SFLP18_19_NOTIFICATION_MEDIA_SYNC: FAIL')
    print(f'- {msg}')
    sys.exit(1)

for rel in REQUIRED:
    if not (ROOT / rel).exists():
        fail(f'missing {rel}')

for rel, needles in NEEDLES.items():
    text = (ROOT / rel).read_text()
    missing = [n for n in needles if n not in text]
    if missing:
        fail(f'{rel} missing {missing}')

for rel in [
    'core/link/src/test/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowControlInputDelegationTest.kt',
    'app/src/test/java/com/mikeyphw/studyflow/StudyFlowAndroidControlInputBridgeTest.kt',
    'app-tv/src/test/java/com/mikeyphw/studyflow/tv/StudyFlowTvControlInputReceiverTest.kt',
    'desktopApp/src/test/kotlin/com/mikeyphw/studyflow/desktop/DesktopControlInputDelegationControllerTest.kt',
]:
    text = (ROOT / rel).read_text()
    if 'StudyFlowPublicKey' in text or 'publicKey =' in text:
        fail(f'{rel} still references removed StudyFlowPublicKey/publicKey constructor field')

schema = (ROOT / 'protocol/sflp/v1/sflp-notification-media.schema.json').read_text()
for name in ['notification.delivery_ack', 'schedule.notification_change', 'material.chunk']:
    if name not in schema:
        fail(f'schema missing {name}')

print('SFLP18_19_NOTIFICATION_MEDIA_SYNC: PASS')
