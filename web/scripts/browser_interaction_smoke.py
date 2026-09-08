#!/usr/bin/env python3
"""Instrumented StudyFlow browser interaction smoke.

Runs without an HTTP server by injecting the tracked CommonJS runtime and vendored React
files into an about:blank Chromium page. This specifically catches event-loop starvation,
React event-delegation failures, and localization code that rewrites dynamic React text.

Use --optional in environments without Playwright/Chromium; absence then reports SKIP.
"""
from __future__ import annotations

import argparse
import json
import os
import shutil
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DIST = ROOT / "dist"


def find_browser() -> str | None:
    explicit = os.environ.get("STUDYFLOW_CHROMIUM")
    if explicit and Path(explicit).is_file():
        return explicit
    for name in ("chromium", "chromium-browser", "google-chrome", "google-chrome-stable"):
        found = shutil.which(name)
        if found:
            return found
    return None


def build_bundle() -> str:
    sources: dict[str, str] = {}
    for path in sorted((ROOT / "runtime").rglob("*.js")):
        sources[f"https://app.local/runtime/{path.relative_to(ROOT / 'runtime').as_posix()}"] = path.read_text(encoding="utf-8")
    for path in sorted((DIST / "vendor").glob("*.js")):
        sources[f"https://app.local/vendor/{path.name}"] = path.read_text(encoding="utf-8")
    required = {
        "https://app.local/vendor/react.production.js",
        "https://app.local/vendor/react-dom-client.production.js",
        "https://app.local/vendor/react-dom.production.js",
        "https://app.local/vendor/scheduler.production.js",
    }
    missing = sorted(required - set(sources))
    if missing:
        raise RuntimeError(f"vendored browser runtime missing: {', '.join(missing)}")
    return f"""
const SOURCE_MAP={json.dumps(sources)};
const PACKAGE_MODULES=Object.freeze({{
 react:'https://app.local/vendor/react.production.js',
 'react-dom':'https://app.local/vendor/react-dom.production.js',
 'react-dom/client':'https://app.local/vendor/react-dom-client.production.js',
 scheduler:'https://app.local/vendor/scheduler.production.js'
}});
const sources=new Map(Object.entries(SOURCE_MAP)),modules=new Map(),processObject=Object.freeze({{env:Object.freeze({{NODE_ENV:'production'}})}});
function resolveModule(s,p){{if(Object.prototype.hasOwnProperty.call(PACKAGE_MODULES,s))return PACKAGE_MODULES[s];if(!s.startsWith('.')&&!s.startsWith('/'))throw new Error('Unsupported '+s);let u=new URL(s,p);if(!/\\.[a-z0-9]+$/i.test(u.pathname))u=new URL(u.href+'.js');return u.href}}
function execute(u){{if(modules.has(u))return modules.get(u).exports;const source=sources.get(u);if(source==null)throw new Error('missing '+u);const module={{exports:{{}}}};modules.set(u,module);const req=s=>execute(resolveModule(s,u));new Function('module','exports','require','process','globalThis',source)(module,module.exports,req,processObject,globalThis);return module.exports}}
const React=execute('https://app.local/vendor/react.production.js'),client=execute('https://app.local/vendor/react-dom-client.production.js'),{{createStudyStore}}=execute('https://app.local/runtime/state.js'),{{App}}=execute('https://app.local/runtime/react/App.js');
const repo={{load(){{return{{status:'empty',state:null}}}},save(){{return{{status:'saved'}}}}}};
const store=createStudyStore({{repository:repo}});globalThis.__studyflowSmokeStore=store;
client.createRoot(document.getElementById('root')).render(React.createElement(App,{{store,persistence:{{backend:'browser-smoke'}}}}));
"""


def run_smoke(browser_path: str) -> None:
    from playwright.sync_api import sync_playwright

    errors: list[str] = []
    bundle = build_bundle()
    with tempfile.NamedTemporaryFile("w", suffix=".js", encoding="utf-8", delete=False) as handle:
        handle.write(bundle)
        bundle_path = handle.name
    try:
        with sync_playwright() as p:
            browser = p.chromium.launch(headless=True, executable_path=browser_path, args=["--no-sandbox"])
            page = browser.new_page(viewport={"width": 1100, "height": 800})
            page.on("pageerror", lambda exc: errors.append(str(exc)))
            page.set_content('<!doctype html><html><body><div id="root"></div></body></html>')
            page.add_script_tag(path=bundle_path)
            page.wait_for_function("document.documentElement.dataset.studyflowInteractive === 'ready'", timeout=3000)

            if page.locator(".onboarding-backdrop").count():
                page.get_by_role("button", name="Start workspace").click()
                page.wait_for_function("!document.querySelector('.onboarding-backdrop')", timeout=2000)

            page.get_by_role("link", name="Plan").click()
            page.wait_for_function("!!document.querySelector('#plan')", timeout=2000)
            if page.locator("#screen-title").inner_text() != "Plan":
                raise AssertionError("dynamic screen title did not follow Plan navigation")

            page.get_by_role("link", name="Today").click()
            page.wait_for_function("!!document.querySelector('#today')", timeout=2000)
            page.get_by_role("button", name="Search").click()
            page.wait_for_function("!!document.querySelector('.modal-card')", timeout=2000)
            page.get_by_role("button", name="Close").click()

            page.get_by_role("button", name="Start").click()
            page.wait_for_function("__studyflowSmokeStore.getState().focus.status === 'running'", timeout=2000)
            page.get_by_role("button", name="Pause").click()
            page.wait_for_function("__studyflowSmokeStore.getState().focus.status === 'paused'", timeout=2000)

            page.get_by_label("Activity title").fill("Browser smoke activity")
            page.get_by_role("button", name="Add activity").click()
            page.wait_for_function("__studyflowSmokeStore.getState().activities.some(x => x.title === 'Browser smoke activity')", timeout=2000)

            page.get_by_role("button", name="Settings").click()
            page.get_by_label("Application language").select_option("pt-BR")
            page.wait_for_function("__studyflowSmokeStore.getState().preferences.locale === 'pt-BR'", timeout=2000)
            page.get_by_role("button", name="Fechar").click()
            page.get_by_role("link", name="Plano").click()
            page.wait_for_function("document.querySelector('#screen-title')?.textContent === 'Plano'", timeout=2000)

            if errors:
                raise AssertionError("browser page errors: " + " | ".join(errors))
            browser.close()
    finally:
        Path(bundle_path).unlink(missing_ok=True)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--optional", action="store_true", help="report SKIP instead of failing when browser instrumentation is unavailable")
    args = parser.parse_args()
    try:
        import playwright.sync_api  # noqa: F401
    except Exception as exc:
        if args.optional:
            print(f"SKIP browser interaction smoke: Playwright unavailable ({exc})")
            return 0
        raise SystemExit(f"Playwright unavailable: {exc}")
    browser = find_browser()
    if not browser:
        if args.optional:
            print("SKIP browser interaction smoke: Chromium executable unavailable")
            return 0
        raise SystemExit("Chromium executable unavailable; set STUDYFLOW_CHROMIUM")
    run_smoke(browser)
    print(f"StudyFlow browser interaction smoke passed ({browser})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
