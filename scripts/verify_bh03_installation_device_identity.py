#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
model = (ROOT / "core/model/src/main/kotlin/com/mikeyphw/studyflow/core/model/StudySync.kt").read_text()
repository = (ROOT / "core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/LocalStudyCloudSyncRepository.kt").read_text()
tests = (ROOT / "core/model/src/test/kotlin/com/mikeyphw/studyflow/core/model/StudyFlowSyncSettingsTest.kt").read_text()

required_model = [
    "import java.util.UUID",
    'fun newInstallationDeviceId(): String = "device-install-${UUID.randomUUID()}"',
    "fun resolveInstallationDeviceId(existingDeviceId: String?): String =",
    "existingDeviceId ?: newInstallationDeviceId()",
    "fun stableDeviceId(seed: String): String",
]
for needle in required_model:
    assert needle in model, f"missing model identity contract: {needle}"

assert "StudySyncDeviceIdentity.stableDeviceId(deviceName)" not in repository, (
    "repository still derives installation identity from the display name"
)
resolve_calls = repository.count("StudySyncDeviceIdentity.resolveInstallationDeviceId(")
assert resolve_calls == 5, f"expected 5 installation-id resolution sites, found {resolve_calls}"

clear_contract = [
    "val existingDeviceId = before[DeviceIdKey]",
    "preferences.clear()",
    "existingDeviceId?.let { deviceId -> preferences[DeviceIdKey] = deviceId }",
    "return syncState.first()",
]
for needle in clear_contract:
    assert needle in repository, f"clearLocalFoundation does not preserve installation identity: {needle}"

required_tests = [
    "fresh installation device ids are opaque and unique even when display names match",
    "existing persisted device ids are preserved exactly",
    "legacy deterministic device ids remain backward compatible",
    "missing persisted device id receives a new installation identity",
]
for name in required_tests:
    assert f"fun `{name}`()" in tests, f"missing BH03 regression: {name}"

assert 'assertEquals("device-c214f319", StudySyncDeviceIdentity.stableDeviceId("This device"))' in tests
print("BH03_INSTALLATION_DEVICE_IDENTITY: PASS")
