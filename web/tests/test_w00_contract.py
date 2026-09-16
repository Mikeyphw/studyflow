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
        self.assertEqual(web["runner"], "node")
        self.assertEqual(web["platform"], "web")
        self.assertEqual(web["root"], "web")
        self.assertEqual(web["execution_environment"], "native-termux")
        self.assertNotIn("tasks", web)
        self.assertEqual(web["validation"]["max_workers"], 2)
        self.assertEqual(web["validation"]["cpu_limit"], 2)
        self.assertTrue(web["validation"]["parallel"])
        self.assertIn("phases", web["validation"])
        self.assertIn("studyflow_android", targets)
        self.assertIn("studyflow_desktop", targets)
        self.assertEqual(data["workspace"]["default_target"], "studyflow_android")

    def test_bootstrap_has_local_pinned_react_typescript_and_devtool_scripts(self) -> None:
        package = json.loads((WEB / "package.json").read_text(encoding="utf-8"))
        self.assertTrue(package["private"])
        self.assertEqual(package["dependencies"], {"react": "19.2.8", "react-dom": "19.2.8"})
        self.assertEqual(package["devDependencies"], {"typescript": "5.8.3"})
        for name in ("build", "test", "lint", "dev"):
            self.assertIn(name, package["scripts"])
        lock = json.loads((WEB / "package-lock.json").read_text(encoding="utf-8"))
        self.assertEqual(lock["lockfileVersion"], 3)
        self.assertIn("node_modules/react", lock["packages"])
        self.assertIn("node_modules/react-dom", lock["packages"])

    def test_contract_validator_passes(self) -> None:
        path = WEB / "scripts/validate_web.py"
        spec = importlib.util.spec_from_file_location("studyflow_web_validator", path)
        assert spec and spec.loader
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        self.assertEqual(module.validate_contract(), [])


if __name__ == "__main__":
    unittest.main()
