#!/usr/bin/env python3
"""Offline, secret-free structural checks for StudyFlow's Devtool/Actions bridge.

This is not a GitHub runner acceptance test or a release qualification.
"""
from __future__ import annotations

import argparse
import importlib.util
from pathlib import Path
import re
import sys
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def checked_text(relative: str) -> str:
    p = ROOT / relative
    require(p.is_file() and not p.is_symlink(), f"missing/unsafe file: {relative}")
    return p.read_text(encoding="utf-8")


def source_contract() -> None:
    settings = checked_text("settings.gradle.kts")
    for module in ('":app"', '":app-tv"', '":desktopApp"'):
        require(module in settings, f"Gradle module not included: {module}")
    for module, pkg in (("app", "com.mikeyphw.studyflow"),
                        ("app-tv", "com.mikeyphw.studyflow.tv")):
        gradle = checked_text(f"{module}/build.gradle.kts")
        require('id("studyflow.android.application")' in gradle,
                f"convention-plugin identity changed in {module}")
        require(f'applicationId = "{pkg}"' in gradle, f"package changed: {module}")
    convention = checked_text("build-logic/convention/src/main/kotlin/AndroidConvention.kt")
    require('compileSdk = 36' in convention and 'buildToolsVersion = "36.0.0"' in convention,
            "Android 36 build prerequisites changed")
    require('STUDYFLOW_NDK_VERSION = "29.0.14206865"' in convention, "pinned NDK changed")
    require('JavaVersion.VERSION_21' in convention, "Java source/target toolchain changed")
    require('gradle-9.7.1-bin.zip' in checked_text("gradle/wrapper/gradle-wrapper.properties"),
            "Gradle wrapper version changed")
    wrapper = ROOT / "gradle/wrapper/gradle-wrapper.jar"
    require(wrapper.is_file() and not wrapper.is_symlink(), "Gradle wrapper jar missing or unsafe")
    import hashlib
    expected_wrapper_sha = "7a9ce74cff467ca1bf60a4fcd9f05185acceda4d0f382434d393e17864262c5d"
    require(hashlib.sha256(wrapper.read_bytes()).hexdigest() == expected_wrapper_sha,
            "Unofficial Gradle wrapper JAR; use verified Gradle 9.7.1 wrapper")
    targets = checked_text(".devtool.toml")
    for target in ("studyflow_android", "studyflow_tv", "studyflow_desktop", "studyflow_web"):
        require(f"[targets.{target}]" in targets, f"Devtool target unavailable: {target}")
    require('"build":' in checked_text("web/package.json"), "web npm build task missing")


