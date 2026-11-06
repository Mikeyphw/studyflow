#!/usr/bin/env python3
"""SFDT11 source and trust boundary negative/positive regression tests."""
from __future__ import annotations

from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0,str(Path(__file__).resolve().parent))
import sfdt11_contract as c


def config():
    return {'targets': {
        'studyflow_android': {'jobs': {
            'sfdt08-web-full': {'command': ['python3','scripts/sfdt08_hosted_bridge.py','web-full'],
                                'metadata': {'ci_toolchain': 'python-shell'}},
            'sfdt08-phone-release-jvm': {'gradle_tasks':[':app:testReleaseUnitTest'],
                                         'metadata': {'ci_toolchain':'android-gradle'}}}},
        'studyflow_desktop': {'jobs': {
            'sfdt08-desktop-fast': {'command':['python3','scripts/sfdt08_hosted_bridge.py','desktop-fast'],
                                    'metadata': {'ci_toolchain': 'python-shell'}}}}
    }}

class SourceTests(unittest.TestCase):
    def test_git_blob_immutable_inventory_exact(self):
        c.verify_frozen(dict(c.FROZEN_BLOBS))
        one=dict(c.FROZEN_BLOBS)
        one['.devtool.toml']='a'*40
        with self.assertRaises(c.PolicyError): c.verify_frozen(one)
        one=dict(c.FROZEN_BLOBS);one.pop('.devtool.toml')
        with self.assertRaises(c.PolicyError): c.verify_frozen(one)

    def test_provider_truth_does_not_promote_bridges(self):
        a=c.provider_truth(config())
        self.assertFalse(a['provider_cutover'])
        self.assertIn('not-first-class',a['web'])
        self.assertIn('not-first-class',a['desktop'])

    def test_provider_wrong_toolchain_refused(self):
        a=config();a['targets']['studyflow_android']['jobs']['sfdt08-web-full']['metadata']['ci_toolchain']='node'
        with self.assertRaises(c.PolicyError): c.provider_truth(a)
        a=config();a['targets']['studyflow_desktop']['jobs']['sfdt08-desktop-fast']['metadata']['ci_toolchain']='jvm'
        with self.assertRaises(c.PolicyError): c.provider_truth(a)

    def test_source_gradle_task_protected(self):
        a=config();a['targets']['studyflow_android']['jobs']['sfdt08-phone-release-jvm']['gradle_tasks']=[':app:assembleRelease']
        with self.assertRaises(c.PolicyError): c.provider_truth(a)

    def test_unknown_bridge_argument_refused(self):
        a=config();a['targets']['studyflow_desktop']['jobs']['sfdt08-desktop-fast']['command'].append('--force')
        with self.assertRaises(c.PolicyError): c.provider_truth(a)

    def test_real_authored_workflow_policies(self):
        for index,p in enumerate(c.NEW_WORKFLOWS):
            c.policy_workflow((c.ROOT/p).read_text(),rollup=index==1)

    def test_privilege_or_new_trigger_refused(self):
        text=(c.ROOT/c.NEW_WORKFLOWS[1]).read_text()
        for evil in ('\n  actions: write\n', '\n  push:\n', '\nsecrets.STUDYFLOW\n',
                     '\n  schedule:\n','\ncontinue-on-error:\n','\ngit push\n'):
            with self.subTest(evil=evil),self.assertRaises(c.PolicyError):
                c.policy_workflow(text+evil,rollup=True)

    def test_automatic_source_check_cannot_acquire_actions_read(self):
        text=(c.ROOT/c.NEW_WORKFLOWS[0]).read_text()
        with self.assertRaises(c.PolicyError):
            c.policy_workflow(text+'\n  actions: read\n',rollup=False)
        with self.assertRaises(c.PolicyError):
            c.policy_workflow(text+'\n  workflow_dispatch:\n',rollup=False)

    def test_source_only_checkout_is_pinned(self):
        text=(c.ROOT/c.NEW_WORKFLOWS[0]).read_text()
        with self.assertRaises(c.PolicyError):
            c.policy_workflow(text.replace('actions/checkout@d23441a48e516b6c34aea4fa41551a30e30af803',
                                           'actions/checkout@v4'),rollup=False)

    def test_source_check_fake_repo_inventory(self):
        with tempfile.TemporaryDirectory(prefix='sfdt11-policy-') as d:
            root=Path(d)
            for p in c.FROZEN_BLOBS:
                f=root/p;f.parent.mkdir(parents=True,exist_ok=True)
                if p=='web/package.json':
                    f.write_bytes((c.ROOT/p).read_bytes())
                elif p=='.devtool.toml':
                    f.write_text('''[targets.studyflow_android.jobs.sfdt08-web-full]\ncommand=["python3","scripts/sfdt08_hosted_bridge.py","web-full"]\n[targets.studyflow_android.jobs.sfdt08-web-full.metadata]\nci_toolchain="python-shell"\n[targets.studyflow_android.jobs.sfdt08-phone-release-jvm]\ngradle_tasks=[":app:testReleaseUnitTest"]\n[targets.studyflow_android.jobs.sfdt08-phone-release-jvm.metadata]\nci_toolchain="android-gradle"\n[targets.studyflow_desktop.jobs.sfdt08-desktop-fast]\ncommand=["python3","scripts/sfdt08_hosted_bridge.py","desktop-fast"]\n[targets.studyflow_desktop.jobs.sfdt08-desktop-fast.metadata]\nci_toolchain="python-shell"\n''')
                else: f.write_text(c.DEVTOOL if p=='.github/workflows/studyflow-devtool-sfdt10-debug-shadow.yml' else 'tracked fixture\n')
            for p in (*c.NEW_WORKFLOWS,*c.NEW_SCRIPTS):
                f=root/p;f.parent.mkdir(parents=True,exist_ok=True)
                f.write_text((c.ROOT/p).read_text())
            with patch.object(c,'git_blob',side_effect=lambda path,repo=None:
                              c.FROZEN_BLOBS[path.relative_to(root).as_posix()]):
                self.assertEqual(c.source_check(root)['status'],'SOURCE_POLICY_PASS')
                (root/c.NEW_WORKFLOWS[1]).write_text('on: push\n')
                with self.assertRaises(c.PolicyError):c.source_check(root)

if __name__=='__main__':unittest.main(verbosity=2)
