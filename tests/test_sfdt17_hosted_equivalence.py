#!/usr/bin/env python3
"""SFDT17: adversarial offline artifact-archive and evidence admission tests."""
from __future__ import annotations
import copy
import hashlib
import io
import json
from pathlib import Path
import stat
import sys
import tempfile
import unittest
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from scripts.sfdt17_hosted_equivalence import (
    EvidenceRefused, artifact_contracts, verify_bundle_metadata,
    inspect_zip, assemble_report, safe_member, inspect_junit,
    report_refusal, _trusted_cdn, REPO, MAX_ARCHIVE, CANONICAL_WORKFLOWS,
)

SOURCE = 'a'*40
IDS = {'legacy': 100, 'source_shadow': 101, 'expanded_shadow': 102,
       'native_fast': 103, 'native_deep': 104}
ATTEMPTS = {lane: 1 for lane in IDS}
IDENTITIES = {lane: {'attempt': 1, 'workflow_path': CANONICAL_WORKFLOWS[lane][0]} for lane in IDS}
JUNIT = b'<testsuite name="valid"><testcase name="one"/><testcase name="two"><skipped/></testcase></testsuite>'


def zip_bytes(role: str) -> bytes:
    mem = io.BytesIO()
    with zipfile.ZipFile(mem, 'w', compression=zipfile.ZIP_DEFLATED) as f:
        if role.endswith('_apk'):
            f.writestr('app-debug.apk', b'PK\x03\x04' + bytes(range(32)))
        elif role.endswith('junit') or role in ('android_deep', 'tv_deep', 'desktop_deep'):
            f.writestr('reports/TEST-class.xml', JUNIT)
        else:
            f.writestr('reports/receipt.json', json.dumps({'schema':'test', 'status':'PASS'}))
    return mem.getvalue()


def example(tmp: Path):
    names = artifact_contracts(IDS, ATTEMPTS)
    inventory = {}
    i = 50
    for lane, roles in names.items():
        artifacts = []
        for name, role in roles.items():
            contents = zip_bytes(role)
            aid = i
            i += 1
            (tmp / f'{aid}.zip').write_bytes(contents)
            artifacts.append({'name':name, 'id':aid, 'size_in_bytes':len(contents),
                              'digest':'sha256:'+hashlib.sha256(contents).hexdigest(),
                              'expired':False,
                              'workflow_run':{'id':IDS[lane], 'head_sha':SOURCE}})
        inventory[lane] = {'artifacts':artifacts}
    return inventory


