#!/usr/bin/env python3
"""OV01-R4 source-only integration and regression acceptance (never invokes Gradle)."""
from __future__ import annotations
import importlib.util
import pathlib
import re
import subprocess
import sys
import tomllib
from unittest.mock import patch
ROOT = pathlib.Path(__file__).resolve().parents[1]


def assert_true(condition: bool, reason: str):
    if not condition:
        raise AssertionError(reason)


def run_source_verifier(script: str):
    result = subprocess.run([sys.executable, str(ROOT / 'scripts' / script)],
                            cwd=ROOT, capture_output=True, text=True, timeout=60)
    assert_true(result.returncode == 0,
                f'{script} failed: {result.stdout[-3000:]} {result.stderr[-3000:]}')


def main():
    for verifier in ('verify_ov01_repository_gradle_devtool_truth.py',
                     'verify_ov30_studyflow_web_target_seal.py',
                     'verify_desktop_dov08_persistent_workspace.py',
                     'verify_desktop_dov09_plan_content_lifecycle.py'):
        run_source_verifier(verifier)
    config = tomllib.loads((ROOT / '.devtool.toml').read_text())
    targets = config['targets']
    web = targets['studyflow_web']
    assert_true(web['root'] == 'web', 'Web Devtool target root changed')
    assert_true({'src/**', 'tests/**', 'scripts/**', 'package.json', 'package-lock.json',
                 'tsconfig.json', 'sw.js', 'requirements-test.txt'}.issubset(set(web['inputs'])),
                'Target-relative web inputs missing')
    assert_true('dist/**' in web['outputs'], 'Target-relative web output missing')
    from pathlib import PurePosixPath
    assert_true(str(PurePosixPath(web['root']) / 'package.json') == 'web/package.json',
                'Devtool target root must resolve package.json exactly once')
    for field in ('inputs', 'outputs'):
        assert_true(all(not p.startswith('web/') and not p.startswith('/') and '..' not in PurePosixPath(p).parts
                        for p in web[field]), f'Web {field} contains doubly-rooted or unsafe path')
    desktop = targets['studyflow_desktop']['tasks']['test']
    desktop_phases = targets['studyflow_desktop']['validation']['phases']['unit-test']
    required_desktop = {'verifyDesktopDov08PersistentWorkspace','verifyDesktopDov09PlanContentLifecycle'}
    assert_true(required_desktop.issubset(set(desktop)),
                'Desktop DOV08/DOV09 not in canonical Devtool test plan')
    assert_true(required_desktop.issubset(set(desktop_phases)),
                'Desktop DOV08/DOV09 missing from native Devtool validation phases')
    android = targets['studyflow_android']['tasks']['test']
    app_build = (ROOT / 'app/build.gradle.kts').read_text()
    assert_true('testBuildType = "release"' in app_build,
                'Phone JVM tests are not configured for Release')
    assert_true(':app:testReleaseUnitTest' in android, 'Deep CI lacks actual configured phone JVM test task')
    assert_true(':app:testDebugUnitTest' not in android, 'Deep CI selected nonexistent phone Debug JVM test task')
    workflow = (ROOT / '.github/workflows/studyflow-ci.yml').read_text()
    assert_true("test_task: ':app:testReleaseUnitTest'" in workflow,
                'Standard CI does not select the configured Release JVM test task')
    assert_true(":app:testDebugUnitTest" not in workflow and 'assembleRelease' not in workflow,
                'Unsigned standard CI accidentally requests nonexistent debug or packaged release tasks')
    assert_true('dependsOn(verifyPhoneReleaseSigningPreflight)' in app_build,
                'Actual Release packaging lost mandatory signing preflight')
    # Do not attach the signing gate to AGP packageReleaseResources: Release JVM
    # tests transitively require this resource task even when no APK/AAB is built.
    assert_true('name.startsWith("packageRelease") &&\n                name != "packageReleaseResources" &&\n                !name.contains("UnitTest")' in app_build,
                'Release signing must guard package outputs while excluding only resource preparation')
    assert_true('name.startsWith("packageRelease") ||' not in app_build,
                'Release resource packaging still triggers signing preflight')
    for expression in ('name == "assembleRelease"', 'name == "bundleRelease"',
                       'name.startsWith("signRelease")'):
        assert_true(expression in app_build, f'Actual signed output guard missing: {expression}')
    # The Android task profile includes verifiers whose Gradle dependsOn edges
    # still exercise Release *unit tests*. They are allowed unsigned, but only
    # because test/resource tasks no longer imply signed APK/AAB packaging.
    gradle_build = (ROOT / 'build.gradle.kts').read_text()
    assert_true('":app:testReleaseUnitTest"' in gradle_build,
                'Expected historical Release JVM unit-test dependencies changed')
    spec = importlib.util.spec_from_file_location('studyflow_ci_profile_r4',
                               ROOT / 'scripts/studyflow_ci_gradle_profile.py')
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    real = mod.profile_plan('android-contracts')
    assert_true(':app:testReleaseUnitTest' in real['tasks'], 'Hosted profile does not use configured Release JVM tests')
    with patch.dict(mod.PROFILES, {'android-contracts':('studyflow_android','test')}):
        assert_true(mod.profile_plan('android-contracts')['qualified_release'] is False,
                    'Unsigned deep plan improperly claims release qualification')
    # Negative mutation: a release task in the Devtool source config must be rejected
    # before any wrapper command can run. No source files are changed.
    import tempfile
    from unittest.mock import patch as mock_patch
    with tempfile.TemporaryDirectory(prefix='studyflow-r4-source-') as tmp:
        fake = pathlib.Path(tmp)
        contents = (ROOT / '.devtool.toml').read_text()
        mutated = contents.replace('  ":app:testReleaseUnitTest",',
                                   '  ":app:assembleRelease",', 1)
        assert_true(mutated != contents, 'Could not form negative profile fixture')
        (fake / '.devtool.toml').write_text(mutated)
        (fake / 'app').mkdir()
        (fake / 'app/build.gradle.kts').write_text(app_build)
        with mock_patch.object(mod,'ROOT',fake):
            try:
                mod.profile_plan('android-contracts')
            except ValueError as exc:
                assert_true('Release' in str(exc), str(exc))
            else:
                raise AssertionError('Release packaging mutation escaped unsigned profile policy')
    # Keep historical source provenance meaningful rather than accepting arbitrary
    # fake root manifests that only satisfy schema/type checks.
    import json
    history = json.loads((ROOT / 'devtool-overlay.json').read_text())
    assert_true(history['name'] == 'studyflow_failure_audit_ov02_sflp_programming_contracts_v1',
                'Root overlay provenance identity drifted')
    print('STUDYFLOW_OV01_R4R3_HOSTED_SOURCE_CONTRACTS: PASS (no Gradle execution)')


if __name__ == '__main__':
    main()
