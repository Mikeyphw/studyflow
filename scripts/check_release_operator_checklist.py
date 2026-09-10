#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import os
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_REPORT_DIR = ROOT / "build/reports/studyflow"
STATUS_PASS = "PASS"
STATUS_BLOCKED = "BLOCKED"
STATUS_PENDING = "PENDING_TARGET_EXECUTION"
SECRET_WORDS = {
    "password", "passwd", "secret", "token", "credential", "keystore",
    "storepass", "keypass", "private_key", "access_key", "refresh_token",
}
REQUIRED_EVIDENCE = {
    "rel01TargetValidation": "target-release-validation-summary.json",
    "rel02EvidenceSummary": "release-evidence-export/release-evidence-summary.json",
    "rel03ArtifactIdentity": "release-artifact-signature-identity.json",
    "rel03InstallSmoke": "install-smoke-evidence.json",
    "rel04RolloutReadiness": "rollout-readiness.json",
    "rel05ReleaseNotes": "release-notes.json",
    "rel06RollbackManifest": "release-rollback-manifest.json",
}
DEFAULT_CHECKS = [
    "target validation summary attached",
    "release evidence bundle exported",
    "artifact identity and signature evidence attached",
    "install smoke evidence attached",
    "rollout channel manifest reviewed",
    "release notes reviewed",
    "rollback package reviewed",
    "known blockers triaged or explicitly accepted",
]


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
        if any(word in lowered_value for word in ("storepass=", "keypass=", "password=", "token=", "secret=")):
            return "<redacted>"
    return value


def load_json(path: Path) -> dict[str, Any] | None:
    if not path.is_file():
        return None
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError:
        return {"status": STATUS_BLOCKED, "reason": f"invalid JSON: {path}"}


def status_of(value: dict[str, Any] | None) -> str:
    if value is None:
        return STATUS_PENDING
    raw = str(value.get("status") or STATUS_PENDING).upper()
    if raw in {"PASS", "PASSED", "OK"}:
        return STATUS_PASS
    if raw in {"FAIL", "FAILED", "ERROR", "BLOCKED"}:
        return STATUS_BLOCKED
    return STATUS_PENDING


def derive_status(statuses: list[str], checklist_complete: bool) -> str:
    if any(status == STATUS_BLOCKED for status in statuses):
        return STATUS_BLOCKED
    if not checklist_complete:
        return STATUS_PENDING
    if any(status == STATUS_PENDING for status in statuses):
        return STATUS_PENDING
    return STATUS_PASS


def load_operator_checks(path: Path | None) -> dict[str, bool]:
    if path is None or not path.is_file():
        return {name: False for name in DEFAULT_CHECKS}
    data = load_json(path)
    if not isinstance(data, dict):
        return {name: False for name in DEFAULT_CHECKS}
    raw = data.get("checks", data)
    if isinstance(raw, dict):
        return {str(k): bool(v) for k, v in raw.items()}
    if isinstance(raw, list):
        out: dict[str, bool] = {}
        for item in raw:
            if isinstance(item, dict):
                name = str(item.get("name") or item.get("id") or "unnamed check")
                out[name] = bool(item.get("complete") or item.get("checked") or item.get("passed"))
            else:
                out[str(item)] = True
        return out
    return {name: False for name in DEFAULT_CHECKS}


def write_json(path: Path, value: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(redact(value), indent=2, sort_keys=True) + "\n", encoding="utf-8")


def command_template(report_dir: Path) -> str:
    return f"""#!/usr/bin/env bash
set -euo pipefail

REPORT_DIR=${{1:-{str(report_dir)!r}}}
CHECKLIST_JSON=${{2:-$REPORT_DIR/operator-release-checklist.json}}

cat > "$CHECKLIST_JSON" <<'JSON'
{{
  "checks": {{
    "target validation summary attached": false,
    "release evidence bundle exported": false,
    "artifact identity and signature evidence attached": false,
    "install smoke evidence attached": false,
    "rollout channel manifest reviewed": false,
    "release notes reviewed": false,
    "rollback package reviewed": false,
    "known blockers triaged or explicitly accepted": false
  }}
}}
JSON

echo "Edit $CHECKLIST_JSON, switch completed items to true, then run:"
echo "python3 scripts/check_release_operator_checklist.py --report-dir '$REPORT_DIR' --checklist-json '$CHECKLIST_JSON'"
"""


