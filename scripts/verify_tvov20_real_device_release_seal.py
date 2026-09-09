#!/usr/bin/env python3
from __future__ import annotations

import argparse
import os
import re
import shutil
import subprocess
import sys
import time
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FAILURES: list[str] = []
PACKAGE = "com.mikeyphw.studyflow.tv"
ACTIVITY = f"{PACKAGE}/.StudyFlowTvActivity"
TARGET_MIB = 25


def require(condition: bool, message: str) -> None:
    if not condition:
        FAILURES.append(message)


def read(rel: str) -> str:
    path = ROOT / rel
    require(path.is_file(), f"missing required file: {rel}")
    return path.read_text(encoding="utf-8") if path.is_file() else ""


def run_capture(command: list[str], timeout: int = 30) -> subprocess.CompletedProcess[str] | None:
    try:
        return subprocess.run(command, cwd=ROOT, text=True, capture_output=True, timeout=timeout, check=False)
    except (OSError, subprocess.TimeoutExpired):
        return None


def adb_command(adb: str, serial: str, *args: str, timeout: int = 30) -> subprocess.CompletedProcess[str] | None:
    return run_capture([adb, "-s", serial, *args], timeout=timeout)


def find_adb() -> str | None:
    direct = shutil.which("adb")
    if direct:
        return direct
    for env_name in ("ANDROID_SDK_ROOT", "ANDROID_HOME"):
        root = os.environ.get(env_name)
        if root:
            candidate = Path(root) / "platform-tools" / "adb"
            if candidate.is_file():
                return str(candidate)
    return None


def connected_device(adb: str) -> tuple[str | None, str]:
    result = run_capture([adb, "devices"], timeout=10)
    if result is None or result.returncode != 0:
        detail = "adb devices unavailable"
        if result is not None:
            detail = (result.stderr or result.stdout).strip() or detail
        return None, detail
    devices: list[str] = []
    for line in result.stdout.splitlines()[1:]:
        parts = line.split()
        if len(parts) >= 2 and parts[1] == "device":
            devices.append(parts[0])
    requested = os.environ.get("STUDYFLOW_TV_SERIAL", "").strip()
    if requested:
        return (requested, "selected by STUDYFLOW_TV_SERIAL") if requested in devices else (None, f"requested serial {requested} is not online")
    if not devices:
        return None, "no online ADB device"
    if len(devices) > 1:
        return None, "multiple online devices; set STUDYFLOW_TV_SERIAL to choose the TV"
    return devices[0], "single online ADB device"


def verify_release_artifact() -> None:
    release_apks = sorted((ROOT / "app-tv/build/outputs/apk/release").glob("*.apk"))
    mapping = ROOT / "app-tv/build/outputs/mapping/release/mapping.txt"
    require(bool(release_apks), "TVOV20 release seal requires a built Release APK")
    require(mapping.is_file(), "TVOV20 release seal requires R8 mapping.txt")
    if not release_apks:
        return
    apk = release_apks[0]
    size_mib = apk.stat().st_size / (1024 * 1024)
    require(size_mib <= TARGET_MIB, f"Release APK is {size_mib:.2f} MiB, above the 25 MiB target")
    try:
        with zipfile.ZipFile(apk) as archive:
            require(archive.testzip() is None, "Release APK ZIP integrity failed")
            names = set(archive.namelist())
            require("AndroidManifest.xml" in names, "Release APK missing AndroidManifest.xml")
            require(any(name.endswith("classes.dex") for name in names), "Release APK missing classes.dex")
    except zipfile.BadZipFile:
        require(False, "Release APK is not a valid ZIP")

    aapt2 = shutil.which("aapt2")
    if aapt2:
        result = run_capture([aapt2, "dump", "badging", str(apk)])
        require(result is not None and result.returncode == 0, "aapt2 failed to inspect final Release APK")
        if result is not None and result.returncode == 0:
            require(f"package: name='{PACKAGE}'" in result.stdout, "final Release APK package identity mismatch")
            require("android.software.leanback" in result.stdout, "final Release APK missing Leanback feature")
    else:
        print("TVOV20_AAPT2_BADGING: SKIP (aapt2 not available in this environment)")

    signing_keys = (
        "STUDYFLOW_RELEASE_STORE_FILE",
        "STUDYFLOW_RELEASE_STORE_PASSWORD",
        "STUDYFLOW_RELEASE_KEY_ALIAS",
        "STUDYFLOW_RELEASE_KEY_PASSWORD",
    )
    signing_configured = all(os.environ.get(key, "").strip() for key in signing_keys)
    if signing_configured:
        apksigner = shutil.which("apksigner")
        require(apksigner is not None, "release signing is configured but apksigner is unavailable")
        if apksigner:
            result = run_capture([apksigner, "verify", "--verbose", str(apk)])
            require(result is not None and result.returncode == 0, "final signed Release APK failed apksigner verification")
    else:
        print("TVOV20_SIGNED_RELEASE: SKIP (production signing credentials not configured)")


