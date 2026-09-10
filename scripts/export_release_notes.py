#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import re
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_REPORT_DIR = ROOT / "build/reports/studyflow"
STATUS_PASS = "PASS"
STATUS_BLOCKED = "BLOCKED"
STATUS_PENDING = "PENDING_TARGET_EXECUTION"
SECRET_WORDS = {
    "password",
    "passwd",
    "secret",
    "token",
    "credential",
    "keystore",
    "storepass",
    "keypass",
    "private_key",
    "access_key",
}
PUBLIC_HINTS = ("user", "release", "fixed", "added", "improved", "support", "sync", "tv", "web", "desktop", "android")
INTERNAL_HINTS = ("devtool", "verifier", "validation", "artifact", "triage", "manifest", "hash", "signature")


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat()


def redact(value: Any, key_hint: str = "") -> Any:
    lowered = key_hint.lower()
    if any(word in lowered for word in SECRET_WORDS):
        return "<redacted>"
    if isinstance(value, dict):
        return {str(k): redact(v, str(k)) for k, v in value.items()}
    if isinstance(value, list):
        return [redact(v, key_hint) for v in value]
    if isinstance(value, str):
        lowered_value = value.lower()
        if any(word in lowered_value for word in ("storepass=", "keypass=", "password=", "token=")):
            return "<redacted>"
    return value


def write_json(path: Path, value: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(redact(value), indent=2, sort_keys=True) + "\n", encoding="utf-8")


def normalize_bullet(line: str) -> str:
    value = line.strip()
    value = re.sub(r"^[-*]\s+", "", value)
    value = re.sub(r"`([^`]+)`", r"\1", value)
    value = re.sub(r"\s+", " ", value)
    return value.strip()


def extract_changelog_section(changelog: str, since_marker: str | None, max_lines: int) -> list[str]:
    lines = changelog.splitlines()
    selected: list[str] = []
    active = since_marker is None
    headings_seen = 0
    for line in lines:
        if since_marker and since_marker in line:
            active = True
            continue
        if not active:
            continue
        if line.startswith("#"):
            headings_seen += 1
            if since_marker is not None and headings_seen > 1:
                break
        if len(selected) >= max_lines:
            break
        if line.strip():
            selected.append(line.rstrip())
    return selected


def split_public_internal(bullets: list[str]) -> tuple[list[str], list[str]]:
    public: list[str] = []
    internal: list[str] = []
    seen: set[str] = set()
    for raw in bullets:
        bullet = normalize_bullet(raw)
        if not bullet or bullet.startswith("#"):
            continue
        key = bullet.lower()
        if key in seen:
            continue
        seen.add(key)
        lowered = bullet.lower()
        if any(hint in lowered for hint in INTERNAL_HINTS) and not any(hint in lowered for hint in PUBLIC_HINTS):
            internal.append(bullet)
        elif lowered.startswith(("rel", "sflp", "tvov", "cpov")):
            internal.append(bullet)
        else:
            public.append(bullet)
    return public[:24], internal[:24]


def make_store_draft(public_notes: list[str], limit: int) -> str:
    if not public_notes:
        return "StudyFlow release notes are pending target evidence review."
    out: list[str] = []
    current = ""
    for note in public_notes:
        candidate = f"- {note}"
        next_value = (current + "\n" + candidate).strip()
        if len(next_value) > limit:
            continue
        current = next_value
        out.append(candidate)
    if not out:
        return ("- " + public_notes[0])[:limit]
    return "\n".join(out)


def status_for(changelog_path: Path, public_notes: list[str], internal_notes: list[str]) -> str:
    if not changelog_path.is_file():
        return STATUS_BLOCKED
    if not public_notes and not internal_notes:
        return STATUS_PENDING
    return STATUS_PASS


