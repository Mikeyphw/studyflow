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

    def test_interactive_controls_have_real_semantic_targets(self) -> None:
        for element_id in (
            "quick-add-dialog", "quick-add-form", "today-activity-list",
            "focus-timer", "focus-start", "focus-pause", "focus-reset",
            "practice-question-form", "practice-feedback", "toast-region",
        ):
            self.assertIn(element_id, self.parser.ids)
        tags = [tag for tag, _ in self.parser.tags]
        self.assertIn("dialog", tags)
        self.assertIn("form", tags)
        self.assertIn("fieldset", tags)
        self.assertIn("legend", tags)

    def test_navigation_tabs_dialogs_filters_and_forms_are_bound(self) -> None:
        for token in (
            "data-screen-link", "data-tab-nav", "data-schedule-filter",
            "showScreen", "activateTab", "showModal", "FormData",
            "addEventListener", "aria-current", "aria-pressed",
            "createElement", "textContent",
        ):
            self.assertIn(token, self.js + self.events + self.html)

    def test_focus_timer_uses_absolute_time_for_truth(self) -> None:
        domain = (WEB / "src/domain.js").read_text(encoding="utf-8") if (WEB / "src/domain.js").is_file() else ""
        self.assertIn("endsAt", self.js + domain)
        self.assertTrue("Date.now()" in self.js + domain or "Date.parse" in self.js + domain)
        self.assertIn("setInterval", self.js)
        self.assertIn("clearInterval", self.js)

    def test_w03_keeps_persistence_behind_dedicated_modules(self) -> None:
        lowered = self.js.lower()
        self.assertNotIn("localstorage", lowered)
        self.assertNotIn("globalthis.indexeddb", lowered)
        self.assertNotIn("fetch(", lowered)
        self.assertIn("createbrowserpersistence", lowered)

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