def run_real_device_acceptance() -> bool:
    adb = find_adb()
    if adb is None:
        print("TVOV20_REAL_DEVICE_ACCEPTANCE: SKIP (adb not available)")
        return False
    serial, reason = connected_device(adb)
    if serial is None:
        print(f"TVOV20_REAL_DEVICE_ACCEPTANCE: SKIP ({reason})")
        return False
    print(f"TVOV20_REAL_DEVICE_SERIAL={serial} ({reason})")

    features = adb_command(adb, serial, "shell", "pm", "list", "features", timeout=15)
    require(features is not None and features.returncode == 0, "could not query connected-device features")
    if features is None or features.returncode != 0:
        return True
    require("android.software.leanback" in features.stdout, "connected device is not an Android TV/Leanback device")
    if "android.software.leanback" not in features.stdout:
        return True

    debug_apks = sorted((ROOT / "app-tv/build/outputs/apk/debug").glob("*.apk"))
    require(bool(debug_apks), "real-device acceptance requires a built Debug APK")
    if not debug_apks:
        return True
    apk = debug_apks[0]
    install = adb_command(adb, serial, "install", "-r", "-t", "-d", str(apk), timeout=120)
    install_text = "" if install is None else (install.stdout + "\n" + install.stderr)
    require(install is not None and install.returncode == 0 and "Success" in install_text, "TV Debug APK install/upgrade failed")
    if install is None or install.returncode != 0:
        return True

    adb_command(adb, serial, "shell", "am", "force-stop", PACKAGE, timeout=10)
    launch = adb_command(adb, serial, "shell", "am", "start", "-W", "-n", ACTIVITY, timeout=30)
    launch_text = "" if launch is None else (launch.stdout + "\n" + launch.stderr)
    require(launch is not None and launch.returncode == 0 and "Error" not in launch_text, "TV activity launch failed")
    if launch is None or launch.returncode != 0:
        return True

    time.sleep(1.0)
    for key in ("KEYCODE_DPAD_RIGHT", "KEYCODE_DPAD_DOWN", "KEYCODE_DPAD_LEFT", "KEYCODE_DPAD_UP", "KEYCODE_DPAD_CENTER"):
        result = adb_command(adb, serial, "shell", "input", "keyevent", key, timeout=10)
        require(result is not None and result.returncode == 0, f"D-pad smoke input failed for {key}")

    pid = adb_command(adb, serial, "shell", "pidof", PACKAGE, timeout=10)
    require(pid is not None and pid.returncode == 0 and pid.stdout.strip(), "TV process is not alive after D-pad smoke")
    package_dump = adb_command(adb, serial, "shell", "dumpsys", "package", PACKAGE, timeout=20)
    require(package_dump is not None and package_dump.returncode == 0, "could not inspect installed TV package")
    if package_dump is not None and package_dump.returncode == 0:
        require(re.search(r"versionCode=26(?:\s|$)", package_dump.stdout) is not None, "installed TV package versionCode is not 26")
        require("versionName=0.1.0-tv26" in package_dump.stdout, "installed TV package versionName mismatch")

    if not FAILURES:
        print("TVOV20_REAL_DEVICE_ACCEPTANCE: PASS")
    return True


parser = argparse.ArgumentParser()
parser.add_argument("--require-release-artifact", action="store_true")
parser.add_argument("--device-acceptance", action="store_true")
args = parser.parse_args()

