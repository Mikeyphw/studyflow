#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
REQUIRED = [
    'core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowShareImportConflictReview.kt',
    'core/link/src/test/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowShareImportConflictReviewTest.kt',
    'app/src/main/java/com/mikeyphw/studyflow/link/AndroidStudyFlowShareImportBridge.kt',
    'app/src/test/java/com/mikeyphw/studyflow/StudyFlowAndroidShareImportBridgeTest.kt',
    'desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopShareImportConflictController.kt',
    'desktopApp/src/test/kotlin/com/mikeyphw/studyflow/desktop/DesktopShareImportConflictControllerTest.kt',
    'app-tv/src/main/java/com/mikeyphw/studyflow/tv/link/StudyFlowTvShareImportConflictReceiver.kt',
    'app-tv/src/test/java/com/mikeyphw/studyflow/tv/StudyFlowTvShareImportConflictReceiverTest.kt',
    'web/runtime/sflp_link.js',
    'web/dist/runtime/sflp_link.js',
    'web/src/sflp_link.ts',
    'web/tests/test_sflp20_21_share_conflict.py',
    'protocol/sflp/v1/sflp-share-import-conflict.schema.json',
    'protocol/sflp/v1/golden/share-import-offer.json',
    'protocol/sflp/v1/golden/conflict-review-open.json',
    'SFLP20_21_CLIPBOARD_CONFLICT_REVIEW.md',
    'docs/architecture/SFLP20_21_CLIPBOARD_CONFLICT_REVIEW.md',
]
NEEDLES = {
    'core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowShareImportConflictReview.kt': [
        'share.import_offer', 'share.import_ack', 'conflict.review_open', 'conflict.resolve', 'conflict.resolution_ack',
        'StudyFlowShareImportReducer', 'StudyFlowConflictReviewReducer', 'tvClass && isWriteIntent',
        'stale_sequence', 'duplicate', 'StudyFlowShareImportDefaults',
    ],
    'core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowLinkProtocol.kt': [
        'ClipboardRead', 'ClipboardWrite', 'ShareReceive', 'ImportPreview', 'ConflictReview',
    ],
    'core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowDomainOperation.kt': [
        'ClipboardApplied', 'ShareImported', 'ConflictMerged',
    ],
    'core/link/src/test/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowShareImportConflictReviewTest.kt': [
        'shareImportOfferIsTargetGatedDuplicateSafeAndDomainConvertible',
        'tvClassPeersCanPreviewButCannotAcceptWriteImports',
        'conflictReviewOpensResolvesOnceAndProducesCanonicalOperation',
    ],
    'web/runtime/sflp_link.js': [
        'StudyFlowShareImportConflictRuntime', 'applyShareImportDelivery', 'openConflictReview', 'resolveConflict',
        'exports.StudyFlowControlInputRuntime = StudyFlowControlInputRuntime;',
        'exports.StudyFlowNotificationMediaRuntime = StudyFlowNotificationMediaRuntime;',
    ],
    'build.gradle.kts': ['verifySflp20Sflp21ShareImportConflict', 'verifySflp18Sflp19NotificationMediaSync'],
    'docs/roadmap/CROSS_PLATFORM_PARITY_CONNECTIVITY_ROADMAP.md': ['SFLP20 + SFLP21', 'COMPLETE', 'SFLP22'],
}

def fail(msg):
    print('SFLP20_21_SHARE_IMPORT_CONFLICT: FAIL')
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

runtime = (ROOT / 'web/runtime/sflp_link.js').read_text()
if 'export class StudyFlowControlInputRuntime' in runtime or 'export class StudyFlowNotificationMediaRuntime' in runtime:
    fail('web runtime still mixes CommonJS and ES module class exports')

schema = (ROOT / 'protocol/sflp/v1/sflp-share-import-conflict.schema.json').read_text()
for wire_name in ['share.import_offer', 'share.import_ack', 'conflict.review_open', 'conflict.resolve', 'conflict.resolution_ack']:
    if wire_name not in schema:
        fail(f'schema missing {wire_name}')

print('SFLP20_21_SHARE_IMPORT_CONFLICT: PASS')
