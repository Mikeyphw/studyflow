#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
REPORT = ROOT / "build/reports/studyflow/tvov26-two-device-acceptance.json"
PHONE_PACKAGE = "com.mikeyphw.studyflow"
PHONE_ACTIVITY = f"{PHONE_PACKAGE}/.MainActivity"
PHONE_CONTROLLER_ACTION = "com.mikeyphw.studyflow.action.TV_CONTROLLER"
TV_PACKAGE = "com.mikeyphw.studyflow.tv"
TV_ACTIVITY = f"{TV_PACKAGE}/.StudyFlowTvActivity"
TV_VERSION_CODE = 26
TV_VERSION_NAME = "0.1.0-tv26"
REQUIRED_CHECKS = [
    "two_devices_classified",
    "signed_release_artifacts_installed",
    "tv_leanback_launch_dpad",
    "phone_controller_launch",
    "fresh_pairing",
    "authenticated_navigation",
    "live_cast_acknowledged",
    "canonical_domain_mutation",
    "tv_restart_resync",
    "phone_restart_resync",
    "stop_cast_waiting_state",
    "revoke_blocks_control",
]

class AcceptanceBlocked(RuntimeError):
    pass


def run(*args: str, timeout: int = 60, check: bool = True) -> subprocess.CompletedProcess[str]:
    result = subprocess.run(list(args), cwd=ROOT, text=True, capture_output=True, timeout=timeout, check=False)
    if check and result.returncode:
        raise AcceptanceBlocked((result.stderr or result.stdout).strip() or f"command failed: {' '.join(args)}")
    return result


def adb(adb_bin: str, serial: str, *args: str, timeout: int = 60, check: bool = True) -> subprocess.CompletedProcess[str]:
    return run(adb_bin, "-s", serial, *args, timeout=timeout, check=check)


def adb_text(adb_bin: str, serial: str, *args: str, timeout: int = 60) -> str:
    return adb(adb_bin, serial, *args, timeout=timeout).stdout.strip()


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def installed_apk_sha256(adb_bin: str, serial: str, package: str) -> str:
    paths = [
        line.removeprefix("package:").strip()
        for line in adb_text(adb_bin, serial, "shell", "pm", "path", package).splitlines()
        if line.startswith("package:")
    ]
    base = next((item for item in paths if item.endswith("/base.apk")), paths[0] if len(paths) == 1 else None)
    if not base:
        raise AcceptanceBlocked(f"unable to resolve installed base APK for {package} on {serial}")
    process = subprocess.Popen([adb_bin, "-s", serial, "exec-out", "cat", base], stdout=subprocess.PIPE)
    assert process.stdout is not None
    h = hashlib.sha256()
    for chunk in iter(lambda: process.stdout.read(1024 * 1024), b""):
        h.update(chunk)
    if process.wait() != 0:
        raise AcceptanceBlocked(f"unable to read installed APK for {package} on {serial}")
    return h.hexdigest()


def online_devices(adb_bin: str) -> list[str]:
    output = run(adb_bin, "devices", timeout=15).stdout
    return [line.split()[0] for line in output.splitlines()[1:] if len(line.split()) >= 2 and line.split()[1] == "device"]


def has_leanback(adb_bin: str, serial: str) -> bool:
    return "feature:android.software.leanback" in adb_text(adb_bin, serial, "shell", "pm", "list", "features")


