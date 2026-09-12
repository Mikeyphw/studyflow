#!/usr/bin/env python3
from __future__ import annotations

import json
import py_compile
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
REQUIRED = [
    "scripts/export_release_notes.py",
    "scripts/build_rollback_package_manifest.py",
    "scripts/verify_rel05_06_release_notes_rollback.py",
    "docs/validation/REL05_06_RELEASE_NOTES_ROLLBACK.md",
    "docs/release/REL05_06_RELEASE_NOTES_ROLLBACK.md",
    "REL05_06_RELEASE_NOTES_ROLLBACK.md",
]
TOKENS = [
    "REL05_RELEASE_NOTES_EXPORTER",
    "release-notes.json",
    "release-notes.md",
    "release-notes-play-store-draft.txt",
    "REL06_ROLLBACK_PACKAGE_MANIFEST",
    "release-rollback-manifest.json",
    "rollback-package-manifest.md",
    "rollback-command-template.sh",
    "studyflow-rollback-package-bundle.zip",
    "PENDING_TARGET_EXECUTION",
    "BLOCKED",
    "<redacted>",
    "rollbackReady",
]


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def text(path: str) -> str:
    value = (ROOT / path).read_text(encoding="utf-8")
    require(value.endswith("\n"), f"{path} must end with a newline")
    return value


def run_script(args: list[str]) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        [sys.executable, *args],
        cwd=ROOT,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=False,
    )


def main() -> None:
    for path in REQUIRED:
        require((ROOT / path).is_file(), f"missing REL05/06 file: {path}")

    release_notes = text("scripts/export_release_notes.py")
    rollback = text("scripts/build_rollback_package_manifest.py")
    docs = text("docs/validation/REL05_06_RELEASE_NOTES_ROLLBACK.md")
    release_docs = text("docs/release/REL05_06_RELEASE_NOTES_ROLLBACK.md")
    note = text("REL05_06_RELEASE_NOTES_ROLLBACK.md")
    gradle = text("build.gradle.kts")
    devtool = text(".devtool.toml")
    readme = text("README.md")
    changelog = text("CHANGELOG.md")
    matrix = text("docs/validation/STUDYFLOW_FINAL_ACCEPTANCE_MATRIX.md")
    packaging = text("docs/release/RELEASE_PACKAGING.md")

    combined = "\n".join([release_notes, rollback, docs, release_docs, note, matrix, packaging])
    for token in TOKENS:
        require(token in combined, f"REL05/06 token missing: {token}")

    require('tasks.register<Exec>("verifyRel05Rel06ReleaseNotesRollback")' in gradle,
            "Gradle REL05/06 verifier task missing")
    require('commandLine("python3", "scripts/verify_rel05_06_release_notes_rollback.py")' in gradle,
            "Gradle REL05/06 verifier command missing")
    require('dependsOn("verifyRel03Rel04ArtifactInstallRollout")' in gradle,
            "REL05/06 verifier must depend on REL03/04")
    require('"verifyRel05Rel06ReleaseNotesRollback"' in gradle,
            "checkAll must include REL05/06 verifier")
    require('"verifyRel07Rel08FinalReleaseOpsSeal"' in devtool,
            "Devtool test lane must include the terminal REL07/08 release-ops seal")
    require("REL05 release notes exporter" in readme, "README must expose REL05")
    require("REL06 rollback package manifest" in readme, "README must expose REL06")
    require("# REL05/REL06" in changelog, "CHANGELOG must include REL05/REL06 entry")
    require("releaseReady\": True" not in release_notes and "releaseReady=True" not in release_notes,
            "release notes exporter must not synthesize releaseReady=true")
    require("rollbackReady\": True" not in rollback and "rollbackReady=True" not in rollback,
            "rollback manifest must not synthesize rollbackReady=true")

    for path in [
        "scripts/export_release_notes.py",
        "scripts/build_rollback_package_manifest.py",
        "scripts/verify_rel05_06_release_notes_rollback.py",
    ]:
        py_compile.compile(str(ROOT / path), doraise=True)

    with tempfile.TemporaryDirectory() as tmp:
        tmp_path = Path(tmp)
        repo = tmp_path / "repo"
        repo.mkdir()
        (repo / "CHANGELOG.md").write_text(
            "# REL05/REL06 test\n\n"
            "- Added release notes for Android, TV, Web and Desktop users.\n"
            "- Internal validation manifest token=should-not-leak.\n",
            encoding="utf-8",
        )
        report_dir = tmp_path / "reports"
        completed = run_script([
            str(ROOT / "scripts/export_release_notes.py"),
            "--repo-root", str(repo),
            "--report-dir", str(report_dir),
            "--channel", "internal",
            "--version-name", "1.0-test",
            "--version-code", "100",
        ])
        require(completed.returncode == 0, completed.stderr or completed.stdout)
        notes = json.loads((report_dir / "release-notes.json").read_text(encoding="utf-8"))
        require(notes["status"] == "PASS", "release notes exporter should pass with a changelog")
        require(notes["releaseReady"] is False, "release notes exporter must not declare release ready")
        require((report_dir / "release-notes.md").is_file(), "release notes markdown missing")
        require((report_dir / "release-notes-play-store-draft.txt").is_file(), "store draft missing")

        export = report_dir / "release-evidence-export"
        export.mkdir(parents=True)
        (report_dir / "target-release-validation-summary.json").write_text(json.dumps({"status": "PASS"}) + "\n", encoding="utf-8")
        (export / "release-evidence-summary.json").write_text(json.dumps({"status": "PASS"}) + "\n", encoding="utf-8")
        (report_dir / "release-artifact-signature-identity.json").write_text(json.dumps({"status": "PENDING_TARGET_EXECUTION", "secretToken": "abc"}) + "\n", encoding="utf-8")
        (report_dir / "rollout-readiness.json").write_text(json.dumps({"status": "PENDING_TARGET_EXECUTION", "releaseReady": False}) + "\n", encoding="utf-8")
        completed = run_script([
            str(ROOT / "scripts/build_rollback_package_manifest.py"),
            "--report-dir", str(report_dir),
            "--previous-version-name", "0.9-test",
            "--previous-version-code", "99",
            "--previous-apk", "/sdcard/Download/studyflow-prev.apk",
        ])
        require(completed.returncode == 0, completed.stderr or completed.stdout)
        rollback_report = json.loads((report_dir / "release-rollback-manifest.json").read_text(encoding="utf-8"))
        require(rollback_report["status"] == "PENDING_TARGET_EXECUTION", "pending target evidence must keep rollback pending")
        require(rollback_report["rollbackReady"] is False, "rollback manifest must not declare rollback ready")
        require((report_dir / "rollback-package-manifest.md").is_file(), "rollback markdown missing")
        require((report_dir / "rollback-command-template.sh").is_file(), "rollback command template missing")
        bundle = report_dir / "studyflow-rollback-package-bundle.zip"
        require(bundle.is_file(), "rollback package bundle missing")
        with zipfile.ZipFile(bundle) as archive:
            names = set(archive.namelist())
        require("release-rollback-manifest.json" in names, "rollback bundle missing manifest")
        require("release-notes.json" in names, "rollback bundle missing release notes")
        require("<redacted>" in json.dumps(rollback_report), "rollback manifest must redact credential-looking keys")

    print("REL05_06_RELEASE_NOTES_ROLLBACK: PASS")


if __name__ == "__main__":
    main()