def workflow_contract() -> None:
    android_path = ".github/workflows/devtool-android-build.yml"
    ci_path = ".github/workflows/studyflow-ci.yml"
    android = checked_text(android_path)
    ci = checked_text(ci_path)
    # Basic fail-closed checks run without PyYAML (not preinstalled on GitHub runners).
    for needle in ("workflow_dispatch:", "project_dir:", "module:", "variant:",
                   "format:", "options: [app, app-tv]", "options: [debug]",
                   "options: [apk, aab]", "test \"$VARIANT\" = debug",
                   "test \"$(git rev-parse HEAD)\" = \"$GITHUB_SHA\"",
                   "sdkmanager --install 'platforms;android-36' 'build-tools;36.0.0' 'ndk;29.0.14206865'",
                   '"$ANDROID_HOME/build-tools/36.0.0/apksigner" verify --print-certs',  "--verify-aab",
                   "actions/upload-artifact@v4", "if-no-files-found: error"):
        require(needle in android, f"Android workflow lacks mandatory control: {needle}")
    for needle in ("workflow_dispatch:", "pull_request:", "push:",
                   "options: [all, android, desktop, web, deep]", "expected_sha:", "deep-contracts:", "npm ci", "npm test",
                   "npm run build", ":desktopApp:test", ":app:testReleaseUnitTest",
                   ":app-tv:testDebugUnitTest", "lintDebug", '"$ANDROID_HOME/build-tools/36.0.0/apksigner" verify --print-certs'):
        require(needle in ci, f"CI workflow lacks required lane/control: {needle}")
    for name, content in ((android_path, android), (ci_path, ci)):
        require(re.search(r"(?m)^permissions:\s*\n\s+contents: read\s*$", content) is not None,
                f"{name}: missing least-privilege workflow permissions")
        for banned in ('secrets.', 'pull_request_target:', 'permissions: write-all', 'DEVTOOL_RELEASE_KEYSTORE_B64'):
            require(banned not in content, f"{name}: unsafe credential or trigger token {banned!r}")
    require('test "$EXPECTED_SHA" = "$GITHUB_SHA"' in android and
            'test "$EXPECTED_SHA" = "$GITHUB_SHA"' in ci,
            "Both dispatch workflows must reject a mismatched reviewed source SHA")
    for path, source in ((android_path, android), (ci_path, ci)):
        require(re.search(r"(?ms)^      expected_sha:\n(?:        [^\n]*\n)*?        required: true\s*$", source) is not None,
                f"{path}: expected_sha must be required for manual dispatch")
    require("github.event_name == 'workflow_dispatch' && github.run_id || github.ref" in ci,
            "Manual deep jobs must not be cancelled by ordinary push CI")
    require("if-no-files-found: warn" in ci and "--init-receipt" in ci,
            "Deep receipt must be initialized before hosted Gradle setup and uploaded on failure")
    require("Verify successful hosted Gradle receipt" in ci,
            "Deep successful results must verify a completed passed receipt")
    require("python3 scripts/studyflow_ci_gradle_profile.py --run" in ci,
            "CI deep contract lane must execute the canonical Devtool Gradle profile")
    require("matrix.profile == 'android-contracts' || matrix.profile == 'parity-contracts'" in ci
            and "Install pinned TypeScript environment for native/Web contracts" in ci,
            "Android and parity deep profiles require pinned TypeScript installed from web/npm lock")
    require("assembleRelease" not in android and "bundleRelease" not in android,
            "debug-only Actions workflow must not package release")
    require(":app:testDebugUnitTest" not in ci and ":app:assembleRelease" not in ci
            and ":app:bundleRelease" not in ci and ":app:packageRelease" not in ci,
            "unsigned CI must select configured phone Release JVM tests, never Release packaging")
    require('testBuildType = "release"' in checked_text('app/build.gradle.kts'),
            "phone test task does not match configured testBuildType")
    reqs = checked_text('web/requirements-test.txt')
    require([line.strip() for line in reqs.splitlines() if line.strip() and not line.lstrip().startswith('#')]
            == ['Pillow==12.0.0'], 'Pillow test dependency is not exactly pinned')
    for fragment in ("actions/setup-python@v5", "python-version: '3.12'",
                     "-r requirements-test.txt", "-r web/requirements-test.txt"):
        require(fragment in ci, f"CI missing Python image test dependency setup: {fragment}")
    import tomllib
    devtool_cfg = tomllib.loads(checked_text('.devtool.toml'))
    web_cfg = devtool_cfg['targets']['studyflow_web']
    require(web_cfg['root'] == 'web' and 'src/**' in web_cfg['inputs']
            and 'package.json' in web_cfg['inputs'] and 'requirements-test.txt' in web_cfg['inputs'] and 'dist/**' in web_cfg['outputs']
            and not any(p.startswith('web/') for p in web_cfg['inputs'] + web_cfg['outputs']),
            'Devtool web input/output paths must be target-root relative')
    signing = checked_text('app/build.gradle.kts')
    require('name.startsWith("packageRelease") && name != "packageReleaseResources"' in signing and
            'name.startsWith("packageRelease") ||' not in signing and
            'dependsOn(verifyPhoneReleaseSigningPreflight)' in signing,
            'Release signing gate must cover output packaging, not unit-test resource packaging')

    # The full schema parse is an optional additional check, not a hidden CI dependency.
    if importlib.util.find_spec("yaml") is not None:
        import yaml
        for relative in (android_path, ci_path):
            obj = yaml.safe_load(checked_text(relative))
            require(isinstance(obj, dict) and isinstance(obj.get("jobs"), dict),
                    f"invalid YAML jobs: {relative}")
            triggers = obj.get("on", obj.get(True))
            require(isinstance(triggers, dict) and "workflow_dispatch" in triggers,
                    f"missing dispatch in parsed YAML: {relative}")
            if relative == android_path:
                entries = triggers["workflow_dispatch"]["inputs"]
                require(set(entries) == {"expected_sha", "project_dir", "module", "variant", "format"},
                        "Android inputs have changed")
                for field, expected in (("project_dir", ["."]), ("module", ["app", "app-tv"]),
                                        ("variant", ["debug"]), ("format", ["apk", "aab"])):
                    require(entries[field].get("options") == expected,
                            f"unexpected Android dispatch choices: {field}")
            else:
                require({"source", "web", "desktop", "android", "deep-contracts"}.issubset(obj["jobs"]),
                        "CI lost a supported client lane")



