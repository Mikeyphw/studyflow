#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import re
import subprocess
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
    fail(errors, config.get("project", {}).get("default_target") != "studyflow_desktop", "web overlays must not change the established default target")

    package_text = read_text(WEB / "package.json", errors)
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
        fail(errors, bool(package.get("dependencies")), "web target must remain dependency-free")
        fail(errors, bool(package.get("devDependencies")), "web target must remain dependency-free")

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
        self.stylesheets: list[str] = []
        self.scripts: list[dict[str, str | None]] = []

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
        elif tag == "link" and values.get("rel") == "stylesheet" and values.get("href"):
            self.stylesheets.append(str(values["href"]))
        elif tag == "script":
            self.scripts.append(values)


def parse_shell(errors: list[str]) -> tuple[str, ShellParser | None]:
    index = WEB / "index.html"
    text = read_text(index, errors)
    if not text:
        return "", None
    parser = ShellParser()
    try:
        parser.feed(text)
        parser.close()
    except Exception as exc:
        errors.append(f"index.html parse failed: {exc}")
        return text, None
    return text, parser


def validate_shell() -> list[str]:
    errors = validate_contract()
    text, parser = parse_shell(errors)
    if parser is None:
        return errors

    fail(errors, not parser.doctype, "index.html must declare <!doctype html>")
    html_attrs = next((attrs for tag, attrs in parser.tags if tag == "html"), {})
    fail(errors, not str(html_attrs.get("lang") or "").strip(), "html lang is required")
    metas = [attrs for tag, attrs in parser.tags if tag == "meta"]
    fail(errors, not any(m.get("charset") == "utf-8" for m in metas), "utf-8 meta charset is required")
    fail(errors, not any(m.get("name") == "viewport" and "width=device-width" in str(m.get("content") or "") for m in metas), "mobile viewport meta is required")
    fail(errors, parser.main_count != 1, "exactly one main element is required")
    fail(errors, "style=" in text.lower() or "<style" in text.lower(), "presentation must remain external; inline styles are forbidden")

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


def validate_visual() -> list[str]:
    errors = validate_shell()
    text, parser = parse_shell(errors)
    if parser is None:
        return errors
    css = read_text(WEB / "styles.css", errors)
    if not css:
        return errors

    fail(errors, parser.stylesheets != ["styles.css"], "W02 requires exactly one local styles.css stylesheet")
    for token in (
        "--bg", "--surface", "--text", "--muted", "--border", "--accent",
        "--success", "--warning", "--danger", "--tap-size", "--content-max",
    ):
        fail(errors, token not in css, f"styles.css missing design token: {token}")
    fail(errors, "box-sizing: border-box" not in css, "W02 requires universal border-box sizing")
    fail(errors, "--tap-size: 44px" not in css, "W02 touch target baseline must remain 44px")
    fail(errors, not re.search(r"\.bottom-nav\s*\{[^}]*position:\s*fixed", css, re.S), "bottom navigation must remain fixed on the phone baseline")
    fail(errors, "env(safe-area-inset-bottom)" not in css, "bottom safe-area handling is required")
    fail(errors, "@media (min-width: 700px)" not in css, "W02 wide-screen progressive enhancement breakpoint missing")
    fail(errors, "@media (max-width:" in css, "W02 must remain mobile-first; max-width media-query baseline is forbidden")
    fail(errors, "prefers-reduced-motion: reduce" not in css, "reduced-motion handling missing")
    fail(errors, ":focus-visible" not in css, "visible keyboard focus treatment missing")
    fail(errors, "clamp(" not in css, "fluid clamp typography missing")
    fail(errors, "url(http://" in css.lower() or "url(https://" in css.lower(), "W02 must not depend on remote CSS assets")
    return errors


