#!/usr/bin/env python3
from __future__ import annotations

import json
import py_compile
import subprocess
import sys
import tempfile
import zipfile
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
REQUIRED = [
    "scripts/collect_release_artifact_identity.py",
    "scripts/collect_install_smoke_evidence.py",
    "scripts/build_release_channel_manifest.py",
    "scripts/verify_rel03_04_artifact_install_rollout.py",
    "docs/validation/REL03_04_ARTIFACT_INSTALL_ROLLOUT.md",
    "docs/release/REL03_04_ARTIFACT_INSTALL_ROLLOUT.md",
    "REL03_04_ARTIFACT_INSTALL_ROLLOUT.md",
]
TOKENS = [
    "REL03_ARTIFACT_SIGNATURE_IDENTITY",
    "release-artifact-signature-identity.json",
    "REL03_INSTALL_SMOKE_EVIDENCE",
    "install-smoke-evidence.json",
    "install-smoke-command-template.sh",
    "REL04_RELEASE_CHANNEL_MANIFEST",
    "release-channel-manifest.json",
    "rollout-readiness.json",
    "PENDING_TARGET_EXECUTION",
    "BLOCKED",
    "<redacted>",
    "fail-when-blocked",
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


def make_zip(path: Path, entries: dict[str, str]) -> None:
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as archive:
        for name, content in entries.items():
            archive.writestr(name, content)


def main() -> None:
    for path in REQUIRED:
        require((ROOT / path).is_file(), f"missing REL03/REL04 file: {path}")

    artifact = text("scripts/collect_release_artifact_identity.py")
    install = text("scripts/collect_install_smoke_evidence.py")
    channel = text("scripts/build_release_channel_manifest.py")
    docs = text("docs/validation/REL03_04_ARTIFACT_INSTALL_ROLLOUT.md")
    release_docs = text("docs/release/REL03_04_ARTIFACT_INSTALL_ROLLOUT.md")
    note = text("REL03_04_ARTIFACT_INSTALL_ROLLOUT.md")
    gradle = text("build.gradle.kts")
    devtool = text(".devtool.toml")
    readme = text("README.md")
    changelog = text("CHANGELOG.md")
    matrix = text("docs/validation/STUDYFLOW_FINAL_ACCEPTANCE_MATRIX.md")
    packaging = text("docs/release/RELEASE_PACKAGING.md")

    combined = "\n".join([artifact, install, channel, docs, release_docs, note, matrix, packaging])
    for token in TOKENS:
        require(token in combined, f"REL03/04 token missing: {token}")

    require('tasks.register<Exec>("verifyRel03Rel04ArtifactInstallRollout")' in gradle,
            "Gradle REL03/REL04 verifier task missing")
    require('commandLine("python3", "scripts/verify_rel03_04_artifact_install_rollout.py")' in gradle,
            "Gradle REL03/REL04 verifier command missing")
    require('dependsOn("verifyRel02ReleaseEvidenceTriage")' in gradle,
            "REL03/REL04 verifier must depend on REL02")
    require('"verifyRel03Rel04ArtifactInstallRollout"' in gradle,
            "checkAll must include REL03/REL04 verifier")
    require('"verifyRel03Rel04ArtifactInstallRollout"' in devtool,
            "Devtool test lane must include REL03/REL04 verifier")
    require("REL03 signed artifact identity" in readme, "README must expose REL03")
    require("REL04 rollout manifest" in readme, "README must expose REL04")
    require("# REL03/REL04" in changelog, "CHANGELOG must include REL03/REL04 entry")
    require("releaseReady\": True" not in artifact and "releaseReady=True" not in artifact,
            "artifact collector must not synthesize releaseReady=true")
    require("releaseReady\": True" not in install and "releaseReady=True" not in install,
            "install smoke collector must not synthesize releaseReady=true")

    for path in [
        "scripts/collect_release_artifact_identity.py",
        "scripts/collect_install_smoke_evidence.py",
        "scripts/build_release_channel_manifest.py",
        "scripts/verify_rel03_04_artifact_install_rollout.py",
    ]:
        py_compile.compile(str(ROOT / path), doraise=True)

    with tempfile.TemporaryDirectory() as tmp:
        tmp_path = Path(tmp)
        apk = tmp_path / "sample.apk"
        aab = tmp_path / "sample.aab"
        make_zip(apk, {"AndroidManifest.xml": "binary-manifest-placeholder", "classes.dex": "dex-placeholder"})
        make_zip(aab, {"BundleConfig.pb": "bundle-config", "base/manifest/AndroidManifest.xml": "manifest"})
        report_dir = tmp_path / "reports"

        completed = run_script([
            str(ROOT / "scripts/collect_release_artifact_identity.py"),
            "--apk", str(apk),
            "--aab", str(aab),
            "--report-dir", str(report_dir),
        ])
        require(completed.returncode == 0, completed.stderr or completed.stdout)
        identity = json.loads((report_dir / "release-artifact-signature-identity.json").read_text(encoding="utf-8"))
        require(identity["releaseReady"] is False, "REL03 artifact collector must not declare release ready")
        require(identity["status"] in {"PASS", "PENDING_TARGET_EXECUTION", "BLOCKED"}, "unexpected identity status")

        completed = run_script([
            str(ROOT / "scripts/collect_install_smoke_evidence.py"),
            "--mode", "template",
            "--apk", str(apk),
            "--report-dir", str(report_dir),
        ])
        require(completed.returncode == 0, completed.stderr or completed.stdout)
        install_report = json.loads((report_dir / "install-smoke-evidence.json").read_text(encoding="utf-8"))
        require(install_report["status"] == "PENDING_TARGET_EXECUTION", "template mode must stay pending")
        require((report_dir / "install-smoke-command-template.sh").is_file(), "install template missing")

        export = report_dir / "release-evidence-export"
        export.mkdir(parents=True)
        (report_dir / "target-release-validation-plan.json").write_text(json.dumps({"status": "PASS"}) + "\n", encoding="utf-8")
        (report_dir / "target-release-validation-summary.json").write_text(json.dumps({"status": "PASS"}) + "\n", encoding="utf-8")
        (export / "release-evidence-summary.json").write_text(json.dumps({"status": "PENDING_TARGET_EXECUTION", "releaseReady": False}) + "\n", encoding="utf-8")
        (export / "release-failure-triage.json").write_text(json.dumps({"status": "BLOCKED", "token": "do-not-leak"}) + "\n", encoding="utf-8")
        completed = run_script([
            str(ROOT / "scripts/build_release_channel_manifest.py"),
            "--channel", "production",
            "--report-dir", str(report_dir),
        ])
        require(completed.returncode == 0, completed.stderr or completed.stdout)
        readiness = json.loads((report_dir / "rollout-readiness.json").read_text(encoding="utf-8"))
        manifest = json.loads((report_dir / "release-channel-manifest.json").read_text(encoding="utf-8"))
        require(readiness["releaseReady"] is False, "pending REL02 must block rollout")
        require(readiness["status"] in {"PENDING_TARGET_EXECUTION", "BLOCKED"}, "rollout must be pending or blocked")
        require("<redacted>" in json.dumps(manifest), "channel manifest must redact credential-looking keys")

    print("REL03_04_ARTIFACT_INSTALL_ROLLOUT: PASS")


if __name__ == "__main__":
    main()
