#!/usr/bin/env python3
from pathlib import Path
import tomllib

ROOT = Path(__file__).resolve().parents[1]
config = tomllib.loads((ROOT / '.devtool.toml').read_text(encoding='utf-8'))
target = config.get('targets', {}).get('studyflow_tv')
assert isinstance(target, dict), 'studyflow_tv target missing'
assert target.get('type') == 'android', 'studyflow_tv must use Devtool Android runner'
assert target.get('execution_environment') == 'native-termux', 'studyflow_tv should remain native-Termux first'
android = target.get('android')
assert isinstance(android, dict), 'studyflow_tv Android runner options missing'
assert android.get('variant') == 'debug', 'studyflow_tv debug variant contract changed'
assert android.get('aapt2_provider') == 'termux', 'studyflow_tv must use Termux AAPT2'
assert android.get('prefer_native_termux') is True, 'studyflow_tv must prefer native Termux'
assert android.get('ndk_version') == '29.0.14206865', 'studyflow_tv NDK 29 baseline changed'
tasks = target.get('tasks')
assert isinstance(tasks, dict), 'studyflow_tv task map missing'
build = tasks.get('build')
assert isinstance(build, list), 'studyflow_tv build task list missing'
assert ':app-tv:assembleDebug' in build, 'studyflow_tv build must assemble the TV debug APK'
assert ':app:assembleDebug' not in build, 'studyflow_tv build must not assemble the mobile app'
assert 'verifyStudyFlowTvDevtoolAndroidRunner' in build, 'TV runner regression guard must be part of build'
phases = target.get('validation', {}).get('phases', {})
assert 'verifyStudyFlowTvDevtoolAndroidRunner' in phases.get('preflight', []), 'TV runner guard must run in validation preflight'
print('STUDYFLOW_TV_DEVTOOL_ANDROID_RUNNER: PASS')