def validate_interactions() -> list[str]:
    errors = validate_visual()
    text, parser = parse_shell(errors)
    if parser is None:
        return errors
    js = read_text(WEB / "app.js", errors)
    if not js:
        return errors

    fail(errors, len(parser.scripts) != 1, "W03 requires exactly one local application script")
    if parser.scripts:
        script = parser.scripts[0]
        fail(errors, script.get("src") != "app.js", "application script must be app.js")
        fail(errors, "defer" not in script, "app.js must be loaded with defer")
        fail(errors, str(script.get("src") or "").startswith(("http://", "https://")), "remote application scripts are forbidden")

    for element_id in (
        "quick-add-dialog", "quick-add-form", "today-activity-list",
        "focus-timer", "focus-start", "focus-pause", "focus-reset",
        "practice-question-form", "practice-feedback", "toast-region",
    ):
        fail(errors, element_id not in parser.ids, f"W03 interactive element missing: {element_id}")

    domain_clock = read_text(WEB / "src/domain.js", []) if (WEB / "src/domain.js").is_file() else ""
    interaction_source = js + "\n" + text + "\n" + domain_clock + "\n" + (read_text(WEB / "src/events.js", []) if (WEB / "src/events.js").is_file() else "")
    for token in (
        "showScreen", "activateTab", "showModal", "FormData", "addEventListener",
        "aria-current", "aria-pressed", "createElement", "textContent",
        "endsAt", "setInterval", "clearInterval",
    ):
        fail(errors, token not in interaction_source, f"W03 interaction contract missing token: {token}")

    lowered = js.lower()
    for forbidden in ("localstorage", "indexeddb", "fetch("):
        fail(errors, forbidden in lowered, f"W03 must not cross persistence/network boundary: {forbidden}")

    try:
        result = subprocess.run(
            ["node", "--check", str(WEB / "app.js")],
            text=True,
            capture_output=True,
            check=False,
            timeout=30,
        )
    except Exception as exc:
        errors.append(f"node --check app.js failed to run: {exc}")
    else:
        if result.returncode != 0:
            errors.append(f"app.js syntax invalid: {result.stderr.strip() or result.stdout.strip()}")

    doc = read_text(ROOT / "docs/web/W02_W03_VISUAL_INTERACTION_FOUNDATION.md", errors)
    for token in ("mobile-first", "44px", "prefers-reduced-motion", "Quick Add", "ephemeral", "W04"):
        fail(errors, token.lower() not in doc.lower(), f"W02/W03 delivery doc missing: {token}")
    return errors



def _node_check(errors: list[str], path: Path) -> None:
    try:
        result = subprocess.run(
            ["node", "--check", str(path)],
            text=True,
            capture_output=True,
            check=False,
            timeout=30,
            cwd=WEB,
        )
    except Exception as exc:
        errors.append(f"node --check {path.relative_to(WEB)} failed to run: {exc}")
        return
    if result.returncode != 0:
        errors.append(f"{path.relative_to(WEB)} syntax invalid: {result.stderr.strip() or result.stdout.strip()}")


def validate_state() -> list[str]:
    errors = validate_interactions()
    package_text = read_text(WEB / "package.json", errors)
    if package_text:
        try:
            package = json.loads(package_text)
        except Exception:
            package = {}
        fail(errors, package.get("type") != "module", "W04 requires package type=module for native ES modules")

    html, parser = parse_shell(errors)
    if parser is not None:
        for element_id in ("persistence-status", "plan-subject-list", "reset-local-state"):
            fail(errors, element_id not in parser.ids, f"W04 state UI hook missing: {element_id}")
        if parser.scripts:
            fail(errors, parser.scripts[0].get("type") != "module", "W04 app.js must load as an ES module")

    modules = {
        "storage": read_text(WEB / "src/storage.js", errors),
        "state": read_text(WEB / "src/state.js", errors),
        "render": read_text(WEB / "src/render.js", errors),
        "events": read_text(WEB / "src/events.js", errors),
    }
    storage = modules["storage"]
    state = modules["state"]
    app = read_text(WEB / "app.js", errors)
    for token in ("studyflow.local.state.v", "JSON.parse", "JSON.stringify", "validateStudyState", "getItem", "setItem", "removeItem"):
        fail(errors, token not in storage, f"W04 storage contract missing token: {token}")
    for token in ("createStudyStore", "subscribe", "addActivity", "resetLocalState", "repository.save"):
        fail(errors, token not in state, f"W04 state contract missing token: {token}")
    for token in ("createBrowserRepository", "createStudyStore", "renderStudyState", "bindPersistentStudyEvents"):
        fail(errors, token not in app, f"W04 application wiring missing token: {token}")
    lowered = "\n".join(modules.values()).lower()
    fail(errors, "indexeddb" in lowered, "W04 must not steal W12 IndexedDB scope")
    fail(errors, "fetch(" in lowered, "W04 local state must not introduce network access")
    for relative in ("app.js", "src/storage.js", "src/state.js", "src/render.js", "src/events.js"):
        _node_check(errors, WEB / relative)

    doc = read_text(ROOT / "docs/web/W04_W05_STATE_DOMAIN_FOUNDATION.md", errors)
    for token in ("localStorage", "schemaVersion", "Quick Add", "in memory only", "IndexedDB", "W05"):
        fail(errors, token.lower() not in doc.lower(), f"W04/W05 delivery doc missing: {token}")
    return errors