def classify_devices(adb_bin: str) -> tuple[str, str]:
    devices = online_devices(adb_bin)
    phone_requested = os.environ.get("STUDYFLOW_PHONE_SERIAL", "").strip()
    tv_requested = os.environ.get("STUDYFLOW_TV_SERIAL", "").strip()
    if phone_requested or tv_requested:
        if not phone_requested or not tv_requested:
            raise AcceptanceBlocked("set both STUDYFLOW_PHONE_SERIAL and STUDYFLOW_TV_SERIAL together")
        if phone_requested not in devices or tv_requested not in devices or phone_requested == tv_requested:
            raise AcceptanceBlocked("requested phone/TV serials must be distinct online ADB devices")
        phone, tv = phone_requested, tv_requested
    else:
        if len(devices) != 2:
            raise AcceptanceBlocked(f"TVOV26 requires exactly two online ADB devices; found {len(devices)}")
        leanback = [serial for serial in devices if has_leanback(adb_bin, serial)]
        non_leanback = [serial for serial in devices if serial not in leanback]
        if len(leanback) != 1 or len(non_leanback) != 1:
            raise AcceptanceBlocked("could not classify exactly one Leanback TV and one non-TV Android phone")
        tv, phone = leanback[0], non_leanback[0]
    if not has_leanback(adb_bin, tv):
        raise AcceptanceBlocked(f"selected TV {tv} does not expose android.software.leanback")
    if has_leanback(adb_bin, phone):
        raise AcceptanceBlocked(f"selected phone {phone} unexpectedly exposes android.software.leanback")
    return phone, tv


def find_release_apk(module: str, explicit: str | None) -> Path:
    if explicit:
        path = Path(explicit).expanduser().resolve()
        if not path.is_file():
            raise AcceptanceBlocked(f"Release APK does not exist: {path}")
        return path
    candidates = sorted((ROOT / module / "build/outputs/apk/release").glob("*.apk"))
    signed = [p for p in candidates if "unsigned" not in p.name.lower()]
    if len(signed) == 1:
        return signed[0]
    if len(candidates) == 1:
        return candidates[0]
    raise AcceptanceBlocked(f"expected one Release APK for {module}; found {len(candidates)}")


def require_signed(apksigner: str, path: Path) -> None:
    result = run(apksigner, "verify", "--verbose", str(path), timeout=30, check=False)
    if result.returncode:
        raise AcceptanceBlocked(
            f"{path.name} is not installably signed. Configure the shared STUDYFLOW_RELEASE_* signing inputs before TVOV26."
        )


def install(adb_bin: str, serial: str, apk: Path) -> None:
    result = adb(adb_bin, serial, "install", "-r", "-d", str(apk), timeout=180, check=False)
    text = (result.stdout + "\n" + result.stderr).strip()
    if result.returncode or "Success" not in text:
        raise AcceptanceBlocked(f"Release APK install/upgrade failed on {serial}: {text}")


def grant_nearby(adb_bin: str, serial: str, package: str) -> None:
    sdk = int(adb_text(adb_bin, serial, "shell", "getprop", "ro.build.version.sdk") or "0")
    if sdk >= 33:
        adb(adb_bin, serial, "shell", "pm", "grant", package, "android.permission.NEARBY_WIFI_DEVICES", check=False)


def ui_xml(adb_bin: str, serial: str) -> ET.Element:
    remote = "/sdcard/tvov26-window.xml"
    adb(adb_bin, serial, "shell", "uiautomator", "dump", remote, timeout=20, check=False)
    xml = adb(adb_bin, serial, "exec-out", "cat", remote, timeout=20).stdout
    if not xml.strip().startswith("<?xml"):
        raise AcceptanceBlocked(f"uiautomator dump unavailable on {serial}")
    return ET.fromstring(xml)


def node_label(node: ET.Element) -> str:
    return " ".join(filter(None, (node.attrib.get("text", ""), node.attrib.get("content-desc", "")))).strip()


def bounds_center(raw: str) -> tuple[int, int]:
    match = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", raw or "")
    if not match:
        raise AcceptanceBlocked(f"invalid UI bounds: {raw!r}")
    x1, y1, x2, y2 = map(int, match.groups())
    return (x1 + x2) // 2, (y1 + y2) // 2


def find_node(root: ET.Element, text: str, *, exact: bool = False) -> ET.Element | None:
    target = text.casefold()
    for node in root.iter("node"):
        label = node_label(node).casefold()
        if (label == target if exact else target in label):
            return node
    return None


def all_ui_text(root: ET.Element) -> str:
    return "\n".join(node_label(node) for node in root.iter("node") if node_label(node))


