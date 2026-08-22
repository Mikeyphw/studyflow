#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text()


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(f"CSOV03_PROVIDER_ABSTRACTION: FAIL — {message}")


source = read("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/StudyFlowSyncProvider.kt")
test = read("core/domain/src/test/kotlin/com/mikeyphw/studyflow/core/domain/StudyFlowSyncProviderTest.kt")
quality = read("docs/quality/CSOV03_PROVIDER_ABSTRACTION_ACCEPTANCE.md")
build = read("core/domain/build.gradle.kts")

for marker in [
    "interface StudyFlowSyncProvider",
    "val requiresAccount: Boolean",
    "suspend fun isConfigured()",
    "suspend fun listCheckpoints()",
    "suspend fun uploadCheckpoint(bundle: SyncBundleUpload)",
    "suspend fun downloadCheckpoint(id: String)",
    "suspend fun deleteCheckpoint(id: String)",
    "RemoteSyncCheckpoint",
    "SyncBundleUpload",
    "SyncBundleDownload",
    "encryptedBytes: ByteArray",
    "StudyFlowSyncBundleManifest",
    "SyncProviderNotConfiguredException",
    "DisabledStudyFlowSyncProvider",
    "StudyFlowSyncProviderRegistry",
]:
    require(marker in source, f"missing provider marker {marker!r}")

for marker in [
    "disabledProviderIsAvailableButNeverConfigured",
    "disabledProviderRejectsUpload",
    "registryDefaultsToDisabledProviderForNullProviderId",
    "registryRejectsUnknownProvider",
]:
    require(marker in test, f"missing regression test marker {marker!r}")

for marker in ["fail closed", "not configured", "encrypted `.sfbackup`", "rejects unknown ids"]:
    require(marker in quality, f"missing acceptance marker {marker!r}")

require("testImplementation(libs.kotlinx.coroutines.test)" in build, "domain tests need coroutine test dependency")

print("CSOV03_PROVIDER_ABSTRACTION: PASS")
