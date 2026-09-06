#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
STORE = ROOT / "app/src/main/java/com/mikeyphw/studyflow/reminders/StudyReminderOccurrenceStore.kt"
HISTORY = ROOT / "app/src/main/java/com/mikeyphw/studyflow/reminders/DeliveredReminderHistory.kt"
TEST = ROOT / "app/src/test/java/com/mikeyphw/studyflow/reminders/DeliveredReminderHistoryTest.kt"
CONTRACT_TEST = ROOT / "app/src/test/java/com/mikeyphw/studyflow/reminders/ReminderDeliveredHistorySourceContractTest.kt"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def main() -> int:
    store = STORE.read_text()
    history = HISTORY.read_text()
    tests = TEST.read_text()
    contract_tests = CONTRACT_TEST.read_text()

    require('private const val MaxDeliveredOccurrenceIds = 256' in store, "delivered history cap changed")
    require('deliveredOrderKey(planId)' in store, "ordered delivered-history key is not wired")
    require('putString(' in store and 'DeliveredReminderHistory.encode(delivered)' in store,
            "ordered history is not persisted as a String")
    require('DeliveredReminderHistory.migrateLegacy' in store,
            "legacy delivered StringSet migration is missing")
    require('putStringSet(deliveredKey(planId), LinkedHashSet(delivered))' in store,
            "legacy recovery/downgrade membership mirror is missing")
    require('remove(deliveredOrderKey(planId))' in store,
            "clear/remove paths do not clear ordered history")

    mark = store.split('fun markDelivered', 1)[1].split('fun removeDelivered', 1)[0]
    require('DeliveredReminderHistory.append' in mark, "markDelivered does not use ordered append")
    require('getStringSet(deliveredKey' not in mark,
            "markDelivered still uses SharedPreferences StringSet as an ordering source")
    require('remove(delivered.first())' not in mark,
            "legacy unordered first-entry pruning remains")

    for marker in (
        'ordered.remove(occurrenceId)',
        'ordered.add(occurrenceId)',
        'while (ordered.size > maxSize)',
        'ordered.remove(ordered.first())',
        'private const val FormatPrefix = "v1;"',
        'fun encode(',
        'fun decode(',
    ):
        require(marker in history, f"ordered history helper missing contract marker: {marker}")

    for test_name in (
        'pruning removes the true oldest delivery and keeps the newest 256',
        'redelivery refreshes recency before pruning',
        'ordered encoding round trips arbitrary reminder identities',
        'legacy migration preserves membership within the historical cap',
    ):
        require(test_name in tests, f"missing BH04 regression: {test_name}")

    require('occurrence store uses ordered persistence for delivered history' in contract_tests,
            "missing occurrence-store source wiring regression")

    print("BH04_REMINDER_DELIVERED_HISTORY: PASS")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except AssertionError as exc:
        print(f"BH04_REMINDER_DELIVERED_HISTORY: FAIL: {exc}", file=sys.stderr)
        raise SystemExit(1)
