#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

checks = {
    "core/model/src/main/kotlin/com/mikeyphw/studyflow/core/model/StudySync.kt": [
        "StudySyncConflictResolutionChoice",
        "KEEP_LOCAL",
        "USE_REMOTE",
        "KEEP_BOTH",
        "StudySyncConflictReviewItem",
        "StudySyncConflictReviewPresenter",
        "fromPlan(plan: StudySyncMergePlan)",
    ],
    "feature/settings/src/main/java/com/mikeyphw/studyflow/feature/settings/SyncFoundationScreen.kt": [
        "ConflictReviewCard",
        "conflictReviewItems: List<StudySyncConflictReviewItem>",
        "sync_conflict_review_message",
        "sync_conflict_keep_local",
        "sync_conflict_use_remote",
        "sync_conflict_keep_both",
    ],
    "feature/settings/src/main/res/values/strings.xml": [
        "If the same item changed on two devices",
        "No sync conflicts need review.",
        "Keep local",
        "Use remote",
        "Keep both",
    ],
    "core/domain/src/test/kotlin/com/mikeyphw/studyflow/core/domain/StudySyncConflictReviewPresenterTest.kt": [
        "manual conflicts become review items",
        "StudySyncConflictResolutionChoice.KEEP_LOCAL",
        "StudySyncConflictResolutionChoice.USE_REMOTE",
        "StudySyncConflictResolutionChoice.KEEP_BOTH",
    ],
}

failures = []
for relative, markers in checks.items():
    text = (ROOT / relative).read_text()
    for marker in markers:
        if marker not in text:
            failures.append(f"{relative}: missing {marker}")

if failures:
    raise SystemExit("CSOV08 conflict review violations:\n" + "\n".join(failures))

print("CSOV08_CONFLICT_REVIEW: PASS")
