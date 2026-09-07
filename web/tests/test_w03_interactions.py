from __future__ import annotations

import re
import subprocess
import unittest
from html.parser import HTMLParser
from pathlib import Path

WEB = Path(__file__).resolve().parents[1]


class Capture(HTMLParser):
    def __init__(self) -> None:
        super().__init__()
        self.tags: list[tuple[str, dict[str, str | None]]] = []
        self.ids: set[str] = set()

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        values = dict(attrs)
        self.tags.append((tag, values))
        if values.get("id"):
            self.ids.add(str(values["id"]))


class W03InteractionFoundationTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.html = (WEB / "index.html").read_text(encoding="utf-8")
        cls.js = (WEB / "app.js").read_text(encoding="utf-8")
        cls.events = (WEB / "src/events.js").read_text(encoding="utf-8") if (WEB / "src/events.js").is_file() else ""
        cls.parser = Capture()
        cls.parser.feed(cls.html)
        cls.parser.close()

    def test_deferred_local_script_is_loaded(self) -> None:
        scripts = [attrs for tag, attrs in self.parser.tags if tag == "script"]
        self.assertEqual(len(scripts), 1)
        self.assertEqual(scripts[0].get("src"), "app.js")
        self.assertIn("defer", scripts[0])
        self.assertFalse(str(scripts[0].get("src")).startswith(("http://", "https://")))

    def test_interactive_controls_have_react_semantic_targets(self) -> None:
        app = (WEB / "src/react/App.tsx").read_text(encoding="utf-8")
        for token in ("Today queue", "Quick Add", "Adaptive schedule", "Question practice", "Error Notebook", "Portable package", "Install & offline", "toast-region"):
            self.assertIn(token, app)
        self.assertIn("<form", app)
        self.assertIn("<button", app)

    def test_navigation_tabs_filters_and_forms_are_react_bound(self) -> None:
        app=(WEB/"src/react/App.tsx").read_text(encoding="utf-8")
        for token in ("useState", "useSyncExternalStore", "onClick", "onSubmit", "aria-current", "setPlanTab", "setPracticeTab", "setProgressTab"):
            self.assertIn(token, app)

    def test_focus_timer_uses_absolute_time_for_truth(self) -> None:
        domain=(WEB/"src/domain.ts").read_text(encoding="utf-8")
        app=(WEB/"src/react/App.tsx").read_text(encoding="utf-8")
        self.assertIn("endsAt", domain)
        self.assertIn("Date.parse", domain)
        self.assertIn("setInterval", app)
        self.assertIn("clearInterval", app)

    def test_w03_persistence_boundary_survives_react_migration(self) -> None:
        app=(WEB/"src/react/App.tsx").read_text(encoding="utf-8").lower()
        main=(WEB/"src/main.tsx").read_text(encoding="utf-8")
        self.assertNotIn("localstorage", app)
        self.assertNotIn("indexeddb.open", app)
        self.assertIn("createBrowserPersistence", main)
        self.assertIn("createStudyStore", main)

    def test_javascript_syntax_is_valid(self) -> None:
        result = subprocess.run(
            ["node", "--check", str(WEB / "app.js")],
            text=True,
            capture_output=True,
            check=False,
        )
        self.assertEqual(result.returncode, 0, result.stderr)


if __name__ == "__main__":
    unittest.main()
