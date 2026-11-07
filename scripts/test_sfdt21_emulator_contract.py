#!/usr/bin/env python3
"""Negative-first SFDT21 source/receipt tests, no Gradle, adb or network."""
from __future__ import annotations
import hashlib
import json
from pathlib import Path
import struct
import tempfile
import unittest
import zlib
import sfdt21_emulator_verify as v

ROOT = Path(__file__).resolve().parents[1]
SHA = 'a' * 40


def png_pixel(value: int) -> bytes:
    def chunk(tag: bytes, data: bytes) -> bytes:
        return struct.pack('>I', len(data)) + tag + data + struct.pack('>I', zlib.crc32(tag + data) & 0xffffffff)
    raw_row = b'\x00' + bytes([value, value // 2, 255 - value]) * 320
    pixels = raw_row * 480
    return (b'\x89PNG\r\n\x1a\n' +
            chunk(b'IHDR', struct.pack('>IIBBBBB', 320, 480, 8, 2, 0, 0, 0)) +
            chunk(b'IDAT', zlib.compress(pixels, 1)) +
            chunk(b'IEND', b''))


def good_summary() -> dict:
    return {'expected_tests': list(v.TESTS), 'started_tests': list(v.TESTS),
            'passed_tests': list(v.TESTS), 'failed_tests': [],
            'terminal_success': True, 'reported_ok': True,
            'error_categories': [], 'raw_logs_retained': False}


def fixture(path: Path) -> dict:
    app, test = v.artifact_paths(path)
    for filename, content in ((app, b'APK' * 500), (test, b'TEST' * 500)):
        filename.parent.mkdir(parents=True, exist_ok=True)
        filename.write_bytes(content)
    out = path / 'out'
    out.mkdir()
    screens = []
    for index, name in enumerate(v.SCREENS):
        image = png_pixel(40 + index * 30)
        filename = f'sfdt21-ui-{name}.png'
        (out / filename).write_bytes(image)
        w, h = v.png_dimensions(image)
        screens.append({'name': filename, 'width': w, 'height': h,
                        'size': len(image), 'sha256': v.checked_sha256(image)})
    obj = {'schema': 1, 'producer': 'studyflow-sfdt21-hosted-api36-real-mainactivity',
           'overall': v.OUTCOME, 'source_sha': SHA, 'run_id': '101', 'attempt': '1',
           'hosted_emulator_only': True, 'physical_device_qualified': False,
           'release_qualified': False, 'publication_allowed': False,
           'real_main_activity': True, 'real_ui_interactions': True,
           'fresh_no_account_fixture': True, 'network_disabled': True,
           'raw_logs_retained': False, 'api_level': 36, 'emulator_serial': v.SERIAL,
           'test_count': 4, 'screenshot_count': 4,
           'instrumentation_summary': good_summary(),
           'app_apk': v.file_sha256(app), 'test_apk': v.file_sha256(test),
           'installed_app_sha256': v.file_sha256(app)['sha256'],
           'debug_signing_certificates': {'app': 'c' * 64, 'test': 'c' * 64},
           'screenshots': screens}
    (out / 'SFDT21_EVIDENCE.json').write_text(json.dumps(obj))
    return obj


class SFDT21Contracts(unittest.TestCase):
    def test_real_instrumentation_summary_positive(self):
        lines = []
        for name in v.TESTS:
            lines.extend([f'INSTRUMENTATION_STATUS: test={name}', 'INSTRUMENTATION_STATUS_CODE: 1',
                          f'INSTRUMENTATION_STATUS: test={name}', 'INSTRUMENTATION_STATUS_CODE: 0'])
        lines += ['OK (4 tests)', 'INSTRUMENTATION_CODE: -1']
        self.assertTrue(v.valid_summary(v.summarize('\n'.join(lines))))

    def test_missing_test_or_false_success_refused(self):
        good = good_summary()
        self.assertTrue(v.valid_summary(good))
        for key, value in [('passed_tests', list(v.TESTS[:2])), ('terminal_success', False),
                           ('reported_ok', False), ('raw_logs_retained', True),
                           ('failed_tests', [v.TESTS[1]])]:
            bad = {**good, key: value}
            self.assertFalse(v.valid_summary(bad), key)

    def test_real_emulator_receipt_and_artifact_verification(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d)
            fixture(root)
            result = v.verify(root / 'out', SHA, '101', '1', root, cert_reader=lambda _: 'c' * 64)
            self.assertEqual(result['test_count'], 4)
            self.assertEqual(result['screenshots'], 4)

    def test_apk_tampering_rejected(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d)
            fixture(root)
            app, _ = v.artifact_paths(root)
            app.write_bytes(app.read_bytes() + b'changed')
            with self.assertRaises(v.Refused):
                v.verify(root / 'out', SHA, '101', '1', root, cert_reader=lambda _: 'c' * 64)

    def test_png_tampering_rejected(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d)
            fixture(root)
            image = root / 'out/sfdt21-ui-progress.png'
            image.write_bytes(image.read_bytes()[:-1])
            with self.assertRaises(v.Refused):
                v.verify(root / 'out', SHA, '101', '1', root, cert_reader=lambda _: 'c' * 64)

    def test_source_or_run_mismatch_rejected(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d)
            fixture(root)
            for sha, run, attempt in [('b' * 40, '101', '1'), (SHA, '102', '1'), (SHA, '101', '2')]:
                with self.subTest(sha=sha, run=run, attempt=attempt), self.assertRaises(v.Refused):
                    v.verify(root / 'out', sha, run, attempt, root)

    def test_screenshot_symlink_and_extra_file_refused(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d)
            fixture(root)
            output = root / 'out'
            img = output / 'sfdt21-ui-today.png'
            source = output / 'copy.png'
            source.write_bytes(img.read_bytes())
            img.unlink()
            img.symlink_to(source)
            with self.assertRaises(v.Refused):
                v.verify(output, SHA, '101', '1', root, cert_reader=lambda _: 'c' * 64)

    def test_signer_identity_mismatch_and_forged_cert_refused(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d)
            receipt = fixture(root)
            with self.assertRaises(v.Refused):
                v.verify(root / 'out', SHA, '101', '1', root, cert_reader=lambda _: 'd' * 64)
            receipt['debug_signing_certificates'] = {'app': 'not-a-sha', 'test': 'c' * 64}
            (root / 'out/SFDT21_EVIDENCE.json').write_text(json.dumps(receipt))
            with self.assertRaises(v.Refused):
                v.verify(root / 'out', SHA, '101', '1', root, cert_reader=lambda _: 'c' * 64)

    def test_failed_emulator_phase_is_allowlisted_and_no_raw_logs(self):
        from unittest.mock import patch
        with tempfile.TemporaryDirectory() as d:
            root = Path(d)
            out = root / 'out'
            # No host/ADB executed. This tests safe failure receipts only.
            with patch.object(v, 'assert_host', side_effect=v.Refused('hidden machine data')):
                self.assertFalse(v.run(out, SHA, '101', '1', root))
            receipt = json.loads((out / 'SFDT21_EVIDENCE.json').read_text())
            self.assertEqual(receipt['failure_phase'], 'host_identity')
            self.assertEqual(receipt['failure_class'], 'Refused')
            self.assertNotIn('hidden machine data', json.dumps(receipt))
            self.assertNotIn('instrumentation_summary', receipt)
            self.assertFalse(receipt['publication_allowed'])
            self.assertTrue(set(v.PHASES) >= {receipt['failure_phase']})
            # Host identity is accepted but emulator disconnected. Still no raw output.
            with patch.object(v, 'assert_host'), patch.object(v, 'adb', side_effect=v.Refused('private stdout')):
                self.assertFalse(v.run(out, SHA, '101', '1', root))
            receipt = json.loads((out / 'SFDT21_EVIDENCE.json').read_text())
            self.assertEqual(receipt['failure_phase'], 'emulator_connected')
            self.assertNotIn('private stdout', json.dumps(receipt))

    def test_preavd_apk_preflight_requires_host_and_real_signing(self):
        from unittest.mock import patch
        with tempfile.TemporaryDirectory() as d:
            root = Path(d)
            fixture(root)
            with patch.object(v, 'assert_host'), patch.object(v, 'certificate_sha256', return_value='c'*64):
                result = v.preflight_apks(SHA, '101', '1', root)
                self.assertEqual(result['status'], 'SFDT21_PRE_AVD_APK_PREFLIGHT_PASS')
                self.assertFalse(result['emulator_ui_qualified'])
            with patch.object(v, 'assert_host'), patch.object(v, 'certificate_sha256', side_effect=v.Refused('unsigned')):
                with self.assertRaises(v.Refused):
                    v.preflight_apks(SHA, '101', '1', root)

    def test_crash_and_fake_terminal_are_refused(self):
        for error in ('INSTRUMENTATION_RESULT: shortMsg=Process crashed', 'INSTRUMENTATION_FAILED'):
            result = v.summarize(error + '\nOK (4 tests)\nINSTRUMENTATION_CODE: -1')
            self.assertFalse(v.valid_summary(result))

    def test_production_source_contracts(self):
        workflow = (ROOT / '.github/workflows/studyflow-sfdt21-hosted-ui.yml').read_text()
        gradle = (ROOT / 'app/build.gradle.kts').read_text()
        kotlin = (ROOT / 'app/src/androidTest/java/com/mikeyphw/studyflow/Sfdt21HostedRealUiTest.kt').read_text()
        bash = (ROOT / 'scripts/sfdt21_emulator_ci.sh').read_text()
        # Preserve the literal release default for SFDT01/legacy source contracts.
        assert 'testBuildType = "release"' in gradle
        assert 'STUDYFLOW_SFDT21_HOSTED_EMULATOR' in gradle
        assert 'testBuildType = "debug"' in gradle
        assert '!studyFlowPersonalSigningConfigured && !releaseSigningConfigured' in gradle
        assert 'providers.environmentVariable("GITHUB_ACTIONS").orNull == "true"' in gradle
        for marker in ('MainActivity', 'performClick()', 'scenario.recreate()', 'captureToImage()',
                       'assertIsSelected()', 'createAndroidComposeRule', 'Role.Tab', 'hasText(',
                       'realActivityOpensGlobalSearchAndReturnsToToday',
                       'onNodeWithContentDescription', 'hasSetTextAction()', 'Espresso.pressBack()'):
            assert marker in kotlin, marker
        assert 'rule.setContent(' not in kotlin and 'mock' not in kotlin.lower().split('class sfdt21hostedrealuitest', 1)[1]
        # Hosted run 42411453613 exposed two invalid imports and missing Bitmap.
        assert 'import android.graphics.Bitmap\n' in kotlin
        assert 'import androidx.compose.ui.test.onAllNodes\n' not in kotlin
        assert 'import androidx.compose.ui.test.onNode\n' not in kotlin
        assert 'rule.onNode(' in kotlin and 'rule.onAllNodes(' in kotlin
        for task in (':app:assembleDebug', ':app:assembleDebugAndroidTest'):
            assert task in workflow
        for required in ('api-level: 36', 'arch: x86_64', 'profile: pixel_6',
                         'ReactiveCircus/android-emulator-runner@a421e43855164a8197daf9d8d40fe71c6996bb0d',
                         'name: SFDT21 hosted emulator UI gate',
                         'if-no-files-found: error', 'STUDYFLOW_SFDT21_HOSTED_EMULATOR:',
                         '--verify --output-dir', 'python3 -B scripts/test_sfdt21_emulator_contract.py'):
            assert required in workflow, required
        assert '--preflight-apks' in workflow and 'SFDT21_PRE_AVD_APK_PREFLIGHT_PASS' in (ROOT / 'scripts/sfdt21_emulator_verify.py').read_text()
        assert 'failure_phase' in (ROOT / 'scripts/sfdt21_emulator_verify.py').read_text() and 'phase=' in (ROOT / 'scripts/sfdt21_emulator_verify.py').read_text()
        assert 'ro.kernel.qemu' in bash and 'emulator-5554' in bash
        # The emulator source gate must also execute the legacy source policy.
        assert 'python3 -B scripts/verify_studyflow_actions.py --source-only' in workflow
        assert 'python3 -B scripts/studyflow_ci_gradle_profile.py --check-source' in workflow
        assert 'continue-on-error:' not in workflow and 'secrets.' not in workflow
        assert 'id-token: write' not in workflow and 'contents: write' not in workflow
        for path in ('scripts/sfdt21_emulator_verify.py','scripts/sfdt21_emulator_ci.sh'):
            digest = hashlib.sha256((ROOT / path).read_bytes()).hexdigest()
            assert f'{digest}  {path}' in workflow, path
        kotlin_path = 'app/src/androidTest/java/com/mikeyphw/studyflow/Sfdt21HostedRealUiTest.kt'
        digest = hashlib.sha256((ROOT / kotlin_path).read_bytes()).hexdigest()
        assert f'{digest}  {kotlin_path}' in workflow


if __name__ == '__main__':
    unittest.main(verbosity=2)
