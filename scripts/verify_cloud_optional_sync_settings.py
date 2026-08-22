#!/usr/bin/env python3
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
errors: list[str] = []


def read(rel: str) -> str:
    path = ROOT / rel
    if not path.is_file():
        errors.append(f"missing file: {rel}")
        return ""
    return path.read_text(encoding="utf-8")


def require(condition: bool, message: str) -> None:
    if not condition:
        errors.append(message)


model = read("core/model/src/main/kotlin/com/mikeyphw/studyflow/core/model/StudySync.kt")
data = read("core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/LocalStudyCloudSyncRepository.kt")
settings_screen = read("feature/settings/src/main/java/com/mikeyphw/studyflow/feature/settings/SettingsScreen.kt")
sync_screen = read("feature/settings/src/main/java/com/mikeyphw/studyflow/feature/settings/SyncFoundationScreen.kt")
product = read("docs/product/PRODUCT_CONTRACT.md")
first_run = read("docs/product/FIRST_RUN_AND_UPGRADE_NOTES.md")
adr = read("docs/architecture/ADR-0034-local-first-optional-cloud-sync.md")
acceptance = read("docs/quality/CSOV00_CSOV01_ACCEPTANCE.md")

for token in [
    "data class StudyFlowSyncSettings",
    "syncEnabled: Boolean = false",
    "providerId: String? = null",
    "automaticSyncEnabled: Boolean = false",
    "wifiOnly: Boolean = true",
    "chargingOnly: Boolean = false",
    "encryptRemoteBackups: Boolean = true",
    "cloudRequired: Boolean = false",
    "configuredProviderId: String = providerId ?: StudyFlowSyncProviderIds.NONE",
    "StudyFlowSyncSettings.LocalFirstDefault",
    "NoSyncProvider",
    "ProviderIdKey",
    "AutomaticSyncEnabledKey",
    "WifiOnlyKey",
    "ChargingOnlyKey",
    "EncryptRemoteBackupsKey",
]:
    require(token in model + data, f"CSOV01 model/storage token missing: {token}")

for token in [
    "syncState.settings.providerId",
    "sync_provider_value",
    "sync_automatic_value",
    "sync_encryption_value",
    "sync_provider_none",
]:
    require(token in settings_screen + sync_screen, f"CSOV01 settings UI token missing: {token}")

for rel in [
    "feature/settings/src/main/res/values/strings.xml",
    "feature/settings/src/main/res/values-pt-rBR/strings.xml",
    "feature/plans/src/main/res/values/strings.xml",
    "feature/plans/src/main/res/values-pt-rBR/strings.xml",
]:
    text = read(rel)
    lower = text.lower()
    require("optional" in lower or "opcional" in lower or "local-first" in lower,
            f"{rel} must use local-first optional sync language")
    for forbidden in ["no cloud", "cloud-free", "never sync", "cloud required", "must sign in"]:
        require(forbidden not in lower, f"{rel} contains obsolete/obligatory cloud wording: {forbidden}")
    try:
        ET.fromstring(text)
    except ET.ParseError as exc:
        errors.append(f"invalid XML in {rel}: {exc}")

combined_docs = "\n".join([product, first_run, adr, acceptance]).lower()
for phrase in [
    "local-first",
    "optional cloud sync",
    "requires no studyflow account",
    "sync starts off",
    "provider none",
    "automatic sync off",
]:
    require(phrase in combined_docs, f"CSOV00 docs missing: {phrase}")

for forbidden in ["cloud is required", "obligatory cloud"]:
    require(forbidden not in combined_docs, f"CSOV00 docs contain bad cloud wording: {forbidden}")

if errors:
    print("CSOV00_CSOV01_CONTRACT: FAILED")
    for error in errors:
        print(f"- {error}")
    sys.exit(1)
print("CSOV00_CSOV01_CONTRACT: PASSED (local-first optional cloud settings default off)")
