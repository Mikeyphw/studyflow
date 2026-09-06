from __future__ import annotations

import importlib.util
import unittest
from html.parser import HTMLParser
from pathlib import Path

WEB = Path(__file__).resolve().parents[1]
ROOT = WEB.parent


class Capture(HTMLParser):
    def __init__(self) -> None:
        super().__init__()
        self.tags: list[tuple[str, dict[str, str | None]]] = []
        self.ids: set[str] = set()
        self.hrefs: list[str] = []
        self.decls: list[str] = []

    def handle_decl(self, decl: str) -> None:
        self.decls.append(decl.lower().strip())

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        values = dict(attrs)
        self.tags.append((tag, values))
        if values.get("id"):
            self.ids.add(str(values["id"]))
        if tag == "a" and values.get("href"):
            self.hrefs.append(str(values["href"]))


class W01SemanticMobileShellTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.text = (WEB / "index.html").read_text(encoding="utf-8")
        cls.parser = Capture()
        cls.parser.feed(cls.text)
        cls.parser.close()

    def test_modern_mobile_document_baseline(self) -> None:
        self.assertIn("doctype html", self.parser.decls)
        html_attrs = next(attrs for tag, attrs in self.parser.tags if tag == "html")
        self.assertTrue(html_attrs.get("lang"))
        metas = [attrs for tag, attrs in self.parser.tags if tag == "meta"]
        self.assertTrue(any(m.get("name") == "viewport" and "width=device-width" in str(m.get("content") or "") for m in metas))

    def test_four_primary_destinations_and_main_navigation_are_real_anchors(self) -> None:
        for name in ("today", "plan", "practice", "progress"):
            self.assertIn(name, self.parser.ids)
            self.assertIn(f"#{name}", self.parser.hrefs)

    def test_plan_information_architecture_is_frozen(self) -> None:
        for name in ("plan-overview", "plan-schedule", "plan-content", "plan-library"):
            self.assertIn(name, self.parser.ids)
            self.assertIn(f"#{name}", self.parser.hrefs)

    def test_every_local_anchor_resolves(self) -> None:
        for href in self.parser.hrefs:
            if href.startswith("#"):
                self.assertIn(href[1:], self.parser.ids, href)

    def test_w01_semantic_shell_remains_framework_independent(self) -> None:
        self.assertNotIn("style=", self.text.lower())
        self.assertNotIn("<style", self.text.lower())
        scripts = [attrs for tag, attrs in self.parser.tags if tag == "script"]
        self.assertTrue(all(not str(attrs.get("src") or "").startswith(("http://", "https://")) for attrs in scripts))

    def test_integrated_validator_passes_full_w01_contract(self) -> None:
        path = WEB / "scripts/validate_web.py"
        spec = importlib.util.spec_from_file_location("studyflow_web_validator", path)
        assert spec and spec.loader
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        self.assertEqual(module.validate_shell(), [])


if __name__ == "__main__":
    unittest.main()
