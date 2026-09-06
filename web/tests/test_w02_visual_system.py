from __future__ import annotations

import re
import unittest
from pathlib import Path

WEB = Path(__file__).resolve().parents[1]


class W02VisualSystemTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.html = (WEB / "index.html").read_text(encoding="utf-8")
        cls.css = (WEB / "styles.css").read_text(encoding="utf-8")

    def test_external_stylesheet_and_no_inline_style_ownership(self) -> None:
        self.assertIn('rel="stylesheet"', self.html)
        self.assertIn('href="styles.css"', self.html)
        self.assertNotIn("style=", self.html.lower())
        self.assertNotIn("<style", self.html.lower())

    def test_mobile_first_tokens_and_box_model_are_present(self) -> None:
        for token in (
            "--bg", "--surface", "--text", "--muted", "--border", "--accent",
            "--success", "--warning", "--danger", "--tap-size", "--content-max",
        ):
            self.assertIn(token, self.css)
        self.assertRegex(self.css, r"\*::before[\s\S]*box-sizing:\s*border-box")
        self.assertIn("--tap-size: 44px", self.css)

    def test_phone_bottom_navigation_is_fixed_and_safe_area_aware(self) -> None:
        block = re.search(r"\.bottom-nav\s*\{([^}]*)\}", self.css, re.S)
        self.assertIsNotNone(block)
        assert block
        self.assertIn("position: fixed", block.group(1))
        self.assertIn("grid-template-columns: repeat(4", block.group(1))
        self.assertIn("env(safe-area-inset-bottom)", self.css)

    def test_wide_layout_is_progressive_enhancement(self) -> None:
        self.assertIn("@media (min-width: 700px)", self.css)
        self.assertNotIn("@media (max-width:", self.css)
        self.assertIn("clamp(", self.css)
        self.assertIn("repeat(3, minmax(0, 1fr))", self.css)

    def test_accessibility_motion_and_focus_rules_exist(self) -> None:
        self.assertIn("prefers-reduced-motion: reduce", self.css)
        self.assertIn(":focus-visible", self.css)
        self.assertIn("scroll-behavior: auto !important", self.css)


if __name__ == "__main__":
    unittest.main()