def build_release_notes(repo_root: Path, report_dir: Path, channel: str, version_name: str | None, version_code: str | None, since_marker: str | None, store_limit: int) -> dict[str, Any]:
    changelog_path = repo_root / "CHANGELOG.md"
    readme_path = repo_root / "README.md"
    changelog = changelog_path.read_text(encoding="utf-8") if changelog_path.is_file() else ""
    selected_lines = extract_changelog_section(changelog, since_marker, 140)
    public_notes, internal_notes = split_public_internal(selected_lines)
    status = status_for(changelog_path, public_notes, internal_notes)
    store_draft = make_store_draft(public_notes, store_limit)
    summary = {
        "schema": "studyflow.rel05.release_notes.v1",
        "marker": "REL05_RELEASE_NOTES_EXPORTER",
        "status": status,
        "releaseReady": False,
        "generatedAt": utc_now(),
        "channel": channel,
        "versionName": version_name,
        "versionCode": version_code,
        "source": {
            "changelog": str(changelog_path),
            "readmePresent": readme_path.is_file(),
            "sinceMarker": since_marker,
        },
        "publicNotes": public_notes,
        "internalNotes": internal_notes,
        "storeDraft": store_draft,
        "outputs": {
            "json": str(report_dir / "release-notes.json"),
            "markdown": str(report_dir / "release-notes.md"),
            "storeDraft": str(report_dir / "release-notes-play-store-draft.txt"),
        },
        "notes": [
            "releaseReady remains false because this exporter summarizes release notes only.",
            "Final readiness is still owned by target evidence and rollout gates.",
        ],
    }
    return redact(summary)


def write_markdown(report_dir: Path, report: dict[str, Any]) -> None:
    lines: list[str] = []
    lines.append("# StudyFlow release notes")
    lines.append("")
    lines.append(f"Status: `{report['status']}`")
    lines.append(f"Channel: `{report['channel']}`")
    if report.get("versionName"):
        lines.append(f"Version name: `{report['versionName']}`")
    if report.get("versionCode"):
        lines.append(f"Version code: `{report['versionCode']}`")
    lines.append("")
    lines.append("## Public notes")
    for note in report.get("publicNotes", []):
        lines.append(f"- {note}")
    if not report.get("publicNotes"):
        lines.append("- Pending release-note classification.")
    lines.append("")
    lines.append("## Internal notes")
    for note in report.get("internalNotes", []):
        lines.append(f"- {note}")
    if not report.get("internalNotes"):
        lines.append("- No internal release notes were extracted.")
    lines.append("")
    lines.append("## Store draft")
    lines.append("")
    lines.append("```text")
    lines.append(str(report.get("storeDraft", "")))
    lines.append("```")
    lines.append("")
    lines.append("Release readiness is not inferred from this document.")
    (report_dir / "release-notes.md").write_text("\n".join(lines).rstrip() + "\n", encoding="utf-8")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Export StudyFlow release notes from changelog evidence.")
    parser.add_argument("--repo-root", default=str(ROOT))
    parser.add_argument("--report-dir", default=str(DEFAULT_REPORT_DIR))
    parser.add_argument("--channel", default="internal", choices=["internal", "beta", "production"])
    parser.add_argument("--version-name")
    parser.add_argument("--version-code")
    parser.add_argument("--since-marker")
    parser.add_argument("--store-limit", type=int, default=500)
    args = parser.parse_args(argv)

    repo_root = Path(args.repo_root).resolve()
    report_dir = Path(args.report_dir).resolve()
    report_dir.mkdir(parents=True, exist_ok=True)
    report = build_release_notes(
        repo_root=repo_root,
        report_dir=report_dir,
        channel=args.channel,
        version_name=args.version_name,
        version_code=args.version_code,
        since_marker=args.since_marker,
        store_limit=args.store_limit,
    )
    write_json(report_dir / "release-notes.json", report)
    write_markdown(report_dir, report)
    (report_dir / "release-notes-play-store-draft.txt").write_text(str(report["storeDraft"]).rstrip() + "\n", encoding="utf-8")
    print(f"REL05_RELEASE_NOTES_EXPORTER: {report['status']}")
    return 0 if report["status"] in {STATUS_PASS, STATUS_PENDING} else 1


if __name__ == "__main__":
    raise SystemExit(main())
