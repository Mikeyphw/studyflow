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
    "scripts/check_release_operator_checklist.py",
    "scripts/build_release_ops_final_seal.py",
    "scripts/verify_rel07_08_final_release_ops_seal.py",
    "REL07_08_FINAL_RELEASE_OPS_SEAL.md",
    "docs/validation/REL07_08_FINAL_RELEASE_OPS_SEAL.md",
    "docs/release/REL07_08_FINAL_RELEASE_OPS_SEAL.md",
]
TOKENS = [
    "REL07_RELEASE_CHECKLIST_GATE",
    "release-checklist-gate.json",
    "release-checklist-gate.md",
    "release-checklist-command-template.sh",
    "operatorChecklistComplete",
    "readyForFinalReleaseOpsSeal",
    "REL08_FINAL_RELEASE_OPS_SEAL",
    "release-ops-final-seal.json",
    "release-ops-final-seal.md",
    "studyflow-release-ops-final-bundle.zip",
    "releaseOpsReady",
    "PENDING_TARGET_EXECUTION",
    "BLOCKED",
    "<redacted>",
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


def write_json(path: Path, value: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def main() -> None:
    for path in REQUIRED:
        require((ROOT / path).is_file(), f"missing REL07/08 file: {path}")

    checklist = text("scripts/check_release_operator_checklist.py")
    final = text("scripts/build_release_ops_final_seal.py")
    verifier_text = text("scripts/verify_rel07_08_final_release_ops_seal.py")
    docs = text("docs/validation/REL07_08_FINAL_RELEASE_OPS_SEAL.md")
    release_docs = text("docs/release/REL07_08_FINAL_RELEASE_OPS_SEAL.md")
    note = text("REL07_08_FINAL_RELEASE_OPS_SEAL.md")
    gradle = text("build.gradle.kts")
    devtool = text(".devtool.toml")
    readme = text("README.md")
    changelog = text("CHANGELOG.md")
    matrix = text("docs/validation/STUDYFLOW_FINAL_ACCEPTANCE_MATRIX.md")
    packaging = text("docs/release/RELEASE_PACKAGING.md")

    combined = "\n".join([checklist, final, verifier_text, docs, release_docs, note, matrix, packaging])
    for token in TOKENS:
        require(token in combined, f"REL07/08 token missing: {token}")

    require('tasks.register<Exec>("verifyRel07Rel08FinalReleaseOpsSeal")' in gradle,
            "Gradle REL07/08 verifier task missing")
    require('commandLine("python3", "scripts/verify_rel07_08_final_release_ops_seal.py")' in gradle,
            "Gradle REL07/08 verifier command missing")
    require('dependsOn("verifyRel05Rel06ReleaseNotesRollback")' in gradle,
            "REL07/08 verifier must depend on REL05/06")
    require('"verifyRel07Rel08FinalReleaseOpsSeal"' in gradle,
            "checkAll must include REL07/08 verifier")
    require('"verifyRel07Rel08FinalReleaseOpsSeal"' in devtool,
            "Devtool test lane must include REL07/08 verifier")
    require("REL07 release checklist gate" in readme, "README must expose REL07")
    require("REL08 final release-ops seal" in readme, "README must expose REL08")
    require("# REL07/REL08" in changelog, "CHANGELOG must include REL07/REL08 entry")
    require("does not upload, publish, install, sign, delete, or roll back" in final,
            "final seal must avoid unsafe release actions")

    for path in [
        "scripts/check_release_operator_checklist.py",
        "scripts/build_release_ops_final_seal.py",
        "scripts/verify_rel07_08_final_release_ops_seal.py",
    ]:
        py_compile.compile(str(ROOT / path), doraise=True)

    with tempfile.TemporaryDirectory() as tmp:
        tmp_path = Path(tmp)
        report_dir = tmp_path / "reports"
        report_dir.mkdir(parents=True)

        pending = run_script([
            str(ROOT / "scripts/check_release_operator_checklist.py"),
            "--report-dir", str(report_dir),
        ])
        require(pending.returncode == 0, pending.stderr or pending.stdout)
        pending_gate = json.loads((report_dir / "release-checklist-gate.json").read_text(encoding="utf-8"))
        require(pending_gate["status"] == "PENDING_TARGET_EXECUTION", "missing evidence must leave REL07 pending")
        require(pending_gate["operatorChecklistComplete"] is False, "empty operator checklist must not pass")
        require((report_dir / "release-checklist-command-template.sh").is_file(), "REL07 command template missing")

        # Provide complete, target-like evidence.
        (report_dir / "release-evidence-export").mkdir(parents=True, exist_ok=True)
        write_json(report_dir / "target-release-validation-summary.json", {"status": "PASS"})
        write_json(report_dir / "release-evidence-export" / "release-evidence-summary.json", {"status": "PASS", "secretToken": "abc"})
        write_json(report_dir / "release-evidence-export" / "release-failure-triage.json", {"status": "PASS"})
        write_json(report_dir / "release-artifact-signature-identity.json", {"status": "PASS"})
        write_json(report_dir / "install-smoke-evidence.json", {"status": "PASS"})
        write_json(report_dir / "release-channel-manifest.json", {"status": "PASS", "channel": "internal"})
        write_json(report_dir / "rollout-readiness.json", {"status": "PASS", "releaseReady": True})
        write_json(report_dir / "release-notes.json", {"status": "PASS"})
        write_json(report_dir / "release-rollback-manifest.json", {"status": "PASS", "rollbackReady": True})
        write_json(report_dir / "operator-release-checklist.json", {"checks": {
            "target validation summary attached": True,
            "release evidence bundle exported": True,
            "artifact identity and signature evidence attached": True,
            "install smoke evidence attached": True,
            "rollout channel manifest reviewed": True,
            "release notes reviewed": True,
            "rollback package reviewed": True,
            "known blockers triaged or explicitly accepted": True,
        }})
        completed = run_script([
            str(ROOT / "scripts/check_release_operator_checklist.py"),
            "--report-dir", str(report_dir),
        ])
        require(completed.returncode == 0, completed.stderr or completed.stdout)
        gate = json.loads((report_dir / "release-checklist-gate.json").read_text(encoding="utf-8"))
        require(gate["status"] == "PASS", "complete evidence and checklist must pass REL07")
        require(gate["readyForFinalReleaseOpsSeal"] is True, "REL07 final-seal flag must be true only on PASS")
        require("<redacted>" in json.dumps(gate), "REL07 must redact credential-looking keys")

        completed = run_script([
            str(ROOT / "scripts/build_release_ops_final_seal.py"),
            "--report-dir", str(report_dir),
            "--channel", "internal",
        ])
        require(completed.returncode == 0, completed.stderr or completed.stdout)
        seal = json.loads((report_dir / "release-ops-final-seal.json").read_text(encoding="utf-8"))
        require(seal["status"] == "PASS", "complete REL01-REL07 evidence must pass REL08")
        require(seal["releaseOpsReady"] is True, "REL08 releaseOpsReady should only be true on PASS")
        require((report_dir / "release-ops-final-seal.md").is_file(), "REL08 markdown missing")
        bundle = report_dir / "studyflow-release-ops-final-bundle.zip"
        require(bundle.is_file(), "REL08 final bundle missing")
        with zipfile.ZipFile(bundle) as archive:
            names = set(archive.namelist())
        require("release-ops-final-seal.json" in names, "REL08 bundle missing final seal json")
        require("release-checklist-gate.json" in names, "REL08 bundle missing REL07 gate")

        # Break a critical input and verify fail-closed behavior.
        write_json(report_dir / "install-smoke-evidence.json", {"status": "BLOCKED", "reason": "device unavailable"})
        completed = run_script([
            str(ROOT / "scripts/check_release_operator_checklist.py"),
            "--report-dir", str(report_dir),
        ])
        require(completed.returncode == 1, "blocked smoke evidence should fail REL07 script")
        gate = json.loads((report_dir / "release-checklist-gate.json").read_text(encoding="utf-8"))
        require(gate["status"] == "BLOCKED", "blocked evidence must block REL07")

    print("REL07_08_FINAL_RELEASE_OPS_SEAL: PASS")


if __name__ == "__main__":
    main()
