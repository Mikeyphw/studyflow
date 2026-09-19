#!/usr/bin/env python3
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PHONE = (ROOT / 'app/build.gradle.kts').read_text()
TV = (ROOT / 'app-tv/build.gradle.kts').read_text()
SETUP = (ROOT / 'tools/setup-personal-signing.sh').read_text()
FINGERPRINT = (ROOT / 'tools/print-personal-signing-fingerprint.sh').read_text()
DOC = (ROOT / 'STUDYFLOW_PERSONAL_SIGNING.md').read_text()

class PersonalSigningContractTest(unittest.TestCase):
    def test_tv_kotlin_interpolation_syntax_regression(self):
        self.assertIn('studyFlowTvSigningValue("keyAlias")', TV)
        self.assertNotIn('studyFlowTvSigningValue(\\"keyAlias\\")', TV)

    def test_phone_debug_and_release_share_personal_signing(self):
        self.assertIn('create("studyFlowPersonal")', PHONE)
        self.assertRegex(PHONE, r'getByName\("debug"\)[\s\S]*?signingConfig = signingConfigs\.getByName\("studyFlowPersonal"\)')
        self.assertRegex(PHONE, r'release \{[\s\S]*?signingConfig = signingConfigs\.getByName\("studyFlowPersonal"\)')

    def test_tv_debug_and_release_share_personal_signing(self):
        self.assertIn('create("studyFlowTvPersonal")', TV)
        self.assertRegex(TV, r'getByName\("debug"\)[\s\S]*?signingConfig = signingConfigs\.getByName\("studyFlowTvPersonal"\)')
        self.assertRegex(TV, r'release \{[\s\S]*?signingConfig = signingConfigs\.getByName\("studyFlowTvPersonal"\)')

    def test_phone_and_tv_use_same_external_properties_contract(self):
        marker = '.config/studyflow/android-signing.properties'
        self.assertIn(marker, PHONE)
        self.assertIn(marker, TV)
        self.assertIn('STUDYFLOW_ANDROID_SIGNING_PROPERTIES', PHONE)
        self.assertIn('STUDYFLOW_ANDROID_SIGNING_PROPERTIES', TV)

    def test_tv_release_truth_includes_personal_signing(self):
        self.assertIn('(studyFlowTvPersonalSigningConfigured || tvReleaseSigningConfigured).toString()', TV)

    def test_newer_release_packaging_gates_are_preserved(self):
        self.assertIn('name.startsWith("packageRelease")', PHONE)
        self.assertIn('name.startsWith("signRelease")', PHONE)
        self.assertIn('name.startsWith("packageRelease")', TV)
        self.assertIn('require(studyFlowTvPersonalSigningConfigured || tvReleaseSigningConfigured)', TV)
        self.assertNotIn('verifyStudyFlowTvReleaseSigningConfiguration', TV)

    def test_legacy_release_fallback_is_preserved(self):
        for text in (PHONE, TV):
            for name in ('STUDYFLOW_RELEASE_STORE_FILE', 'STUDYFLOW_RELEASE_STORE_PASSWORD',
                         'STUDYFLOW_RELEASE_KEY_ALIAS', 'STUDYFLOW_RELEASE_KEY_PASSWORD'):
                self.assertIn(name, text)

    def test_personal_entry_points_exist_on_both_modules(self):
        for text in (PHONE, TV):
            for task in ('studyFlowSigningStatus', 'assemblePersonalDebug', 'assemblePersonalRelease'):
                self.assertIn(task, text)
        self.assertIn('verifyStudyFlowPersonalSigningConfigured', PHONE)
        self.assertIn('verifyStudyFlowTvPersonalSigningConfigured', TV)

    def test_setup_is_permanent_external_pkcs12_4096(self):
        self.assertIn('studyflow-personal.p12', SETUP)
        self.assertIn('-storetype PKCS12', SETUP)
        self.assertIn('-keyalg RSA', SETUP)
        self.assertIn('-keysize 4096', SETUP)
        self.assertIn('chmod 600', SETUP)
        self.assertIn('def esc(value: str)', SETUP)
        self.assertIn('python3', SETUP)
        self.assertIn('ord(ch) > 0x7f', SETUP)
        self.assertNotIn('getProperty(name)?.trim()', PHONE)
        self.assertNotIn('getProperty(name)?.trim()', TV)
        self.assertIn('umask 077', SETUP)

    def test_fingerprint_helper_reports_sha1_and_sha256(self):
        self.assertIn('keytool -list -v', FINGERPRINT)
        self.assertIn('SHA1:', FINGERPRINT)
        self.assertIn('SHA256:', FINGERPRINT)
        self.assertIn('decode("iso-8859-1")', FINGERPRINT)
        self.assertIn('unescape(line[split+1:])', FINGERPRINT)
        self.assertNotIn('sed -n', FINGERPRINT)

    def test_documentation_covers_one_time_migration(self):
        self.assertIn('uninstall that older build once', DOC)
        self.assertIn('phone and TV', DOC)

if __name__ == '__main__':
    unittest.main(verbosity=2)
