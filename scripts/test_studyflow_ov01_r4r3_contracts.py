#!/usr/bin/env python3
"""Adversarial source-only tests for the R4R3 hosted CI regressions."""
from pathlib import Path
import importlib.util
import re
import tempfile
import tomllib
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]


def main():
    workflow = (ROOT/'.github/workflows/studyflow-ci.yml').read_text()
    config = tomllib.loads((ROOT/'.devtool.toml').read_text())
    android = config['targets']['studyflow_android']['tasks']['test']
    assert ':app:testReleaseUnitTest' in android
    assert ':app:testDebugUnitTest' not in android
    assert "test_task: ':app:testReleaseUnitTest'" in workflow
    assert 'testBuildType = "release"' in (ROOT/'app/build.gradle.kts').read_text()
    # Contract requires both web and deep profiles to install exactly pinned Pillow.
    requirements = (ROOT/'web/requirements-test.txt').read_text()
    assert [ln.strip() for ln in requirements.splitlines()
            if ln.strip() and not ln.lstrip().startswith('#')] == ['Pillow==12.0.0']
    assert workflow.count('actions/setup-python@v5') == 2
    assert workflow.count("python-version: '3.12'") == 2
    assert '-r requirements-test.txt' in workflow
    assert '-r web/requirements-test.txt' in workflow
    assert 'requirements-test.txt' in config['targets']['studyflow_web']['inputs']
    assert 'from PIL import Image' in (ROOT/'web/tests/test_w13_pwa_offline.py').read_text()
    # A missing prereq or a mismatched test build type must not be silently accepted.
    spec = importlib.util.spec_from_file_location('ci_profile', ROOT/'scripts/studyflow_ci_gradle_profile.py')
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    plan = mod.profile_plan('android-contracts')
    assert plan['qualified_release'] is False
    assert ':app:testReleaseUnitTest' in plan['tasks']
    with tempfile.TemporaryDirectory(prefix='studyflow-r4r3-negative-') as t:
        tmp=Path(t); (tmp/'app').mkdir()
        source=(ROOT/'.devtool.toml').read_text()
        build=(ROOT/'app/build.gradle.kts').read_text()
        (tmp/'app/build.gradle.kts').write_text(build)
        with patch.object(mod,'ROOT',tmp):
            for replacement in (':app:testDebugUnitTest', ':app:assembleRelease', ':app:bundleRelease',
                                ':app:packageRelease', ':app:signReleaseBundle'):
                mutated=source.replace('  ":app:testReleaseUnitTest",', f'  "{replacement}",',1)
                assert mutated != source
                (tmp/'.devtool.toml').write_text(mutated)
                try: mod.profile_plan('android-contracts')
                except ValueError: pass
                else: raise AssertionError(f'unsafe/missing task accepted: {replacement}')
            (tmp/'.devtool.toml').write_text(source)
            (tmp/'app/build.gradle.kts').write_text(build.replace('testBuildType = "release"','testBuildType = "debug"'))
            try: mod.profile_plan('android-contracts')
            except ValueError: pass
            else: raise AssertionError('mismatched testBuildType accepted')
    # Actual artifact output signing remains mandatory and source visible.
    build=(ROOT/'app/build.gradle.kts').read_text()
    assert 'name.startsWith("packageRelease") && name != "packageReleaseResources"' in build
    assert all(t in build for t in ('name == "assembleRelease"','name == "bundleRelease"',
                                    'name.startsWith("signRelease")','dependsOn(verifyPhoneReleaseSigningPreflight)'))
    print('STUDYFLOW_R4R3_NEGATIVE_CONTRACTS: PASS (no Gradle execution)')


if __name__ == '__main__': main()
