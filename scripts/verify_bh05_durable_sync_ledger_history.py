#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
REPO = ROOT / "core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/LocalStudyCloudSyncRepository.kt"
CODEC = ROOT / "core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/StudySyncLedgerHistoryCodec.kt"
TESTS = ROOT / "core/data/src/test/kotlin/com/mikeyphw/studyflow/core/data/StudySyncLedgerHistoryCodecTest.kt"
DOC = ROOT / "docs/quality/BH05_DURABLE_SYNC_LEDGER_HISTORY_ACCEPTANCE.md"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def main() -> None:
    for path in (REPO, CODEC, TESTS, DOC):
        require(path.is_file(), f"missing BH05 file: {path.relative_to(ROOT)}")

    repo = REPO.read_text()
    codec = CODEC.read_text()
    tests = TESTS.read_text()

    require('stringPreferencesKey("sync_ledger_checkpoints_v1")' in repo,
            "checkpoint history is not persisted under a versioned DataStore key")
    require('stringPreferencesKey("sync_ledger_events_v1")' in repo,
            "event history is not persisted under a versioned DataStore key")
    require('StudySyncLedgerHistoryCodec.upsertCheckpoint' in repo,
            "repository does not append/update durable checkpoint history")
    require('StudySyncLedgerHistoryCodec.appendEvent' in repo,
            "repository does not append durable event history")
    require('val existingHistory = persistedEventHistory(preferences)' in repo,
            "event migration must read legacy history before overwriting LastLedgerEvent keys")
    require('val existingHistory = persistedCheckpointHistory(preferences)' in repo,
            "checkpoint migration must read legacy history before overwriting LastCheckpoint keys")
    require('persistCurrentCheckpointHistory(preferences)' in repo,
            "upload/preview mutations are not folded back into checkpoint history")
    require('persistCheckpointHistory(' in repo and 'archive.manifest.checkpointId' in repo,
            "restore preview does not retain previously unseen remote checkpoint facts")
    require('checkpointHistory = persistedCheckpointHistory(before)' in repo,
            "clearLocalFoundation does not preserve/migrate checkpoint history")
    require('eventHistory = persistedEventHistory(before)' in repo,
            "clearLocalFoundation does not preserve/migrate event history")
    require('existingDeviceFirstSeenAt' in repo,
            "clearLocalFoundation does not preserve durable device first-seen fact")

    require('const val MaxCheckpointHistory: Int = 64' in codec,
            "checkpoint retention must remain bounded at 64")
    require('const val MaxEventHistory: Int = 128' in codec,
            "event retention must remain bounded at 128")
    require('studyflow-sync-checkpoints-v1' in codec and 'studyflow-sync-events-v1' in codec,
            "history serialization is not explicitly versioned")
    require('.takeLast(MaxCheckpointHistory)' in codec,
            "checkpoint history pruning is missing")
    require('.takeLast(MaxEventHistory)' in codec,
            "event history pruning is missing")
    require('runCatching { decodeCheckpoint(line) }.getOrNull()' in codec,
            "checkpoint decoder must isolate corrupt rows")
    require('runCatching { decodeEvent(line) }.getOrNull()' in codec,
            "event decoder must isolate corrupt rows")

    for name in (
        'checkpoint history round trips durable upload and restore facts',
        'checkpoint history upsert replaces same checkpoint and keeps newest bounded records',
        'event history round trips text and keeps newest sequences',
        'invalid persisted rows do not erase valid history',
    ):
        require(f'fun `{name}`()' in tests, f"missing BH05 regression: {name}")

    print("BH05_DURABLE_SYNC_LEDGER_HISTORY: PASS")


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:
        print(f"BH05_DURABLE_SYNC_LEDGER_HISTORY: FAIL: {exc}", file=sys.stderr)
        raise SystemExit(1)