class SFDT17Tests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.inventories = example(self.root)

    def test_good_evidence_not_cutover(self):
        r = assemble_report(SOURCE, IDS, IDENTITIES, self.inventories, self.root)
        self.assertEqual(r['artifact_archive_integrity'], 'PASS')
        self.assertFalse(r['cutover_authorized'])
        self.assertEqual(r['exact_artifact_attempt_provenance'],'NOT_VERIFIED')
        self.assertEqual(len(r['lanes']['native_deep']),5)
        self.assertGreater(r['aggregate_junit_executions_not_unique_tests']['executed'],0)

    def test_hardcoded_workflow_policy_rejects_user_supplied_other_path(self):
        contracts=copy.deepcopy(IDENTITIES)
        contracts['native_fast']['workflow_path']='.github/workflows/unrelated.yml'
        with self.assertRaisesRegex(EvidenceRefused,'source-controlled policy'):
            assemble_report(SOURCE,IDS,contracts,self.inventories,self.root)

    def test_hardcoded_workflow_names_reject_impersonation(self):
        self.inventories['legacy']['run']={'name':'Unrelated workflow'}
        with self.assertRaisesRegex(EvidenceRefused,'workflow name differs'):
            assemble_report(SOURCE,IDS,IDENTITIES,self.inventories,self.root)

    def test_expected_names_are_frozen(self):
        specs=artifact_contracts(IDS, ATTEMPTS)
        self.assertEqual(len(specs['legacy']),4)
        self.assertEqual(len(specs['expanded_shadow']),4)
        self.assertEqual(len(specs['native_fast']),3)
        self.assertEqual(len(specs['native_deep']),5)
        self.assertEqual(len(specs['source_shadow']),1)

    def test_missing_artifact(self):
        self.inventories['native_fast']['artifacts'].pop()
        with self.assertRaisesRegex(EvidenceRefused,'required artifacts missing'):
            assemble_report(SOURCE,IDS,IDENTITIES,self.inventories,self.root)

    def test_extra_artifact(self):
        self.inventories['legacy']['artifacts'][0]['name']='unrelated-artifact'
        with self.assertRaisesRegex(EvidenceRefused,'unexpected artifact'):
            assemble_report(SOURCE,IDS,IDENTITIES,self.inventories,self.root)

    def test_duplicate_artifact_id(self):
        self.inventories['native_fast']['artifacts'][0]['id']=self.inventories['legacy']['artifacts'][0]['id']
        with self.assertRaisesRegex(EvidenceRefused,'duplicate/invalid artifact ID'):
            assemble_report(SOURCE,IDS,IDENTITIES,self.inventories,self.root)

    def test_expired_artifact(self):
        self.inventories['legacy']['artifacts'][0]['expired']=True
        with self.assertRaisesRegex(EvidenceRefused,'expired artifact'):
            assemble_report(SOURCE,IDS,IDENTITIES,self.inventories,self.root)

    def test_bad_metadata_digest(self):
        self.inventories['legacy']['artifacts'][0]['digest']='sha256:BAD'
        with self.assertRaisesRegex(EvidenceRefused,'SHA-256'):
            assemble_report(SOURCE,IDS,IDENTITIES,self.inventories,self.root)

    def test_changed_download_bytes(self):
        aid=self.inventories['legacy']['artifacts'][0]['id']
        f=self.root/f'{aid}.zip'; f.write_bytes(f.read_bytes()+b'garbage')
        with self.assertRaisesRegex(EvidenceRefused,'bytes mismatch'):
            assemble_report(SOURCE,IDS,IDENTITIES,self.inventories,self.root)

    def test_wrong_producer_sha(self):
        self.inventories['native_deep']['artifacts'][0]['workflow_run']['head_sha']='b'*40
        with self.assertRaisesRegex(EvidenceRefused,'producer run/SHA'):
            assemble_report(SOURCE,IDS,IDENTITIES,self.inventories,self.root)

    def test_missing_producer(self):
        self.inventories['legacy']['artifacts'][0].pop('workflow_run')
        with self.assertRaisesRegex(EvidenceRefused,'producer run/SHA'):
            assemble_report(SOURCE,IDS,IDENTITIES,self.inventories,self.root)

    def test_reused_archive_name(self):
        name=self.inventories['native_fast']['artifacts'][0]['name']
        self.inventories['native_fast']['artifacts'].append(copy.deepcopy(self.inventories['native_fast']['artifacts'][0]))
        with self.assertRaisesRegex(EvidenceRefused,'duplicate artifact name'):
            assemble_report(SOURCE,IDS,IDENTITIES,self.inventories,self.root)

    def test_unsafe_paths(self):
        for name in ('../x','/absolute','C:/evil','folder/../file','folder\\evil','./file','folder//file'):
            with self.subTest(name=name),self.assertRaises(EvidenceRefused):
                safe_member(name,False)

    def test_case_collision(self):
        archive=self.root/'collision.zip'
        with zipfile.ZipFile(archive,'w') as f:
            f.writestr('A/file.json','{}'); f.writestr('a/FILE.json','{}')
        with self.assertRaisesRegex(EvidenceRefused,'case-colliding'):
            inspect_zip(archive,role='source_receipt')

    def test_symlink_rejected(self):
        archive=self.root/'symlink.zip'
        with zipfile.ZipFile(archive,'w') as f:
            info=zipfile.ZipInfo('bad')
            info.create_system=3
            info.external_attr=(stat.S_IFLNK|0o777)<<16
            f.writestr(info,'target')
        with self.assertRaisesRegex(EvidenceRefused,'symlink'):
            inspect_zip(archive,role='source_receipt')

    def test_missing_apk(self):
        archive=self.root/'not-apk.zip'
        with zipfile.ZipFile(archive,'w') as f: f.writestr('report.json','{}')
        with self.assertRaisesRegex(EvidenceRefused,'expected one'):
            inspect_zip(archive,role='phone_debug_apk')

    def test_multiple_apks(self):
        archive=self.root/'two-apks.zip'
        with zipfile.ZipFile(archive,'w') as f:
            f.writestr('a.apk','aa'); f.writestr('b.apk','bb')
        with self.assertRaisesRegex(EvidenceRefused,'expected one'):
            inspect_zip(archive,role='phone_debug_apk')

    def test_no_junit_refused(self):
        archive=self.root/'no-junit.zip'
        with zipfile.ZipFile(archive,'w') as f:f.writestr('other.json','{}')
        with self.assertRaisesRegex(EvidenceRefused,'missing parseable JUnit'):
            inspect_zip(archive,role='web_junit')

    def test_deep_android_cannot_omit_junit(self):
        archive = self.root/'no-deep-junit.zip'
        with zipfile.ZipFile(archive,'w') as f:f.writestr('receipt.json','{}')
        with self.assertRaisesRegex(EvidenceRefused,'missing parseable JUnit'):
            inspect_zip(archive,role='android_deep')

    def test_junit_counts_are_real_cases(self):
        result=inspect_junit(JUNIT)
        self.assertEqual((result['executed'],result['passed'],result['skipped']), (2,1,1))

    def test_invalid_xml_not_junit(self):
        self.assertIsNone(inspect_junit(b'<testsuite><testcase>'))

    def test_untrusted_redirect_host(self):
        for url in ('http://objects.githubusercontent.com/a','https://evil.example.com/x',
                    'https://githubusercontent.com.evil.example/zip',
                    'https://attacker@pipelines.actions.githubusercontent.com/foo'):
            self.assertFalse(_trusted_cdn(url),url)

    def test_allowed_cdn(self):
        self.assertTrue(_trusted_cdn('https://pipelines.actions.githubusercontent.com/a'))
        self.assertTrue(_trusted_cdn('https://productionresultssa0.blob.core.windows.net/a'))

    def test_source_sha_required(self):
        with self.assertRaises(EvidenceRefused):
            assemble_report('bads',IDS,IDENTITIES,self.inventories,self.root)

    def test_zip_file_size_cap(self):
        artifact=self.inventories['legacy']['artifacts'][0]
        artifact['size_in_bytes']=MAX_ARCHIVE+1
        with self.assertRaisesRegex(EvidenceRefused,'unbounded'):
            assemble_report(SOURCE,IDS,IDENTITIES,self.inventories,self.root)

    def test_source_archive_receipt_not_release(self):
        r=assemble_report(SOURCE,IDS,IDENTITIES,self.inventories,self.root)
        self.assertFalse(r['release_qualified'])
        self.assertFalse(r['signing_qualified'])
        self.assertFalse(r['device_qualified'])

    def test_refusal_never_grants_authority(self):
        r=report_refusal(SOURCE,'bad')
        self.assertEqual(r['status'],'REFUSED')
        self.assertFalse(r['cutover_authorized'])


if __name__ == '__main__':
    unittest.main()
