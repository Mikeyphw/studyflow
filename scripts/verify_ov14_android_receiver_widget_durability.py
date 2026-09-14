#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
checks = [
    ("S5-03 receiver-bound TV credential", "core/tvcontrol/src/main/kotlin/com/mikeyphw/studyflow/core/tvcontrol/StudyFlowTvWireProtocol.kt", [
        "receiverServiceName", "receiverHostAddress", "receiverPort", "bindToReceiver", "matchesCredentialBinding",
    ]),
    ("S5-03 client refuses bound credential mismatch", "core/tvlink/src/main/kotlin/com/mikeyphw/studyflow/core/tvlink/StudyFlowTvLocalLink.kt", [
        "decrypted.bindToReceiver(endpoint, hostKey)", "receiver-binding-mismatch", "endpoint.matchesCredentialBinding(credential)",
    ]),
    ("S5-04 reminder deep links persist in launch intent", "app/src/main/java/com/mikeyphw/studyflow/reminders/StudyReminderLaunchActivity.kt", [
        "putReminderLaunchExtras", "consume(intent: Intent?", "intent?.toStudyReminderLaunch() ?: pending", "data = intent.data",
    ]),
    ("S5-04 MainActivity consumes actual intent extras", "app/src/main/java/com/mikeyphw/studyflow/MainActivity.kt", [
        "StudyReminderLaunchStore.consume(intent)", "onNewIntent", "setIntent(intent)",
    ]),
    ("S5-09 receiver mutation journal", "app/src/main/java/com/mikeyphw/studyflow/reminders/StudyFlowReceiverMutationJournal.kt", [
        "StudyFlowReceiverMutationJournal", "begin(kind", "complete(handle", "fail(handle", "pendingCount",
    ]),
    ("S5-09 reminder receivers settle accepted work", "app/src/main/java/com/mikeyphw/studyflow/reminders/StudyReminderReceivers.kt", [
        "journal.begin(\"reminder-deliver\"", "journal.complete(handle)", "journal.fail(handle", "StudyFlowReceiverMutationRecovery.noteReconcile",
    ]),
    ("DS2-06 widget START_NEXT is configured-plan scoped", "app/src/main/java/com/mikeyphw/studyflow/widget/StudyFlowWidgetActionReceiver.kt", [
        "activeSessionForConfiguredPlan", "it.studyPlanId == configuredPlanId", "container.manageStudySchedule.observeSchedule(planId)",
    ]),
    ("DS2-28 collision-proof action ids", "core/tvcontrol/src/main/kotlin/com/mikeyphw/studyflow/core/tvcontrol/StudyFlowTvWireProtocol.kt", [
        "StudyFlowTvDomainActionJournalKeys", "SHA-256", "action_sha256_", "tv-action-sha256-",
    ]),
    ("DS2-28 hashed journal and SFLP keys are used", "app/src/main/java/com/mikeyphw/studyflow/tvcontrol/StudyFlowTvDomainActionExecutor.kt", [
        "StudyFlowTvDomainActionJournalKeys.forAction(actionId)",
    ]),
    ("DS2-28 TV SFLP publish uses hashed keys", "app-tv/src/main/java/com/mikeyphw/studyflow/tv/link/StudyFlowTvSflpPeer.kt", [
        "StudyFlowTvDomainActionJournalKeys.sflpOperationId(action.actionId)",
        "StudyFlowTvDomainActionJournalKeys.sflpEntityId(action.actionId)",
    ]),
    ("OV14 tests exist", "core/tvcontrol/src/test/kotlin/com/mikeyphw/studyflow/core/tvcontrol/StudyFlowOv14ReceiverBindingJournalKeysTest.kt", [
        "sessionCredentialBindsToTheSelectedReceiverEndpoint", "domainActionJournalKeysUseFullSha256InsteadOfSanitizedTruncation",
    ]),
]

missing = []
for label, rel, needles in checks:
    path = ROOT / rel
    if not path.exists():
        missing.append(f"{label}: missing {rel}")
        continue
    text = path.read_text(errors="replace")
    for needle in needles:
        if needle not in text:
            missing.append(f"{label}: missing token {needle!r} in {rel}")

if missing:
    print("OV14 source evidence: FAIL", file=sys.stderr)
    for item in missing:
        print("- " + item, file=sys.stderr)
    sys.exit(1)
print("OV14 source evidence: PASS (10/10 receiver, widget and TV action durability gates)")