def build_gate(report_dir: Path, checklist_path: Path | None) -> dict[str, Any]:
    evidence: dict[str, Any] = {}
    statuses: list[str] = []
    for name, rel in REQUIRED_EVIDENCE.items():
        value = load_json(report_dir / rel)
        status = status_of(value)
        statuses.append(status)
        evidence[name] = value or {"status": STATUS_PENDING, "reason": f"missing {rel}"}
    checks = load_operator_checks(checklist_path)
    checklist_complete = bool(checks) and all(checks.values())
    status = derive_status(statuses, checklist_complete)
    missing_checks = [name for name, complete in checks.items() if not complete]
    gate = {
        "schema": "studyflow.rel07.release_checklist_gate.v1",
        "marker": "REL07_RELEASE_CHECKLIST_GATE",
        "status": status,
        "generatedAt": utc_now(),
        "operatorChecklistComplete": checklist_complete,
        "readyForFinalReleaseOpsSeal": status == STATUS_PASS,
        "evidence": evidence,
        "evidenceStatuses": {name: status_of(value if isinstance(value, dict) else None) for name, value in evidence.items()},
        "operatorChecklist": checks,
        "missingOperatorChecks": missing_checks,
        "outputs": {
            "json": str(report_dir / "release-checklist-gate.json"),
            "markdown": str(report_dir / "release-checklist-gate.md"),
            "commandTemplate": str(report_dir / "release-checklist-command-template.sh"),
        },
        "notes": [
            "REL07 is fail-closed: missing target evidence or unchecked operator items keep the status pending.",
            "This script does not build, sign, install, upload, publish, delete, or roll back artifacts.",
        ],
    }
    return redact(gate)


def write_markdown(report_dir: Path, gate: dict[str, Any]) -> None:
    lines = [
        "# StudyFlow REL07 release checklist gate",
        "",
        f"Status: `{gate['status']}`",
        f"Ready for final release-ops seal: `{gate['readyForFinalReleaseOpsSeal']}`",
        "",
        "## Evidence",
    ]
    for name, value in gate.get("evidenceStatuses", {}).items():
        lines.append(f"- {name}: `{value}`")
    lines.extend(["", "## Operator checklist"])
    for name, complete in gate.get("operatorChecklist", {}).items():
        lines.append(f"- [{'x' if complete else ' '}] {name}")
    if gate.get("missingOperatorChecks"):
        lines.extend(["", "## Missing operator checks"])
        for name in gate["missingOperatorChecks"]:
            lines.append(f"- {name}")
    lines.extend([
        "",
        "REL07 never claims production readiness by itself; it only prepares an auditable final-seal input.",
    ])
    (report_dir / "release-checklist-gate.md").write_text("\n".join(lines).rstrip() + "\n", encoding="utf-8")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Check the StudyFlow release operator checklist against release evidence.")
    parser.add_argument("--report-dir", default=str(DEFAULT_REPORT_DIR))
    parser.add_argument("--checklist-json")
    args = parser.parse_args(argv)
    report_dir = Path(args.report_dir).resolve()
    report_dir.mkdir(parents=True, exist_ok=True)
    checklist_path = Path(args.checklist_json).resolve() if args.checklist_json else report_dir / "operator-release-checklist.json"
    template = command_template(report_dir)
    (report_dir / "release-checklist-command-template.sh").write_text(template, encoding="utf-8")
    os.chmod(report_dir / "release-checklist-command-template.sh", 0o755)
    gate = build_gate(report_dir, checklist_path)
    write_json(report_dir / "release-checklist-gate.json", gate)
    write_markdown(report_dir, gate)
    print(f"REL07_RELEASE_CHECKLIST_GATE: {gate['status']}")
    return 0 if gate["status"] in {STATUS_PASS, STATUS_PENDING} else 1


if __name__ == "__main__":
    raise SystemExit(main())