def swipe_up(adb_bin: str, serial: str) -> None:
    size = adb_text(adb_bin, serial, "shell", "wm", "size")
    match = re.search(r"(\d+)x(\d+)", size)
    width, height = (1080, 1920) if not match else (int(match.group(1)), int(match.group(2)))
    adb(adb_bin, serial, "shell", "input", "swipe", str(width // 2), str(int(height * .78)), str(width // 2), str(int(height * .30)), "350")
    time.sleep(.45)


def find_with_scroll(adb_bin: str, serial: str, text: str, *, exact: bool = False, attempts: int = 8) -> ET.Element | None:
    for _ in range(attempts):
        root = ui_xml(adb_bin, serial)
        node = find_node(root, text, exact=exact)
        if node is not None:
            return node
        swipe_up(adb_bin, serial)
    return None


def click_text(adb_bin: str, serial: str, text: str, *, exact: bool = False, scroll: bool = False) -> None:
    node = find_with_scroll(adb_bin, serial, text, exact=exact) if scroll else find_node(ui_xml(adb_bin, serial), text, exact=exact)
    if node is None:
        raise AcceptanceBlocked(f"UI text not found on {serial}: {text!r}")
    x, y = bounds_center(node.attrib.get("bounds", ""))
    adb(adb_bin, serial, "shell", "input", "tap", str(x), str(y))
    time.sleep(.6)


def wait_text(adb_bin: str, serial: str, text: str, *, timeout: float = 20.0, exact: bool = False) -> ET.Element:
    deadline = time.time() + timeout
    last = ""
    while time.time() < deadline:
        root = ui_xml(adb_bin, serial)
        node = find_node(root, text, exact=exact)
        if node is not None:
            return node
        last = all_ui_text(root)
        time.sleep(.7)
    raise AcceptanceBlocked(f"timed out waiting for {text!r} on {serial}; visible UI: {last[-1200:]}")


def input_pairing_code(adb_bin: str, serial: str, code: str) -> None:
    root = ui_xml(adb_bin, serial)
    editable = next((n for n in root.iter("node") if n.attrib.get("class", "").endswith("EditText") or n.attrib.get("editable") == "true"), None)
    if editable is None:
        editable = find_with_scroll(adb_bin, serial, "6-digit TV code")
    if editable is None:
        raise AcceptanceBlocked("phone pairing code field is not reachable")
    x, y = bounds_center(editable.attrib.get("bounds", ""))
    adb(adb_bin, serial, "shell", "input", "tap", str(x), str(y))
    adb(adb_bin, serial, "shell", "input", "keyevent", "KEYCODE_MOVE_END", check=False)
    adb(adb_bin, serial, "shell", "input", "text", code)
    time.sleep(.4)


def wait_phone_cast_live(adb_bin: str, phone: str, timeout: float = 35.0) -> None:
    deadline = time.time() + timeout
    while time.time() < deadline:
        root = ui_xml(adb_bin, phone)
        text = all_ui_text(root)
        if "Cast status: LIVE" in text:
            return
        swipe_up(adb_bin, phone)
    raise AcceptanceBlocked("phone never observed exact LIVE cast acknowledgment")


def device_info(adb_bin: str, serial: str) -> dict[str, str]:
    def prop(name: str) -> str:
        return adb_text(adb_bin, serial, "shell", "getprop", name)
    return {
        "serial": serial,
        "manufacturer": prop("ro.product.manufacturer"),
        "model": prop("ro.product.model"),
        "apiLevel": prop("ro.build.version.sdk"),
        "fingerprint": prop("ro.build.fingerprint"),
    }


def main() -> None:
    parser = argparse.ArgumentParser(description="TVOV26 fail-closed real phone + Android TV acceptance")
    parser.add_argument("--phone-apk")
    parser.add_argument("--tv-apk")
    parser.add_argument("--report", default=str(REPORT))
    args = parser.parse_args()

    adb_bin = shutil.which("adb")
    apksigner = shutil.which("apksigner")
    if not adb_bin:
        raise AcceptanceBlocked("adb is required; TVOV26 never skips physical acceptance")
    if not apksigner:
        raise AcceptanceBlocked("apksigner is required for exact Release-artifact acceptance")

    checks: dict[str, dict[str, str]] = {item: {"status": "BLOCKED", "evidence": ""} for item in REQUIRED_CHECKS}
    report_path = Path(args.report).resolve()
    report_path.parent.mkdir(parents=True, exist_ok=True)
    phone = tv = ""
    phone_apk = tv_apk = None
    failure = None

    def passed(check: str, evidence: str) -> None:
        checks[check] = {"status": "PASS", "evidence": evidence}
        print(f"TVOV26 {check}: PASS — {evidence}")

    try:
        phone, tv = classify_devices(adb_bin)
        passed("two_devices_classified", f"phone={phone}; tv={tv}; TV exposes Leanback and phone does not")

        phone_apk = find_release_apk("app", args.phone_apk)
        tv_apk = find_release_apk("app-tv", args.tv_apk)
        require_signed(apksigner, phone_apk)
        require_signed(apksigner, tv_apk)
        install(adb_bin, phone, phone_apk)
        install(adb_bin, tv, tv_apk)
        grant_nearby(adb_bin, phone, PHONE_PACKAGE)
        grant_nearby(adb_bin, tv, TV_PACKAGE)
        phone_local_hash, tv_local_hash = sha256(phone_apk), sha256(tv_apk)
        phone_installed_hash = installed_apk_sha256(adb_bin, phone, PHONE_PACKAGE)
        tv_installed_hash = installed_apk_sha256(adb_bin, tv, TV_PACKAGE)
        if phone_local_hash != phone_installed_hash or tv_local_hash != tv_installed_hash:
            raise AcceptanceBlocked("installed APK bytes do not match the exact Release artifacts")
        tv_dump = adb_text(adb_bin, tv, "shell", "dumpsys", "package", TV_PACKAGE)
        if not re.search(r"versionCode=26(?:\s|$)", tv_dump) or f"versionName={TV_VERSION_NAME}" not in tv_dump:
            raise AcceptanceBlocked("installed TV Release identity is not TVOV26")
        passed("signed_release_artifacts_installed", f"phone_sha256={phone_local_hash}; tv_sha256={tv_local_hash}")

        # Reset only TV state so fresh pairing is deterministic without destroying real phone StudyFlow data.
        adb(adb_bin, tv, "shell", "pm", "clear", TV_PACKAGE)
        grant_nearby(adb_bin, tv, TV_PACKAGE)
        adb(adb_bin, tv, "shell", "am", "start", "-W", "-n", TV_ACTIVITY, timeout=30)
        wait_text(adb_bin, tv, "Pair phone", timeout=20)
        for key in ("KEYCODE_DPAD_RIGHT", "KEYCODE_DPAD_LEFT", "KEYCODE_DPAD_DOWN", "KEYCODE_DPAD_UP"):
            adb(adb_bin, tv, "shell", "input", "keyevent", key)
        if not adb_text(adb_bin, tv, "shell", "pidof", TV_PACKAGE):
            raise AcceptanceBlocked("TV process died during D-pad smoke")
        passed("tv_leanback_launch_dpad", "optimized Release launched on Leanback device and survived D-pad navigation")

        click_text(adb_bin, tv, "Pair phone", exact=True)
        wait_text(adb_bin, tv, "Manage pairing", timeout=10)
        click_text(adb_bin, tv, "Manage pairing", exact=True)
        pairing_node = wait_text(adb_bin, tv, "Pairing code:", timeout=10)
        pairing_text = node_label(pairing_node)
        code_match = re.search(r"Pairing code:\s*(\d{6})", pairing_text)
        if not code_match:
            code_match = re.search(r"Pairing code:\s*(\d{6})", all_ui_text(ui_xml(adb_bin, tv)))
        if not code_match:
            raise AcceptanceBlocked("could not read the six-digit pairing code from the real TV UI")
        code = code_match.group(1)

        adb(adb_bin, phone, "shell", "am", "force-stop", PHONE_PACKAGE)
        adb(adb_bin, phone, "shell", "am", "start", "-W", "-a", PHONE_CONTROLLER_ACTION, "-n", PHONE_ACTIVITY, timeout=30)
        wait_text(adb_bin, phone, "StudyFlow TV controller", timeout=30)
        passed("phone_controller_launch", "Release phone app opened the production controller action")

        # Discovery can take a few seconds. Scan explicitly, then select the only StudyFlow TV.
        if find_node(ui_xml(adb_bin, phone), "Scan", exact=True):
            click_text(adb_bin, phone, "Scan", exact=True)
        tv_model = adb_text(adb_bin, tv, "shell", "getprop", "ro.product.model")
        receiver = wait_text(adb_bin, phone, tv_model, timeout=30)
        x, y = bounds_center(receiver.attrib.get("bounds", ""))
        adb(adb_bin, phone, "shell", "input", "tap", str(x), str(y))
        time.sleep(.7)
        if find_node(ui_xml(adb_bin, phone), "Forget saved TV on this phone"):
            click_text(adb_bin, phone, "Forget saved TV on this phone", scroll=True)
        input_pairing_code(adb_bin, phone, code)
        click_text(adb_bin, phone, "Pair", exact=True, scroll=True)
        wait_text(adb_bin, phone, "Paired with", timeout=20)
        passed("fresh_pairing", f"fresh six-digit pairing completed with TV model {tv_model}")

        click_text(adb_bin, phone, "Today", exact=True, scroll=True)
        wait_text(adb_bin, tv, "Today", timeout=15)
        passed("authenticated_navigation", "paired phone opened Today on the TV through authenticated control")

        # Casting requires a real default plan; fail rather than seed production data.
        phone_text = all_ui_text(ui_xml(adb_bin, phone))
        if "No default plan selected" in phone_text:
            raise AcceptanceBlocked("phone has no default StudyFlow plan; create/select a real or sample plan before TVOV26 physical acceptance")
        click_text(adb_bin, phone, "Start cast", exact=True, scroll=True)
        wait_phone_cast_live(adb_bin, phone)
        tv_text = all_ui_text(ui_xml(adb_bin, tv))
        if "Waiting for current StudyFlow presentation data from a paired phone." in tv_text:
            raise AcceptanceBlocked("TV still shows the waiting state after the phone reported LIVE cast acknowledgment")
        passed("live_cast_acknowledged", "phone observed exact presentation receipt and TV rendered non-waiting canonical state")

        # Try real canonical persistence controls; no hidden seed/test endpoint is permitted.
        mutation_passed = False
        for label in ("Start", "Mark for review", "Complete"):
            try:
                click_text(adb_bin, phone, label, exact=True, scroll=True)
                deadline = time.time() + 12
                while time.time() < deadline:
                    text = all_ui_text(ui_xml(adb_bin, phone))
                    if "Last result: domain-persisted" in text:
                        mutation_passed = True
                        break
                    time.sleep(.6)
                if mutation_passed:
                    passed("canonical_domain_mutation", f"real controller action {label!r} persisted through the phone-owned domain executor")
                    break
            except AcceptanceBlocked:
                continue
        if not mutation_passed:
            raise AcceptanceBlocked("no real projected item accepted a canonical mutation; prepare a default plan with an actionable session/question/review item")

        # Restart TV: phone remains alive and must rediscover/resync the exact current snapshot.
        adb(adb_bin, tv, "shell", "am", "force-stop", TV_PACKAGE)
        adb(adb_bin, tv, "shell", "am", "start", "-W", "-n", TV_ACTIVITY, timeout=30)
        wait_phone_cast_live(adb_bin, phone, timeout=45)
        tv_text = all_ui_text(ui_xml(adb_bin, tv))
        if "Waiting for current StudyFlow presentation data from a paired phone." in tv_text:
            raise AcceptanceBlocked("TV restart did not rehydrate canonical presentation state")
        passed("tv_restart_resync", "TV process restart restored pairing and phone-driven canonical presentation")

        # Restart phone: TVOV26 persisted cast intent must resume the same default-plan stream.
        adb(adb_bin, phone, "shell", "am", "force-stop", PHONE_PACKAGE)
        adb(adb_bin, phone, "shell", "am", "start", "-W", "-a", PHONE_CONTROLLER_ACTION, "-n", PHONE_ACTIVITY, timeout=30)
        wait_text(adb_bin, phone, "StudyFlow TV controller", timeout=30)
        wait_phone_cast_live(adb_bin, phone, timeout=50)
        passed("phone_restart_resync", "phone process restart restored credential + explicit cast intent and regained exact LIVE acknowledgment")

        click_text(adb_bin, phone, "Stop cast", exact=True, scroll=True)
        wait_text(adb_bin, phone, "Cast status: STOPPED", timeout=25)
        wait_text(adb_bin, tv, "Waiting for current StudyFlow presentation data from a paired phone.", timeout=20)
        passed("stop_cast_waiting_state", "STOP_CAST cleared remote presentation while keeping the paired controller session")

        click_text(adb_bin, phone, "Revoke pairing", exact=True, scroll=True)
        wait_text(adb_bin, phone, "Last result: pairing-revoked", timeout=20)
        click_text(adb_bin, tv, "Settings", exact=True, scroll=False)
        wait_text(adb_bin, tv, "pairing=revoked", timeout=15)
        phone_ui = all_ui_text(ui_xml(adb_bin, phone))
        if "Study session controls" in phone_ui or "Open on TV" in phone_ui:
            raise AcceptanceBlocked("paired control surfaces remained enabled after revoke")
        passed("revoke_blocks_control", "remote revoke reached TV and paired control surfaces became unavailable")

    except Exception as error:
        failure = str(error)
    finally:
        artifact = {}
        installed = {}
        if phone_apk and phone_apk.is_file():
            artifact["phone"] = {"path": str(phone_apk), "sha256": sha256(phone_apk)}
        if tv_apk and tv_apk.is_file():
            artifact["tv"] = {"path": str(tv_apk), "sha256": sha256(tv_apk)}
        if phone:
            try:
                installed["phoneSha256"] = installed_apk_sha256(adb_bin, phone, PHONE_PACKAGE)
            except Exception:
                pass
        if tv:
            try:
                installed["tvSha256"] = installed_apk_sha256(adb_bin, tv, TV_PACKAGE)
            except Exception:
                pass
        status = "PASS" if failure is None and all(checks[item]["status"] == "PASS" for item in REQUIRED_CHECKS) else "BLOCKED"
        report = {
            "schemaVersion": 1,
            "overlay": "TVOV26",
            "status": status,
            "physicalRun": True,
            "generatedAtUtc": datetime.now(timezone.utc).isoformat(),
            "failure": failure,
            "phonePackage": PHONE_PACKAGE,
            "tvPackage": TV_PACKAGE,
            "tvVersionCode": TV_VERSION_CODE,
            "tvVersionName": TV_VERSION_NAME,
            "artifacts": artifact,
            "installed": installed,
            "devices": {
                "phone": device_info(adb_bin, phone) if phone else None,
                "tv": device_info(adb_bin, tv) if tv else None,
            },
            "checks": [{"id": item, **checks[item]} for item in REQUIRED_CHECKS],
        }
        report_path.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
        print(f"TVOV26 evidence: {report_path}")
        if status != "PASS":
            print(f"TVOV26_TWO_DEVICE_ACCEPTANCE: BLOCKED — {failure or 'required checks incomplete'}", file=sys.stderr)
            raise SystemExit(1)
        print("TVOV26_TWO_DEVICE_ACCEPTANCE: PASS")

if __name__ == "__main__":
    main()
