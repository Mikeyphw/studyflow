#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text()


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(f"CSOV02_SYNC_BUNDLE: FAIL — {message}")


codec = read("core/portability/src/main/kotlin/com/mikeyphw/studyflow/core/portability/StudyFlowSyncBundle.kt")
test = read("core/portability/src/test/kotlin/com/mikeyphw/studyflow/core/portability/StudyFlowSyncBundleCodecTest.kt")
quality = read("docs/quality/CSOV02_SYNC_BUNDLE_ACCEPTANCE.md")

for marker in [
    "StudyFlowSyncBundleFormat",
    "com.mikeyphw.studyflow.sync",
    "StudyFlowSyncBundleSchemaVersion = 1",
    "StudyFlowSyncBundleExtension = \".sfbackup\"",
    "manifest.json",
    "checkpoint.json",
    "database.snapshot",
    "attachments/",
    "integrity.sha256",
    "StudyFlowSyncCheckpoint",
    "StudyFlowSyncBundleManifest",
    "StudyFlowSyncIntegrity",
    "writeEncryptedArchive",
    "StudyPlanEncryptedPackageCodec",
    "encryptedForRemote",
    "databaseChecksumSha256",
]:
    require(marker in codec, f"missing codec marker {marker!r}")

for marker in [
    "archiveRoundTripKeepsManifestCheckpointDatabaseAndAttachments",
    "encryptedArchiveUsesExistingAuthenticatedEnvelope",
    "manifestRejectsRawRemoteUploadDeclaration",
    "assertFalse(encoded.decodeToString().contains(\"room database snapshot\"))",
]:
    require(marker in test, f"missing regression test marker {marker!r}")

for marker in ["AES-GCM", ".sfbackup", "integrity.sha256", "must not expose the raw database"]:
    require(marker in quality, f"missing acceptance marker {marker!r}")

print("CSOV02_SYNC_BUNDLE: PASS")