def validate_domain() -> list[str]:
    errors = validate_state()
    domain = read_text(WEB / "src/domain.js", errors)
    for token in (
        "createPlan", "createSubject", "createTopic", "createActivity", "createStudySession",
        "createGoal", "createMaterial", "createReview", "createQuestion", "createEvidence",
        "createEntityId", "validateStudyState", "selectActivePlan", "selectSubjectsForPlan",
        "selectTopicsForSubject", "selectActivitiesForQueue", "derivePlanInventory",
        "EXAM_PLAN", "PROGRAMMING_COURSE", "LANGUAGE_DECK", "READING_STUDY", "GENERAL_SKILL",
    ):
        fail(errors, token not in domain, f"W05 domain contract missing token: {token}")
    lowered = domain.lower()
    for forbidden in ("document.", "window.", "localstorage", "indexeddb", "htmlelement", "fetch("):
        fail(errors, forbidden in lowered, f"W05 domain must remain UI/storage/network independent: {forbidden}")
    _node_check(errors, WEB / "src/domain.js")
    return errors


def validate_focus() -> list[str]:
    errors = validate_domain()
    html, parser = parse_shell(errors)
    state = read_text(WEB / "src/state.js", errors)
    domain = read_text(WEB / "src/domain.js", errors)
    storage = read_text(WEB / "src/storage.js", errors)
    app = read_text(WEB / "app.js", errors)
    if parser is not None:
        for element_id in ("focus-complete", "focus-cancel", "focus-note", "focus-history", "recommendation-title", "today-overdue-review-count", "study-cycle-next", "study-cycle-advance"):
            fail(errors, element_id not in parser.ids, f"W06 focus UI hook missing: {element_id}")
    for token in ("createFocusState", "focusRemainingMs", "recommendNextActivity", "selectOverdueReviews", "createStudyCycleState", "selectCurrentCycleSubject"):
        fail(errors, token not in domain, f"W06 domain contract missing token: {token}")
    for token in ("startFocus", "pauseFocus", "completeFocus", "cancelFocus", "getFocusSnapshot", "focus-session-completed", "advanceStudyCycle", "setStudyCycle"):
        fail(errors, token not in state, f"W06 store contract missing token: {token}")
    for token in ("studyflow.local.state.v1", "LEGACY_STORAGE_KEYS", "migrated"):
        fail(errors, token not in storage, f"W06 migration contract missing token: {token}")
    for token in ("setInterval", "clearInterval", "remainingMs"):
        fail(errors, token not in app, f"W06 timer orchestration missing token: {token}")
    doc = read_text(ROOT / "docs/web/W06_W07_TODAY_FOCUS_PLAN_CONTENT.md", errors)
    for token in ("absolute", "StudySession", "Evidence", "migration", "overdue"):
        fail(errors, token.lower() not in doc.lower(), f"W06/W07 delivery doc missing: {token}")
    return errors


def validate_plan_content() -> list[str]:
    errors = validate_focus()
    html, parser = parse_shell(errors)
    state = read_text(WEB / "src/state.js", errors)
    domain = read_text(WEB / "src/domain.js", errors)
    render = read_text(WEB / "src/render.js", errors)
    events = read_text(WEB / "src/events.js", errors)
    if parser is not None:
        for element_id in ("content-search", "content-filter", "add-subject", "subject-dialog", "subject-form", "topic-dialog", "topic-form"):
            fail(errors, element_id not in parser.ids, f"W07 Plan Content UI hook missing: {element_id}")
    for token in ("addSubject", "updateSubject", "deleteSubject", "moveSubject", "addTopic", "updateTopic", "deleteTopic", "moveTopic"):
        fail(errors, token not in state, f"W07 state contract missing token: {token}")
    for token in ("selectAllSubjectsForPlan", "selectAllTopicsForSubject", "notes", "order"):
        fail(errors, token not in domain, f"W07 domain contract missing token: {token}")
    for token in ("evidence", "content", "filter"):
        fail(errors, token.lower() not in (render + events).lower(), f"W07 rendering/event contract missing token: {token}")
    lowered = domain.lower()
    for forbidden in ("document.", "window.", "localstorage", "indexeddb", "fetch("):
        fail(errors, forbidden in lowered, f"W07 must keep domain independent: {forbidden}")
    for relative in ("src/domain.js", "src/storage.js", "src/state.js", "src/render.js", "src/events.js", "app.js"):
        _node_check(errors, WEB / relative)
    return errors