def verify_aab_file(path: Path) -> None:
    """Verify exactly one JAR signer and its cryptographic AAB signature.

    Strict jarsigner rejects otherwise-correct Android debug self-signed certs, so
    follow Devtool's non-strict, explicit-verified-verdict contract instead.
    """
    require(path.is_file() and not path.is_symlink() and path.stat().st_size > 0,
            "missing or unsafe AAB")
    try:
        with zipfile.ZipFile(path) as archive:
            names = {entry.filename.upper() for entry in archive.infolist() if not entry.is_dir()}
    except (zipfile.BadZipFile, OSError, ValueError) as e:
        raise AssertionError("invalid AAB ZIP") from e
    signatures = {n[9:-3] for n in names if n.startswith('META-INF/')
                  and n.count('/') == 1 and n.endswith('.SF')}
    blocks = {n[9:-4] for n in names if n.startswith('META-INF/')
              and n.count('/') == 1 and n.endswith(('.RSA', '.DSA'))}
    blocks |= {n[9:-3] for n in names if n.startswith('META-INF/')
               and n.count('/') == 1 and n.endswith('.EC')}
    require(len(signatures) == 1 and signatures == blocks and bool(next(iter(signatures), '')),
            "unsigned/multiple-signer AAB rejected")
    try:
        outcome = subprocess.run(['jarsigner', '-verify', '-verbose', str(path)],
                                 capture_output=True, text=True, timeout=60, check=False)
    except (OSError, subprocess.TimeoutExpired) as e:
        raise AssertionError('AAB signature tool not available') from e
    report = outcome.stdout + '\n' + outcome.stderr
    verdicts = [line.strip().lower() for line in report.splitlines()
                if line.strip().lower() in ('jar verified.', 'jar is unsigned.',
                                             'jar verified, with signer errors.')]
    require(outcome.returncode == 0 and verdicts == ['jar verified.']
            and 'unsigned entries' not in report.lower()
            and re.search(r'(?m)^\s*\?\s+\d+\s+', report) is None,
            'AAB JAR signature incomplete, invalid, or unsigned')
    print('StudyFlow AAB signature: verified (debug candidate, not release)')

def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-only", action="store_true", help="compatibility alias; all tests are static")
    parser.add_argument("--verify-aab", type=Path, help="verify AAB JAR signature (no release claim)")
    args = parser.parse_args()
    try:
        if args.verify_aab is not None:
            verify_aab_file(args.verify_aab)
            return 0
        source_contract()
        workflow_contract()
    except (AssertionError, OSError, ValueError) as error:
        print(f"StudyFlow GitHub Actions integration: FAIL: {error}", file=sys.stderr)
        return 1
    print("StudyFlow GitHub Actions integration: static PASS (no remote CI executed)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
