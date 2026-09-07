#!/usr/bin/env python3
from pathlib import Path
root = Path(__file__).resolve().parents[1]
checks = {
    "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/StudyReferenceFreshness.kt": [
        "PossiblyStaleAfterDays: Long = 90",
        "studyflow:reference-verified-on=",
        "studyflow:reference-supersedes=",
    ],
    "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/ManageStudyLibrary.kt": [
        "suspend fun verifyReference(",
        "suspend fun replaceReference(",
        "previous.copy(archived = true",
    ],
    "feature/library/src/main/java/com/mikeyphw/studyflow/feature/library/StudyLibraryScreen.kt": [
        "reference_stale_policy",
        "replaceReference(",
        "reference_replace_history_notice",
    ],
}
for rel, needles in checks.items():
    text=(root/rel).read_text()
    for needle in needles:
        assert needle in text, f"{rel}: missing {needle}"
print("OV15 reference freshness source contract: PASS")