def validate_schedule() -> list[str]:
    errors = validate_plan_content()
    html, parser = parse_shell(errors)
    domain = read_text(WEB / "src/domain.js", errors)
    state = read_text(WEB / "src/state.js", errors)
    render = read_text(WEB / "src/render.js", errors)
    events = read_text(WEB / "src/events.js", errors)
    if parser is not None:
        for element_id in ("schedule-preview", "schedule-apply", "schedule-preview-list", "schedule-current-list", "schedule-preview-status"):
            fail(errors, element_id not in parser.ids, f"W08 schedule UI hook missing: {element_id}")
    for token in ("buildSchedulePreview", "baseRevision", "operations", "Overdue planned activity", "scheduledFor"):
        fail(errors, token not in domain, f"W08 schedule-domain contract missing token: {token}")
    for token in ("previewSchedule", "applySchedulePreview", "Schedule preview is stale"):
        fail(errors, token not in state, f"W08 schedule-store contract missing token: {token}")
    for token in ("renderScheduleState", "Preview ready"):
        fail(errors, token not in render, f"W08 schedule rendering missing token: {token}")
    fail(errors, "schedule-preview" not in events or "schedule-apply" not in events, "W08 schedule event wiring missing")
    doc = read_text(ROOT / "docs/web/W08_W09_SCHEDULE_PRACTICE.md", errors)
    for token in ("preview before apply", "stale", "immutable history", "overdue review"):
        fail(errors, token.lower() not in doc.lower(), f"W08/W09 delivery doc missing: {token}")
    return errors


def validate_practice() -> list[str]:
    errors = validate_schedule()
    html, parser = parse_shell(errors)
    domain = read_text(WEB / "src/domain.js", errors)
    state = read_text(WEB / "src/state.js", errors)
    render = read_text(WEB / "src/render.js", errors)
    events = read_text(WEB / "src/events.js", errors)
    storage = read_text(WEB / "src/storage.js", errors)
    if parser is not None:
        for element_id in ("practice-question-select", "practice-question-form", "practice-review-list", "practice-error-list", "question-dialog", "question-form"):
            fail(errors, element_id not in parser.ids, f"W09 practice UI hook missing: {element_id}")
    for token in ("createErrorEntry", "ERROR_CATEGORIES", "gradeQuestion", "deriveQuestionStats", "selectOpenErrors"):
        fail(errors, token not in domain, f"W09 practice-domain contract missing token: {token}")
    for token in ("addQuestion", "answerQuestion", "completeReview", "resolveError", "question-attempt"):
        fail(errors, token not in state, f"W09 practice-store contract missing token: {token}")
    for token in ("renderPracticeState", "graded accuracy", "recurrence"):
        fail(errors, token not in render, f"W09 practice rendering missing token: {token}")
    for token in ("practice-question-form", "data-complete-review", "data-resolve-error"):
        fail(errors, token not in events, f"W09 event wiring missing token: {token}")
    for token in ("studyflow.local.state.v2", "studyflow.local.state.v1"):
        fail(errors, token not in storage, f"W09 state migration missing legacy key: {token}")
    for relative in ("src/domain.js", "src/storage.js", "src/state.js", "src/render.js", "src/events.js", "app.js"):
        _node_check(errors, WEB / relative)
    return errors

def main() -> int:
    parser = argparse.ArgumentParser()
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--contract", action="store_true")
    mode.add_argument("--shell", action="store_true")
    mode.add_argument("--visual", action="store_true")
    mode.add_argument("--interactions", action="store_true")
    mode.add_argument("--state", action="store_true")
    mode.add_argument("--domain", action="store_true")
    mode.add_argument("--focus", action="store_true")
    mode.add_argument("--content", action="store_true")
    mode.add_argument("--schedule", action="store_true")
    mode.add_argument("--practice", action="store_true")
    mode.add_argument("--all", action="store_true")
    args = parser.parse_args()

    if args.contract:
        errors = validate_contract()
        label = "W00 product contract"
    elif args.shell:
        errors = validate_shell()
        label = "W01 semantic shell"
    elif args.visual:
        errors = validate_visual()
        label = "W02 visual system"
    elif args.interactions:
        errors = validate_interactions()
        label = "W02/W03 visual + interaction foundation"
    elif args.state:
        errors = validate_state()
        label = "W04 application state + temporary persistence"
    elif args.domain:
        errors = validate_domain()
        label = "W04/W05 state + core domain foundation"
    elif args.focus:
        errors = validate_focus()
        label = "W06 Today + durable focus"
    elif args.content:
        errors = validate_plan_content()
        label = "W06/W07 Today, Focus + Plan Content"
    elif args.schedule:
        errors = validate_schedule()
        label = "W08 schedule intelligence + adaptive preview"
    else:
        errors = validate_practice()
        label = "W08/W09 schedule intelligence + practice"

    if errors:
        print("StudyFlow web validation FAILED:", file=sys.stderr)
        for error in errors:
            print(f"- {error}", file=sys.stderr)
        return 1
    print(f"StudyFlow web validation passed: {label}.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
