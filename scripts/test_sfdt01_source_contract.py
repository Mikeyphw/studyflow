#!/usr/bin/env python3
"""Source-only negative regressions for SFDT-01; no Gradle, network or writes to repository."""
from __future__ import annotations

import importlib.util
from pathlib import Path
import shutil
import tempfile
import unittest

SOURCE = Path(__file__).resolve().with_name("sfdt01_source_contract.py")
spec = importlib.util.spec_from_file_location("sfdt_source_contract", SOURCE)
assert spec and spec.loader
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class Contracts(unittest.TestCase):
    def sandbox(self, tmp: str) -> Path:
        repo = Path(tmp)
        files = [".devtool.toml", *module.WORKFLOWS]
        for name in files:
            (repo / name).parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(module.ROOT / name, repo / name)
        for name in (
            "devtool_actions_contract.py", "verify_studyflow_actions.py",
            "sfdt01_source_contract.py", "studyflow_ci_gradle_profile.py",
        ):
            (repo / "scripts").mkdir(exist_ok=True)
            shutil.copyfile(module.ROOT / "scripts" / name, repo / "scripts" / name)
        return repo

    def test_clean_source_inventory_passes(self):
        with tempfile.TemporaryDirectory() as tmp:
            report = module.audit(self.sandbox(tmp))
            self.assertEqual(report["status"], "SOURCE_ONLY_PASS")
            self.assertFalse(report["qualified_release"])
            self.assertFalse(report["gradle_executed"])

    def test_missing_source_verifier_refused(self):
        with tempfile.TemporaryDirectory() as tmp:
            repo = self.sandbox(tmp)
            (repo / "scripts/sfdt01_source_contract.py").unlink()
            with self.assertRaises(module.SourceContractError):
                module.audit(repo)

    def test_unsafe_wrapper_default_refused(self):
        with tempfile.TemporaryDirectory() as tmp:
            repo = self.sandbox(tmp)
            file = repo / ".devtool.toml"
            content = file.read_text()
            self.assertIn('default_command = "ci-source"', content)
            file.write_text(content.replace('default_command = "ci-source"', 'default_command = "build"', 1))
            with self.assertRaises(module.SourceContractError):
                module.audit(repo)

    def test_exo_gradle_execution_is_refused(self):
        with tempfile.TemporaryDirectory() as tmp:
            repo = self.sandbox(tmp)
            file = repo / ".devtool.toml"
            content = file.read_text()
            original = 'command = ["python3", "scripts/sfdt01_source_contract.py", "--check"]'
            self.assertIn(original, content)
            file.write_text(content.replace(original, 'command = ["./gradlew", "test"]', 1))
            with self.assertRaises(module.SourceContractError):
                module.audit(repo)

    def test_missing_signing_environment_guard_refused(self):
        with tempfile.TemporaryDirectory() as tmp:
            repo = self.sandbox(tmp)
            file = repo / module.WORKFLOWS[3]
            content = file.read_text()
            self.assertIn("environment: studyflow-signing", content)
            file.write_text(content.replace("environment: studyflow-signing", "environment: skip", 1))
            with self.assertRaises(module.SourceContractError):
                module.audit(repo)

    def test_appended_job_argument_refused(self):
        with tempfile.TemporaryDirectory() as tmp:
            repo = self.sandbox(tmp)
            file = repo / ".devtool.toml"
            content = file.read_text()
            original = 'command = ["python3", "scripts/sfdt01_source_contract.py", "--check"]'
            self.assertIn(original, content)
            file.write_text(content.replace(original, original[:-1] + ', "--unsafe"]', 1))
            with self.assertRaises(module.SourceContractError):
                module.audit(repo)

    def test_unreviewed_python_script_refused(self):
        with tempfile.TemporaryDirectory() as tmp:
            repo = self.sandbox(tmp)
            malicious = repo / "scripts/not-approved.py"
            malicious.write_text('print("unexpected")\n')
            file = repo / ".devtool.toml"
            content = file.read_text()
            original = 'command = ["python3", "scripts/sfdt01_source_contract.py", "--check"]'
            self.assertIn(original, content)
            file.write_text(content.replace(original, 'command = ["python3", "scripts/not-approved.py"]', 1))
            with self.assertRaises(module.SourceContractError):
                module.audit(repo)

    def test_source_job_missing_own_input_refused(self):
        with tempfile.TemporaryDirectory() as tmp:
            repo = self.sandbox(tmp)
            file = repo / ".devtool.toml"
            content = file.read_text()
            # Every source-only job must own its own source file, so a change
            # cannot be silently missed once affected-target selection works.
            marker = '"scripts/sfdt01_source_contract.py"]'
            self.assertIn(marker, content)
            file.write_text(content.replace(marker, '"scripts/some-other-file.py"]', 1))
            with self.assertRaises(module.SourceContractError):
                module.audit(repo)

    def test_missing_hosted_self_check_refused(self):
        with tempfile.TemporaryDirectory() as tmp:
            repo = self.sandbox(tmp)
            file = repo / module.WORKFLOWS[0]
            content = file.read_text()
            needle = "python3 scripts/sfdt01_source_contract.py --check"
            self.assertIn(needle, content)
            file.write_text(content.replace(needle, "true", 1))
            with self.assertRaises(module.SourceContractError):
                module.audit(repo)

    def test_missing_hosted_sha_gate_refused(self):
        with tempfile.TemporaryDirectory() as tmp:
            repo = self.sandbox(tmp)
            file = repo / module.WORKFLOWS[0]
            content = file.read_text()
            self.assertIn('test "$EXPECTED_SHA" = "$GITHUB_SHA"', content)
            file.write_text(content.replace('test "$EXPECTED_SHA" = "$GITHUB_SHA"', 'true', 1))
            with self.assertRaises(module.SourceContractError):
                module.audit(repo)


if __name__ == "__main__":
    unittest.main()
