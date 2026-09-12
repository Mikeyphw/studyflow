#!/usr/bin/env python3
from __future__ import annotations

import json
import py_compile
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
REQUIRED = [
    "scripts/export_release_evidence_report.py",
    "scripts/verify_rel02_release_evidence_triage.py",
    "docs/validation/REL02_RELEASE_EVIDENCE_TRIAGE.md",
    "REL02_RELEASE_EVIDENCE_TRIAGE.md",
]
TOKENS = [
    "REL02_RELEASE_EVIDENCE_EXPORT",
    "release-evidence-summary.json",
    "release-failure-triage.json",
    "release-evidence-report.md",
    "studyflow-release-evidence-bundle.zip",
    "PENDING_TARGET_EXECUTION",
    "BLOCKED",
    "releaseReady",
    "fail_when_blocked",
    "<redacted>",
]


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def text(path: str) -> str:
    value = (ROOT / path).read_text(encoding="utf-8")
    require(value.endswith("\n"), f"{path} must end with a newline")
    return value


def main() -> None:
    for path in REQUIRED:
        require((ROOT / path).is_file(), f"missing REL02 file: {path}")

    exporter = text("scripts/export_release_evidence_report.py")
    docs = text("docs/validation/REL02_RELEASE_EVIDENCE_TRIAGE.md")
    note = text("REL02_RELEASE_EVIDENCE_TRIAGE.md")
    gradle = text("build.gradle.kts")
    devtool = text(".devtool.toml")
    readme = text("README.md")
    changelog = text("CHANGELOG.md")
    packaging = text("docs/release/RELEASE_PACKAGING.md")
    matrix = text("docs/validation/STUDYFLOW_FINAL_ACCEPTANCE_MATRIX.md")
    rel01 = text("docs/validation/REL01_TARGET_RELEASE_VALIDATION_RUNNER.md")

    combined = "\n".join([exporter, docs, note, packaging, matrix, rel01])
    for token in TOKENS:
        require(token in combined, f"REL02 token missing: {token}")

    require('tasks.register<Exec>("verifyRel02ReleaseEvidenceTriage")' in gradle,
            "Gradle REL02 verifier task missing")
    require('commandLine("python3", "scripts/verify_rel02_release_evidence_triage.py")' in gradle,
            "Gradle REL02 verifier command missing")
    require('"verifyRel02ReleaseEvidenceTriage"' in gradle,
            "checkAll must include REL02 verifier")
    require('"verifyRel07Rel08FinalReleaseOpsSeal"' in devtool,
            "Devtool test lane must include the terminal REL07/08 release-ops seal")
    require('dependsOn("verifyRel01TargetReleaseValidationRunner")' in gradle,
            "REL02 verifier must preserve REL01 as predecessor")
    require("REL02 release evidence exporter" in readme,
            "README must expose REL02 status")
    require("# REL02" in changelog,
            "CHANGELOG must include REL02 entry")
    require("releaseReady\": True" not in exporter and "releaseReady=True" not in exporter,
            "exporter must not synthesize releaseReady=true")
    require("STUDYFLOW_RELEASE_STORE_PASSWORD" not in exporter,
            "exporter must not enumerate signing secrets directly")

    py_compile.compile(str(ROOT / "scripts/export_release_evidence_report.py"), doraise=True)

    with tempfile.TemporaryDirectory() as tmp:
        report = Path(tmp) / "reports"
        report.mkdir()
        (report / "target-release-validation-plan.json").write_text(
            json.dumps({"status": "PENDING_TARGET_EXECUTION", "releaseReady": False}) + "\n",
            encoding="utf-8",
        )
        (report / "release-artifact-identity.json").write_text(
            json.dumps({"verified": True, "store_password": "do-not-leak"}) + "\n",
            encoding="utf-8",
        )
        out = Path(tmp) / "export"
        completed = subprocess.run(
            [
                sys.executable,
                str(ROOT / "scripts/export_release_evidence_report.py"),
                "--mode",
                "all",
                "--report-dir",
                str(report),
                "--export-dir",
                str(out),
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
            check=False,
        )
        require(completed.returncode == 0, completed.stderr or completed.stdout)
        summary = json.loads((out / "release-evidence-summary.json").read_text(encoding="utf-8"))
        triage = json.loads((out / "release-failure-triage.json").read_text(encoding="utf-8"))
        require(summary["releaseReady"] is False, "pending sample must not become release-ready")
        require(summary["status"] == "PENDING_TARGET_EXECUTION", "pending sample must stay pending")
        require(triage["status"] == "BLOCKED", "missing evidence must create blocked triage")
        require((out / "release-evidence-report.md").is_file(), "markdown report missing")
        require((out / "studyflow-release-evidence-bundle.zip").is_file(), "bundle zip missing")
        require("<redacted>" in (out / "release-evidence-summary.json").read_text(encoding="utf-8"),
                "secret-looking keys must be redacted")

    print("REL02_RELEASE_EVIDENCE_TRIAGE: PASS")


if __name__ == "__main__":
    main()
