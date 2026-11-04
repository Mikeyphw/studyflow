#!/usr/bin/env python3
"""SFDT07-R3: exact GitHub setup-node input regression (no Gradle or network)."""
from __future__ import annotations
from pathlib import Path
import sys
import unittest
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'scripts'))
from sfdt07_consolidated_contract import check_workflow, ContractError
WF=ROOT/'.github/workflows/studyflow-devtool-native-expanded.yml'
MARK="      - name: Reviewed Node runtime for Web pytest subprocesses\n"

class HostedNodeReviewTests(unittest.TestCase):
    def setUp(self):
        self.source=WF.read_text(encoding='utf-8')
    def test_reviewed_pin_scope_and_no_cache(self):
        check_workflow(self.source)
        self.assertEqual(self.source.count(MARK),1)
        self.assertNotIn('          cache: false\n',self.source)
        self.assertNotIn('          cache: npm\n',self.source)
    def reject(self,source):
        with self.assertRaises(ContractError):
            check_workflow(source)
    def test_boolean_cache_refused(self):
        self.reject(self.source.replace("          node-version: '22'\n", "          node-version: '22'\n          cache: false\n",1))
    def test_string_cache_false_refused(self):
        self.reject(self.source.replace("          node-version: '22'\n", "          node-version: '22'\n          cache: 'false'\n",1))
    def test_any_cache_manager_refused_without_review(self):
        self.reject(self.source.replace("          node-version: '22'\n", "          node-version: '22'\n          cache: npm\n",1))
    def test_automatic_package_manager_cache_refused(self):
        self.reject(self.source.replace("          node-version: '22'\n", "          node-version: '22'\n          package-manager-cache: true\n",1))
    def test_moving_node_runtime_to_android_refused(self):
        self.reject(self.source.replace("if: ${{ matrix.lane == 'web-pytest' }}\n        uses: actions/setup-node@", "if: ${{ matrix.lane == 'phone-debug' }}\n        uses: actions/setup-node@",1))
    def test_unpinned_version_refused(self):
        self.reject(self.source.replace('actions/setup-node@249970729cb0ef3589644e2896645e5dc5ba9c38', 'actions/setup-node@v6',1))
    def test_wrong_node_version_refused(self):
        self.reject(self.source.replace("          node-version: '22'\n", "          node-version: '24'\n",1))
    def test_removed_node_step_refused(self):
        head,tail=self.source.split(MARK,1)
        rest=tail.split('      - name: ',1)[1]
        self.reject(head+'      - name: '+rest)
    def test_duplicated_node_step_refused(self):
        frag=MARK+self.source.split(MARK,1)[1].split('      - name: ',1)[0]
        self.reject(self.source.replace(frag,frag+frag,1))
    def test_legacy_authority_still_blocked(self):
        self.assertNotIn('environment: studyflow-signing',self.source)
        self.assertNotIn('id-token: write',self.source)
        self.assertNotIn('pull_request:',self.source)
        self.assertNotIn('  push:',self.source)
if __name__=='__main__': unittest.main(verbosity=2)
