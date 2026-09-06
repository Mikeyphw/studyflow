#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import sys
import tomllib
from html.parser import HTMLParser
from pathlib import Path

WEB = Path(__file__).resolve().parents[1]
ROOT = WEB.parent


def fail(errors: list[str], condition: bool, message: str) -> None:
    if condition:
        errors.append(message)


def read_text(path: Path, errors: list[str]) -> str:
    if not path.is_file():
        errors.append(f"missing file: {path.relative_to(ROOT)}")
        return ""
    return path.read_text(encoding="utf-8")


def validate_contract() -> list[str]:
    errors: list[str] = []
    devtool = ROOT / ".devtool.toml"
    if not devtool.is_file():
        return ["missing .devtool.toml"]
    try:
        config = tomllib.loads(devtool.read_text(encoding="utf-8"))
    except Exception as exc:
        return [f".devtool.toml parse failed: {exc}"]

    targets = config.get("targets", {})
    web = targets.get("studyflow_web", {})
    fail(errors, not isinstance(web, dict), "missing targets.studyflow_web")
    if isinstance(web, dict):
        fail(errors, web.get("type") != "node", "studyflow_web type must be node")
        fail(errors, web.get("root") != "web", "studyflow_web root must be web")
        fail(errors, web.get("execution_environment") != "native-termux", "studyflow_web execution_environment must be native-termux")
        fail(errors, "tasks" in web, "Node target must not retain generic targets.studyflow_web.tasks")
        validation = web.get("validation", {})
        fail(errors, validation.get("max_workers") != 1, "studyflow_web validation max_workers must be 1")
        fail(errors, validation.get("parallel") is not False, "studyflow_web validation parallel must be false")
        fail(errors, validation.get("cpu_limit") != 1, "studyflow_web validation cpu_limit must be 1")
        fail(errors, "phases" in validation, "Node target must not retain generic validation.phases")

    fail(errors, "studyflow_android" not in targets, "existing studyflow_android target must be preserved")
    fail(errors, "studyflow_desktop" not in targets, "existing studyflow_desktop target must be preserved")
    fail(errors, config.get("project", {}).get("default_target") != "studyflow_desktop", "W00 must not change the established default target")

    package_path = WEB / "package.json"
    package_text = read_text(package_path, errors)
    if package_text:
        try:
            package = json.loads(package_text)
        except Exception as exc:
            errors.append(f"package.json parse failed: {exc}")
            package = {}
        fail(errors, package.get("name") != "studyflow-local-web", "package name drift")
        fail(errors, package.get("private") is not True, "web package must remain private")
        scripts = package.get("scripts", {})
        for script in ("build", "test", "lint", "dev"):
            fail(errors, not str(scripts.get(script, "")).strip(), f"missing npm script: {script}")
        fail(errors, bool(package.get("dependencies")), "W00 must not add runtime dependencies")
        fail(errors, bool(package.get("devDependencies")), "W00 must not add dev dependencies")

    lock_text = read_text(WEB / "package-lock.json", errors)
    if lock_text:
        try:
            lock = json.loads(lock_text)
            fail(errors, lock.get("lockfileVersion") != 3, "package-lock.json must use lockfileVersion 3")
        except Exception as exc:
            errors.append(f"package-lock.json parse failed: {exc}")

    readme = read_text(WEB / "README.md", errors)
    for token in ("mobile-first", "no account", "Today → Plan → Practice → Progress", "dependency-free", "Node target"):
        fail(errors, token.lower() not in readme.lower(), f"web README missing contract token: {token}")

    contract = read_text(ROOT / "docs/web/W00_WEB_PRODUCT_CONTRACT.md", errors)
    for token in ("Mobile first", "Local ownership", "Offline core", "Truthful evidence", "Preview before mutation", "Framework-independent domain"):
        fail(errors, token.lower() not in contract.lower(), f"W00 contract missing: {token}")

    read_text(ROOT / "docs/web/WEB_ROADMAP.md", errors)
    read_text(WEB / ".npmrc", errors)
    return errors


class ShellParser(HTMLParser):
    def __init__(self) -> None:
        super().__init__(convert_charrefs=True)
        self.tags: list[tuple[str, dict[str, str | None]]] = []
        self.doctype = False
        self.ids: set[str] = set()
        self.hrefs: list[str] = []
        self.main_count = 0
        self.script_count = 0
        self.style_count = 0

    def handle_decl(self, decl: str) -> None:
        if decl.lower().strip() == "doctype html":
            self.doctype = True

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        values = dict(attrs)
        self.tags.append((tag, values))
        if values.get("id"):
            self.ids.add(str(values["id"]))
        if tag == "a" and values.get("href"):
            self.hrefs.append(str(values["href"]))
        if tag == "main":
            self.main_count += 1
        elif tag == "script":
            self.script_count += 1
        elif tag == "style":
            self.style_count += 1


def validate_shell() -> list[str]:
    errors = validate_contract()
    index = WEB / "index.html"
    text = read_text(index, errors)
    if not text:
        return errors
    parser = ShellParser()
    try:
        parser.feed(text)
        parser.close()
    except Exception as exc:
        errors.append(f"index.html parse failed: {exc}")
        return errors

    fail(errors, not parser.doctype, "index.html must declare <!doctype html>")
    html_attrs = next((attrs for tag, attrs in parser.tags if tag == "html"), {})
    fail(errors, not str(html_attrs.get("lang") or "").strip(), "html lang is required")
    metas = [attrs for tag, attrs in parser.tags if tag == "meta"]
    fail(errors, not any(m.get("charset") == "utf-8" for m in metas), "utf-8 meta charset is required")
    fail(errors, not any(m.get("name") == "viewport" and "width=device-width" in str(m.get("content") or "") for m in metas), "mobile viewport meta is required")
    fail(errors, parser.main_count != 1, "exactly one main element is required")
    fail(errors, parser.script_count != 0, "W01 must not introduce JavaScript; W03 owns interaction")
    fail(errors, parser.style_count != 0 or "style=" in text.lower(), "W01 must not introduce CSS; W02 owns presentation")

    required_ids = {
        "today", "plan", "practice", "progress",
        "plan-overview", "plan-schedule", "plan-content", "plan-library",
        "practice-reviews", "practice-questions", "practice-tests", "practice-errors",
        "progress-summary", "progress-performance", "progress-goals", "progress-tools",
    }
    for required in sorted(required_ids):
        fail(errors, required not in parser.ids, f"missing semantic section id: {required}")

    for target in ("#today", "#plan", "#practice", "#progress"):
        fail(errors, target not in parser.hrefs, f"main navigation missing working anchor: {target}")

    for href in parser.hrefs:
        if href.startswith("#") and len(href) > 1:
            fail(errors, href[1:] not in parser.ids, f"anchor points to missing id: {href}")

    fail(errors, "Today" not in text or "Plan" not in text or "Practice" not in text or "Progress" not in text, "primary product labels missing")
    return errors


def main() -> int:
    parser = argparse.ArgumentParser()
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--contract", action="store_true")
    mode.add_argument("--shell", action="store_true")
    mode.add_argument("--all", action="store_true")
    args = parser.parse_args()

    errors = validate_shell() if (args.shell or args.all) else validate_contract()
    if errors:
        print("StudyFlow web validation FAILED:", file=sys.stderr)
        for error in errors:
            print(f"- {error}", file=sys.stderr)
        return 1
    label = "W01 semantic shell" if (args.shell or args.all) else "W00 product contract"
    print(f"StudyFlow web validation passed: {label}.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
