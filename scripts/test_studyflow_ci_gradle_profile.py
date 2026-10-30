#!/usr/bin/env python3
"""Source-only tests; the executable used below is a harmless stub, not Gradle."""
from __future__ import annotations
import importlib.util
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / 'scripts/studyflow_ci_gradle_profile.py'
spec = importlib.util.spec_from_file_location('studyflow_profile_under_test', SRC)
profile = importlib.util.module_from_spec(spec)
spec.loader.exec_module(profile)


def git(repo: Path, *args: str) -> str:
    return subprocess.run(['git','-C',str(repo),*args], check=True, text=True, capture_output=True).stdout.strip()


def test() -> None:
    with tempfile.TemporaryDirectory(prefix='studyflow-gh-only-source-test-') as folder:
        repo = Path(folder)
        shutil.copyfile(ROOT / '.devtool.toml', repo / '.devtool.toml')
        (repo / 'app').mkdir()
        shutil.copyfile(ROOT / 'app/build.gradle.kts', repo / 'app/build.gradle.kts')
        wrapper = repo / 'gradlew'
        marker = repo / 'stub-invoked'
        # This stub only writes a marker and exits 17; it is NOT Gradle.
        wrapper.write_text('#!/bin/sh\nprintf "%s\\n" "$@" > "' + str(marker) + '"\nexit 17\n')
        wrapper.chmod(0o700)
        git(repo, 'init', '-q')
        git(repo, '-c', 'user.name=StudyFlow Test', '-c', 'user.email=test@invalid.example',
            'commit', '--allow-empty', '-q', '-m', 'test')
        sha = git(repo, 'rev-parse', 'HEAD')
        original_root = profile.ROOT
        try:
            profile.ROOT = repo
            p = profile.profile_plan('android-contracts')
            assert len(p['tasks']) > 30 and p['target'] == 'studyflow_android'
            assert p['tasks'] == list(dict.fromkeys(p['tasks']))
            receipt = repo.parent / (repo.name + '-receipt.json')
            try:
                with patch.dict(os.environ, {'GITHUB_ACTIONS': '', 'CI': '', 'RUNNER_OS':'Linux'}):
                    try:
                        profile.run('android-contracts', receipt)
                        assert False, 'Local Gradle dispatch was not rejected'
                    except ValueError as e:
                        assert 'outside GitHub Actions' in str(e)
                assert not marker.exists(), 'Local call invoked stub'
                env = {'GITHUB_ACTIONS': 'true', 'CI':'true', 'RUNNER_OS':'Linux',
                       'GITHUB_EVENT_NAME':'workflow_dispatch', 'GITHUB_SHA':sha,
                       'GITHUB_RUN_ID':'123', 'GITHUB_RUN_ATTEMPT':'2'}
                # Source-only receipt exists before Gradle setup; no stub dispatched.
                with patch.dict(os.environ, env):
                    profile.init_receipt('android-contracts', receipt)
                preflight_receipt = json.loads(receipt.read_text())
                assert preflight_receipt['status'] == 'blocked_before_gradle'
                assert preflight_receipt['failure_stage'] == 'hosted_prerequisites'
                assert preflight_receipt['build_executed'] is False
                assert preflight_receipt['git_sha'] == sha
                assert not marker.exists()
                with patch.dict(os.environ, env):
                    assert profile.run('android-contracts', receipt) == 17
                result = json.loads(receipt.read_text())
                assert result['exit_code'] == 17 and result['status'] == 'failed'
                assert result['qualified_release'] is False
                assert result['native_exo_gradle_execution'] is False
                assert result['gradle_argv'][0] == './gradlew'
                assert result['git_sha'] == sha
                assert marker.exists(), 'The fake hosted runner stub was not invoked'
                marker.unlink()
                # A mismatched SHA must reject before even calling the stub.
                with patch.dict(os.environ, {**env, 'GITHUB_SHA':'0' * 40}):
                    try:
                        profile.run('android-contracts', receipt)
                        assert False, 'SHA mismatch accepted'
                    except ValueError as e:
                        assert 'mismatch' in str(e)
                assert not marker.exists()
                # Task command injection through the TOML config must be rejected.
                source = (repo / '.devtool.toml').read_text()
                prefix, tail = source.split('test = [', 1)
                tail = tail.replace('"verifyStudyFlowAndroidPreflightContracts"', '"--init-script=malicious.gradle"', 1)
                (repo / '.devtool.toml').write_text(prefix + 'test = [' + tail)
                try:
                    profile.profile_plan('android-contracts')
                    assert False, 'Invalid task token accepted'
                except ValueError as e:
                    assert 'Invalid Gradle task' in str(e)
            finally:
                receipt.unlink(missing_ok=True)
        finally:
            profile.ROOT = original_root
    print('STUDYFLOW_GITHUB_ONLY_GRADLE_NEGATIVE_TESTS: PASS (no Gradle executed)')

if __name__ == '__main__':
    test()
