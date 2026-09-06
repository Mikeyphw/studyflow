from __future__ import annotations

import importlib.util
import json
import tomllib
import unittest
from pathlib import Path

WEB = Path(__file__).resolve().parents[1]
ROOT = WEB.parent


class W00WebContractTest(unittest.TestCase):
    def test_preconfigured_devtool_web_target_is_node_and_preserved(self) -> None:
        data = tomllib.loads((ROOT / ".devtool.toml").read_text(encoding="utf-8"))
        targets = data["targets"]
        web = targets["studyflow_web"]
        self.assertEqual(web["type"], "node")
        self.assertEqual(web["root"], "web")
        self.assertEqual(web["execution_environment"], "native-termux")
        self.assertNotIn("tasks", web)
        self.assertEqual(web["validation"]["max_workers"], 1)
        self.assertEqual(web["validation"]["cpu_limit"], 1)
        self.assertFalse(web["validation"]["parallel"])
        self.assertNotIn("phases", web["validation"])
        self.assertIn("studyflow_android", targets)
        self.assertIn("studyflow_desktop", targets)
        self.assertEqual(data["project"]["default_target"], "studyflow_desktop")

    def test_bootstrap_is_dependency_free_and_has_devtool_scripts(self) -> None:
        package = json.loads((WEB / "package.json").read_text(encoding="utf-8"))
        self.assertTrue(package["private"])
        self.assertFalse(package.get("dependencies"))
        self.assertFalse(package.get("devDependencies"))
        for name in ("build", "test", "lint", "dev"):
            self.assertIn(name, package["scripts"])
        lock = json.loads((WEB / "package-lock.json").read_text(encoding="utf-8"))
        self.assertEqual(lock["lockfileVersion"], 3)

    def test_contract_validator_passes(self) -> None:
        path = WEB / "scripts/validate_web.py"
        spec = importlib.util.spec_from_file_location("studyflow_web_validator", path)
        assert spec and spec.loader
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        self.assertEqual(module.validate_contract(), [])


if __name__ == "__main__":
    unittest.main()
