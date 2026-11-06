"""SFDT13 fail-closed source and dependency metadata parser unit tests.

No Gradle, Android SDK, npm, network, or signing invocation.
"""
from __future__ import annotations
from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'scripts'))
import sfdt13_jvm_shadow as s

class SourceContract(unittest.TestCase):
    def test_real_reviewed_source(self):
        report=s.source_check()
        self.assertEqual(report['selected_module'],':core:model')
        self.assertFalse(report['rm21_qualified'])
        self.assertFalse(report['release_qualified'])

    def test_metadata_needs_real_sha256_members(self):
        with tempfile.TemporaryDirectory() as d:
            p=Path(d)/'verification-metadata.xml'
            p.write_text('<verification-metadata><components><component group="g" name="n" version="1"><artifact name="n.jar"><sha256 value="'+('a'*64)+'"/></artifact></component></components></verification-metadata>')
            # The test intentionally uses a synthetically expanded document for
            # parser grammar; it is NOT submitted as dependency verification.
            p.write_text(p.read_text()+' '*260)
            self.assertEqual(s.verification_metadata(p)['sha256_entries'],1)
            p.write_text(p.read_text().replace('a'*64,'invalid'))
            with self.assertRaises(s.GateError):s.verification_metadata(p)

    def test_metadata_missing_fails_closed(self):
        with tempfile.TemporaryDirectory() as d:
            with self.assertRaises(s.GateError):s.verification_metadata(Path(d)/'missing.xml')

    def test_metadata_rejects_doctype(self):
        with tempfile.TemporaryDirectory() as d:
            p=Path(d)/'verification-metadata.xml'
            p.write_text('<!DOCTYPE x [<!ENTITY ex SYSTEM "file:///etc/passwd">]><verification-metadata/>'+' '*400)
            with self.assertRaises(s.GateError):s.verification_metadata(p)

if __name__=='__main__':unittest.main(verbosity=2)