policy = read("app-tv/src/main/java/com/mikeyphw/studyflow/tv/release/StudyFlowTvReleaseAcceptancePolicy.kt")
policy_test = read("app-tv/src/test/java/com/mikeyphw/studyflow/tv/release/StudyFlowTvReleaseAcceptancePolicyTest.kt")
contract = read("app-tv/src/main/java/com/mikeyphw/studyflow/tv/StudyFlowTvBuildContract.kt")
build_test = read("app-tv/src/test/java/com/mikeyphw/studyflow/tv/StudyFlowTvBuildContractTest.kt")
root_gradle = read("build.gradle.kts")
devtool = read(".devtool.toml")
roadmap = read("ANDROID_TV_ROADMAP.md")
rules = read("TV_ANDROID_TV_FULL_ROADMAP_AND_WORKING_RULES.md")
quality = read("docs/quality/TVOV20_REAL_DEVICE_RELEASE_SEAL_CONTRACTS.md")
notes = read("TVOV20_ANDROID_TV_REAL_DEVICE_ACCEPTANCE_RELEASE_SEAL.md")
readme = read("README.md")

for token in (
    'const val overlay = "TVOV20"',
    'releaseTargetMiB = 25',
    'NO_DEVICE_SKIPPED',
    'REAL_DEVICE_ACCEPTED',
    'REAL_DEVICE_FAILED',
    'fun classifyDeviceAcceptance',
    'fun releaseSealCanPass',
):
    require(token in policy, f"TVOV20 acceptance policy missing token: {token}")
for token in ('noDeviceIsCleanSkipButConnectedFailureIsNot', 'artifactReadyPlusSkipOrAcceptedDeviceCanSeal'):
    require(token in policy_test, f"TVOV20 acceptance test missing {token}")
for token in (
    'finalAcceptanceSealIntroduced = true',
    'optimizedReleaseArtifactRequired = true',
    'leanbackDeviceCheckRequiredWhenConnected = true',
    'installOrUpgradeCheckRequiredWhenConnected = true',
    'launchAndDpadSmokeRequiredWhenConnected = true',
    'noDeviceMustSkipCleanly = true',
    'physicalDeviceAcceptanceMustNotBeClaimedWhenSkipped = true',
):
    require(token in contract, f"TVOV20 build contract missing {token}")
    require(token.split(" = ")[0] in build_test, f"TVOV20 build contract test missing {token}")
for token in (
    'tasks.register<Exec>("verifyStudyFlowTvFinalAcceptanceContracts")',
    'tasks.register<Exec>("verifyStudyFlowTvRealDeviceAcceptance")',
    'tasks.register<Exec>("verifyStudyFlowTvFinalReleaseSeal")',
):
    require(token in root_gradle, f"TVOV20 Gradle wiring missing {token}")
for token in ("verifyStudyFlowTvFinalAcceptanceContracts", "verifyStudyFlowTvRealDeviceAcceptance", "verifyStudyFlowTvFinalReleaseSeal"):
    require(token in devtool, f"studyflow_tv target missing TVOV20 token: {token}")
require("TVOV20  Real-device acceptance and release seal [implemented]" in roadmap, "roadmap must mark TVOV20 implemented")
require("TVOV20  Real-device acceptance and release seal [implemented]" in rules, "working rules must mark TVOV20 implemented")
require("## TVOV20" in roadmap, "roadmap missing TVOV20 implementation note")
require("no-device" in quality.lower() and "D-pad" in quality and "Release APK" in quality, "TVOV20 quality contract incomplete")
require("physical-device acceptance" in notes and "clean skip" in notes, "TVOV20 implementation note incomplete")
require("TVOV19 and TVOV20" in readme, "README missing TVOV19/20 summary")
require("TVOV00 through TVOV26" in rules, "working rules must record the current TV implementation chain")

if args.require_release_artifact:
    verify_release_artifact()
if args.device_acceptance:
    run_real_device_acceptance()

if FAILURES:
    print("TVOV20_FINAL_RELEASE_SEAL: FAIL")
    for failure in FAILURES:
        print(f"- {failure}")
    sys.exit(1)
print("TVOV20_FINAL_RELEASE_SEAL: PASS")
