#!/usr/bin/env python3
"""Focused source contract for BH02 sync-bundle correctness hardening."""

from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
CODEC = ROOT / "core/portability/src/main/kotlin/com/mikeyphw/studyflow/core/portability/StudyFlowSyncBundle.kt"
TESTS = ROOT / "core/portability/src/test/kotlin/com/mikeyphw/studyflow/core/portability/StudyFlowSyncBundleCodecTest.kt"

errors: list[str] = []


def require(condition: bool, message: str) -> None:
    if not condition:
        errors.append(message)


def text(path: Path) -> str:
    if not path.is_file():
        errors.append(f"missing file: {path.relative_to(ROOT)}")
        return ""
    return path.read_text(encoding="utf-8")


codec = text(CODEC)
tests = text(TESTS)

# Actual database bytes, not only mutually-consistent metadata, are authoritative.
require(
    "validateManifest(archive.manifest, archive.databaseSnapshot)" in codec,
    "writer must validate the manifest against actual database bytes",
)
require(
    "val databaseSnapshot = entries.getValue(DatabaseSnapshotEntry)" in codec
    and "validateManifest(manifest, databaseSnapshot)" in codec,
    "reader must validate the manifest against extracted database bytes",
)
require(
    'require(sha256(databaseSnapshot) == manifest.databaseChecksumSha256)' in codec,
    "database.snapshot SHA-256 must equal manifest databaseChecksumSha256",
)

# Duplicate declarations must fail before they can collapse through Set/Map semantics.
require(
    "attachmentEntryNames.size == attachmentEntryNames.toSet().size" in codec,
    "duplicate attachment declarations must be rejected explicitly",
)

# Writer and reader must share the same entry/byte budget semantics.
require("validateWriteBudget(" in codec, "writer must preflight archive budgets")
require(codec.count("ArchiveBudget()") >= 2, "writer and reader must both use ArchiveBudget")
require("private class ArchiveBudget" in codec, "shared archive budget helper is missing")
require("private const val MaxEntryCount = 2_048" in codec, "entry-count ceiling drifted")
require("private const val MaxEntryBytes = 64 * 1024 * 1024" in codec, "per-entry ceiling drifted")
require("private const val MaxTotalBytes = 512 * 1024 * 1024" in codec, "total-byte ceiling drifted")

for test_name in (
    "writerRejectsDatabaseHashMetadataThatDoesNotMatchSnapshotBytes",
    "readerRejectsDatabaseHashMetadataThatDoesNotMatchSnapshotBytes",
    "writerRejectsDuplicateAttachmentDeclarations",
    "readerRejectsDuplicateAttachmentDeclarations",
    "writerRejectsArchiveWhoseDeclaredTotalExceedsReaderBudget",
    "writerRejectsDeclaredEntryLargerThanReaderBudget",
    "writerRejectsArchiveWhoseEntryCountExceedsReaderBudget",
):
    require(f"fun {test_name}()" in tests, f"missing BH02 regression test: {test_name}")

if errors:
    for error in errors:
        print(f"BH02_SYNC_BUNDLE_HARDENING: FAIL: {error}", file=sys.stderr)
    raise SystemExit(1)

print("BH02_SYNC_BUNDLE_HARDENING: PASS")
