#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
FAILURES: list[str] = []


def read(rel: str) -> str:
    path = ROOT / rel
    if not path.is_file():
        FAILURES.append(f"missing required file: {rel}")
        return ""
    return path.read_text(encoding="utf-8")


def require(condition: bool, message: str) -> None:
    if not condition:
        FAILURES.append(message)


def require_tokens(rel: str, *tokens: str) -> str:
    text = read(rel)
    for token in tokens:
        require(token in text, f"{rel} missing token: {token}")
    return text

activity = require_tokens(
    "app-tv/src/main/java/com/mikeyphw/studyflow/tv/StudyFlowTvActivity.kt",
    "AndroidStudyFlowTvDomainActionOutboxPersistence(applicationContext)",
    "StudyFlowTvDurableCommandSequencer(applicationContext)",
    "StudyFlowTvDurableUiStateStore(applicationContext)",
    "commandSequencer.next()",
    "durableUiStateStore.saveDestination",
    "durableUiStateStore.saveSearchState",
    "durableUiStateStore.saveMaterialViewerState",
    "lastTvCommandFailureReason ?: \"command-rejected\"",
)
require("localCommandSequence" not in activity, "S8-02 command sequence must not be Activity memory only")

outbox = require_tokens(
    "app-tv/src/main/java/com/mikeyphw/studyflow/tv/domain/StudyFlowTvDomainActionOutbox.kt",
    "StudyFlowTvDomainActionOutboxPersistence",
    "StudyFlowTvDomainActionOutboxSnapshot",
    "persistentOutboxRequired = true",
    "dropsOldestPendingAction = false",
    "explicitBackpressureReason = \"domain-action-outbox-full\"",
    "pendingActions(): List<StudyFlowTvDomainAction>",
    "lastRejectionReason()",
    "persist()",
)
require("pending.remove(pending.keys.first())" not in outbox, "S8-03/DS2-27 outbox must not drop oldest pending actions")
require('lastRejectionReason = "domain-action-outbox-full"' in outbox, "S8-03 must expose explicit outbox backpressure")

require_tokens(
    "app-tv/src/main/java/com/mikeyphw/studyflow/tv/domain/AndroidStudyFlowTvDomainActionOutboxPersistence.kt",
    "Json.encodeToString",
    "Json.decodeFromString",
    "commit()",
    "corrupt-outbox-snapshot:",
)
require_tokens(
    "app-tv/src/main/java/com/mikeyphw/studyflow/tv/domain/StudyFlowTvDurableCommandSequencer.kt",
    "commandIdsSurviveActivityRecreation = true",
    "commitBeforeIdExposure = true",
    "putLong(KEY_NEXT_SEQUENCE, following).commit()",
)
require_tokens(
    "app-tv/src/main/java/com/mikeyphw/studyflow/tv/StudyFlowTvDurableUiStateStore.kt",
    "presentationUiStateIsDurable = true",
    "destinationSearchAndViewerCursorsPersist = true",
    "restoreDestination()",
    "restoreSearchState",
    "restoreMaterialViewerState",
    "saveMaterialViewerState",
    "commit()",
)

reducer = require_tokens(
    "app-tv/src/main/java/com/mikeyphw/studyflow/tv/control/StudyFlowTvControlStateReducer.kt",
    "wallClockStaleDetectionForbidden = true",
    "duplicateCommandsRejected = true",
    "identity/revision based, not wall-clock based",
)
require("stale-command" not in reducer, "S8-07 reducer must not reject commands by unsynchronized wall-clock time")

presentation = require_tokens(
    "app-tv/src/main/java/com/mikeyphw/studyflow/tv/presentation/StudyFlowTvPresentationAdapter.kt",
    "identity/revision based, not wall-clock based",
    "snapshot.snapshotId == previous.snapshotId",
)
require("generatedAtEpochMillis < previous.generatedAtEpochMillis" not in presentation, "S8-07 presentation inbox must not discard snapshots by sender wall clock")

local_link = require_tokens(
    "core/tvlink/src/main/kotlin/com/mikeyphw/studyflow/core/tvlink/StudyFlowTvLocalLink.kt",
    "sessionCredentialCommitBeforeSuccess = true",
    "tvAndSflpTrustUseCanonicalStore = true",
    "fun save(credential: StudyFlowTvSessionCredential): Boolean",
    "commit()",
    "pairing-credential-save-failed",
    "if (!sessionStore.save(accepted.credential))",
    "publishSession(accepted.credential.toControlSession())",
)
session_store_block = local_link[local_link.find("class StudyFlowTvSessionStore"):local_link.find("/**\n * Android NSD discovery")]
require(".apply()" not in session_store_block, "DS2-44 session save/revoke must use synchronous commit before success")
require(local_link.find("if (!sessionStore.save(accepted.credential))") < local_link.find("credential = accepted.credential"), "DS2-44 TV host must commit credential before updating in-memory success state")
require(local_link.find("if (!sessionStore.save(accepted.credential))") < local_link.find("publishSession(accepted.credential.toControlSession())"), "DS2-44 TV host must commit credential before publishing paired session")

require_tokens(
    "app-tv/src/test/java/com/mikeyphw/studyflow/tv/domain/StudyFlowTvDomainActionOutboxOv18DurabilityTest.kt",
    "outboxRestoresPendingActionsAndAcknowledgementFromPersistence",
    "fullOutboxRejectsWithBackpressureInsteadOfDroppingOldest",
)
require_tokens(
    "app-tv/src/test/java/com/mikeyphw/studyflow/tv/control/StudyFlowTvControlStateReducerOv18ClockTest.kt",
    "reducerDoesNotRejectRemoteCommandBecauseSenderClockLooksOlder",
)
require_tokens(
    "app-tv/src/test/java/com/mikeyphw/studyflow/tv/presentation/StudyFlowTvPresentationAdapterTest.kt",
    "inboxRejectsDuplicatesButDoesNotUseUnsynchronizedWallClockStaleness",
)

for finding in ("S8-01", "S8-02", "S8-03", "S8-07", "S8-18", "S8-19", "DS2-27", "DS2-44"):
    require(finding in read("docs/remediation/OV18_NATIVE_TV_DURABLE_CANONICAL_STATE.md"), f"OV18 doc missing finding {finding}")

if FAILURES:
    print("OV18_NATIVE_TV_DURABLE_STATE: FAIL", file=sys.stderr)
    for failure in FAILURES:
        print(f" - {failure}", file=sys.stderr)
    sys.exit(1)

print("OV18_NATIVE_TV_DURABLE_STATE: PASS")
