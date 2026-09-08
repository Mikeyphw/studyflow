from __future__ import annotations

import json
import subprocess
import textwrap
import unittest
from pathlib import Path

WEB = Path(__file__).resolve().parents[1]
ROOT = WEB.parent


class Pc19InteractionRuntimeTest(unittest.TestCase):
    def test_localization_observer_is_idempotent_and_tracks_react_updates(self) -> None:
        script = textwrap.dedent(
            r"""
            const assert = require('node:assert/strict');
            let callback = null;
            let textWrites = 0;
            let attrWrites = 0;

            class FakeText {
              constructor(value) { this.nodeType = 3; this._value = value; this.childNodes = []; }
              get nodeValue() { return this._value; }
              set nodeValue(value) { textWrites += 1; this._value = value; }
            }
            class FakeElement {
              constructor(attrs = {}, children = []) { this.nodeType = 1; this.attrs = {...attrs}; this.childNodes = children; }
              getAttribute(name) { return Object.prototype.hasOwnProperty.call(this.attrs, name) ? this.attrs[name] : null; }
              setAttribute(name, value) { attrWrites += 1; this.attrs[name] = value; }
            }
            global.Node = { TEXT_NODE: 3 };
            global.Element = FakeElement;
            global.MutationObserver = class {
              constructor(cb) { callback = cb; }
              observe() {}
              disconnect() {}
            };

            const title = new FakeText('Today');
            const labelled = new FakeElement({'aria-label': 'Today'});
            const body = new FakeElement({}, [title, labelled]);
            global.document = { documentElement: {lang: ''}, body };
            const {installDocumentLocalization} = require('./runtime/product.js');

            const handle = installDocumentLocalization('pt-BR');
            assert.equal(title.nodeValue, 'Hoje');
            assert.equal(labelled.getAttribute('aria-label'), 'Hoje');
            assert.equal(textWrites, 1);
            assert.equal(attrWrites, 1);

            callback([
              {type: 'characterData', target: title, addedNodes: []},
              {type: 'attributes', target: labelled, addedNodes: []},
            ]);
            assert.equal(textWrites, 1, 'observer must not rewrite its own translated text');
            assert.equal(attrWrites, 1, 'observer must not rewrite its own translated attributes');

            // React commonly reuses the same Text node. A genuine UI update must become the
            // new canonical source instead of being reverted to the first observed label.
            title._value = 'Plan';
            callback([{type: 'characterData', target: title, addedNodes: []}]);
            assert.equal(title.nodeValue, 'Plano');
            assert.equal(textWrites, 2);
            callback([{type: 'characterData', target: title, addedNodes: []}]);
            assert.equal(textWrites, 2, 'translated React update must also settle idempotently');
            handle.dispose();
            """
        )
        result = subprocess.run(
            ["node", "-e", script], cwd=WEB, text=True, capture_output=True, check=False
        )
        self.assertEqual(result.returncode, 0, result.stderr or result.stdout)

    def test_interactive_ready_probe_and_dynamic_navigation_contract(self) -> None:
        app = (WEB / "src/react/App.tsx").read_text(encoding="utf-8")
        self.assertIn('dataset.studyflowInteractive="ready"', app)
        self.assertIn('delete document.documentElement.dataset.studyflowInteractive', app)
        self.assertIn('addEventListener("hashchange"', app)
        self.assertIn('onClick={()=>setSearchOpen(true)}', app)

    def test_service_worker_cache_is_content_addressed(self) -> None:
        worker = (WEB / "service-worker.js").read_text(encoding="utf-8")
        build = (WEB / "scripts/build_dist.mjs").read_text(encoding="utf-8")
        check = (WEB / "scripts/check_release.mjs").read_text(encoding="utf-8")
        self.assertIn('__STUDYFLOW_CACHE_VERSION__', worker)
        self.assertIn('const cacheSeed=', build)
        self.assertIn('createHash("sha256").update(cacheSeed)', build)
        self.assertIn('cacheVersion', build)
        self.assertIn('manifest.cacheVersion', check)
        self.assertIn('service worker cache version does not match release manifest', check)

    def test_pwa_manifest_is_dark_only(self) -> None:
        manifest = json.loads((WEB / "manifest.webmanifest").read_text(encoding="utf-8"))
        self.assertEqual(manifest["background_color"], "#0b0f14")
        self.assertEqual(manifest["theme_color"], "#121923")
        css = (WEB / "styles.css").read_text(encoding="utf-8")
        self.assertIn("color-scheme: dark", css)
        self.assertNotIn("color-scheme: light", css)

    def test_real_device_visual_shell_regressions_are_closed(self) -> None:
        css = (WEB / "styles.css").read_text(encoding="utf-8")
        self.assertIn(".skip-link {", css)
        self.assertIn("transform: translateY(calc(-100% - 1rem))", css)
        self.assertIn(".skip-link:focus-visible", css)
        self.assertIn('input:not([type="checkbox"]):not([type="radio"]):not([type="file"])', css)
        self.assertIn("textarea:focus-visible", css)
        self.assertIn("background: var(--surface-muted)", css)
        self.assertIn("select {\n    color-scheme: dark;", css)

    def test_encryption_copy_matches_pc18_capability(self) -> None:
        product = (WEB / "src/product.ts").read_text(encoding="utf-8")
        self.assertNotIn("Encrypted portable backups are not included", product)
        self.assertNotIn("Backups portáteis criptografados não fazem parte", product)
        self.assertIn("Password-encrypted portable backups are supported locally", product)
        self.assertIn("Backups portáteis protegidos por senha são suportados localmente", product)

    def test_pc18_seal_is_superseded_by_runtime_audit(self) -> None:
        roadmap = (ROOT / "docs/web/WEB_ROADMAP.md").read_text(encoding="utf-8")
        doc = (ROOT / "docs/web/PC19_INTERACTION_RUNTIME_AUDIT_HOTFIX.md").read_text(encoding="utf-8")
        self.assertIn("PC19", roadmap)
        for token in (
            "main-thread starvation",
            "MutationObserver",
            "content-addressed",
            "browser interaction smoke",
            "dark-only",
        ):
            self.assertIn(token, doc)


if __name__ == "__main__":
    unittest.main()
