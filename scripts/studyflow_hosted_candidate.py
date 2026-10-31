#!/usr/bin/env python3
"""REL-HOSTED-01: unsigned GitHub artifact staging and private offline signing.

This program NEVER runs Gradle, installs APKs, uploads credentials or writes REL-FINAL
PASS reports. The source SHA and artifact hashes must match before local signing.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path

SCHEMA = "studyflow.hosted_candidate.v1"
MODULES = {
    "app": ("com.mikeyphw.studyflow", "37", "1.0.0-rc1"),
    "app-tv": ("com.mikeyphw.studyflow.tv", "31", "1.0.0-tv31"),
}
SHA_RE = re.compile(r"^[0-9a-f]{40}$")
HASH_RE = re.compile(r"^[0-9a-f]{64}$")
ROOT = Path(__file__).resolve().parents[1]


class UnsafeCandidate(ValueError):
    pass


def require(condition: bool, message: str) -> None:
    if not condition:
        raise UnsafeCandidate(message)


def digest(path: Path) -> str:
    sha = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            sha.update(block)
    return sha.hexdigest()


def invoke(args: list[str], *, env: dict[str, str] | None = None, check: bool = True) -> subprocess.CompletedProcess[str]:
    return subprocess.run(args, text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE, env=env, check=check)


def single_file(directory: Path, extension: str) -> Path:
    require(directory.is_dir() and not directory.is_symlink(), f"missing/unsafe output directory: {directory}")
    found = sorted(p for p in directory.iterdir() if p.suffix == extension)
    require(len(found) == 1, f"expected one {extension} package in {directory}; got {len(found)}")
    require(found[0].is_file() and not found[0].is_symlink() and found[0].stat().st_size > 0,
            f"missing/unsafe package: {found[0]}")
    return found[0]


def verify_zip(path: Path, *, aab: bool) -> None:
    require(path.is_file() and not path.is_symlink() and path.stat().st_size > 1024,
            f"missing, symlinked or empty candidate: {path}")
    with zipfile.ZipFile(path) as archive:
        items = archive.infolist()
        require(items and archive.testzip() is None, f"corrupted ZIP: {path}")
        names = [i.filename for i in items]
        require(len(set(names)) == len(names), f"duplicate ZIP entries: {path}")
        require(all(not x.startswith("/") and ".." not in Path(x).parts for x in names),
                f"unsafe ZIP entry: {path}")
        if aab:
            require("base/manifest/AndroidManifest.xml" in names, "AAB base manifest absent")
            require(not any(re.match(r"META-INF/[^/]+\.(SF|RSA|DSA|EC)$", x, flags=re.I) for x in names),
                    "AAB already contains JAR signature blocks")
        else:
            require("AndroidManifest.xml" in names, "APK manifest absent")


def ensure_unsigned(apk: Path, aab: Path, apksigner: str) -> None:
    verify_zip(apk, aab=False)
    verify_zip(aab, aab=True)
    result = invoke([apksigner, "verify", str(apk)], check=False)
    require(result.returncode != 0, "APK is signed; hosted output must be unsigned")


def inspect_apk(apk: Path, module: str, apkanalyzer: str) -> None:
    package, code, version = MODULES[module]
    for key, expected in (("application-id", package), ("version-code", code), ("version-name", version)):
        result = invoke([apkanalyzer, "manifest", key, str(apk)])
        require(result.stdout.strip().strip("'\"") == expected,
                f"APK {key} mismatch: expected {expected}; got {result.stdout.strip()!r}")
    is_debuggable = invoke([apkanalyzer, "manifest", "debuggable", str(apk)])
    require(is_debuggable.stdout.strip().lower() == "false", "hosted candidate must not be debuggable")


def load_manifest(folder: Path, *, expected_sha: str, expected_run_id: str) -> tuple[dict, Path, Path]:
    require(SHA_RE.fullmatch(expected_sha) is not None, "expected SHA must be 40 lowercase hex characters")
    require(re.fullmatch(r"[1-9][0-9]*", expected_run_id) is not None, "expected run ID must be numeric")
    path = folder / "hosted-candidate-manifest.json"
    require(folder.is_dir() and not folder.is_symlink() and path.is_file() and not path.is_symlink(),
            "missing/unsafe hosted manifest")
    content = json.loads(path.read_text(encoding="utf-8"))
    require(isinstance(content, dict) and content.get("schema") == SCHEMA, "wrong candidate manifest schema")
    module = content.get("module")
    require(module in MODULES, "unknown candidate module")
    require(content.get("sourceSha") == expected_sha and content.get("githubRunId") == expected_run_id,
            "artifact source SHA or GitHub run ID mismatch")
    require(content.get("releaseReady") is False and content.get("signing") == "unsigned",
            "candidate manifest claims release/signing authority")
    require(content.get("applicationId") == MODULES[module][0] and
            str(content.get("versionCode")) == MODULES[module][1] and
            content.get("versionName") == MODULES[module][2], "candidate identity mismatch")
    paths = []
    for label, name in (("apk", "unsigned.apk"), ("aab", "unsigned.aab")):
        record = content.get("artifacts", {}).get(label)
        require(isinstance(record, dict) and record.get("file") == name, f"unsafe {label} artifact name")
        checksum = record.get("sha256")
        require(isinstance(checksum, str) and HASH_RE.fullmatch(checksum) is not None,
                f"malformed {label} SHA-256")
        payload = folder / name
        verify_zip(payload, aab=(label == "aab"))
        require(digest(payload) == checksum and record.get("bytes") == payload.stat().st_size,
                f"{label} artifact hash/size mismatch")
        paths.append(payload)
    return content, paths[0], paths[1]


def stage(ns: argparse.Namespace) -> None:
    require(os.getenv("GITHUB_ACTIONS") == "true" and os.getenv("STUDYFLOW_HOSTED_UNSIGNED_CANDIDATE") == "true",
            "stage is restricted to GitHub Actions hosted-candidate job")
    require(SHA_RE.fullmatch(ns.expected_sha) is not None, "invalid expected source SHA")
    require(os.getenv("GITHUB_SHA") == ns.expected_sha and os.getenv("GITHUB_RUN_ID") == ns.run_id,
            "GitHub run/source identity mismatch")
    require(invoke(["git", "rev-parse", "HEAD"]).stdout.strip() == ns.expected_sha,
            "checked-out source differs from reviewed SHA")
    require(not any(os.getenv(x) for x in ("STUDYFLOW_RELEASE_STORE_FILE", "STUDYFLOW_RELEASE_STORE_PASSWORD",
                                         "STUDYFLOW_RELEASE_KEY_ALIAS", "STUDYFLOW_RELEASE_KEY_PASSWORD")),
            "signing variables are forbidden in hosted candidate build")
    require(not (Path.home() / ".config/studyflow/android-signing.properties").exists(),
            "personal signing configuration unexpectedly present on runner")
    module = ns.module
    apk = single_file(ROOT / module / "build/outputs/apk/hostedCandidate", ".apk")
    aab = single_file(ROOT / module / "build/outputs/bundle/hostedCandidate", ".aab")
    ensure_unsigned(apk, aab, ns.apksigner)
    inspect_apk(apk, module, ns.apkanalyzer)
    target = Path(ns.out_dir).resolve()
    require(not target.exists(), "hosted staging destination must be new/empty")
    target.mkdir(parents=True, mode=0o700)
    staged = []
    for src, filename in ((apk, "unsigned.apk"), (aab, "unsigned.aab")):
        dest = target / filename
        shutil.copyfile(src, dest)
        staged.append({"file": filename, "bytes": dest.stat().st_size, "sha256": digest(dest)})
    package, code, version = MODULES[module]
    receipt = {
        "schema": SCHEMA, "module": module, "sourceSha": ns.expected_sha,
        "githubRunId": ns.run_id, "applicationId": package,
        "versionCode": int(code), "versionName": version, "buildType": "hostedCandidate",
        "signing": "unsigned", "releaseReady": False,
        "evidenceClass": "hosted-build-only; no connected, restore, device or release acceptance",
        "artifacts": {"apk": staged[0], "aab": staged[1]},
    }
    (target / "hosted-candidate-manifest.json").write_text(json.dumps(receipt, indent=2, sort_keys=True) + "\n")
    print(f"HOSTED_CANDIDATE_STAGE: PASS {module} {ns.expected_sha} GitHub-run {ns.run_id}")


def java_properties(path: Path) -> dict[str, str]:
    require(path.is_file() and not path.is_symlink(), "missing/unsafe private signing properties")
    require(not path.resolve().is_relative_to(ROOT.resolve()), "signing properties must be outside repository")
    raw = path.read_bytes().decode("iso-8859-1")

    def decode(value: str) -> str:
        out, i = [], 0
        while i < len(value):
            if value[i] != "\\":
                out.append(value[i]); i += 1; continue
            i += 1
            require(i < len(value), "trailing Java-properties escape")
            c = value[i]; i += 1
            if c == "u":
                require(re.fullmatch(r"[0-9a-fA-F]{4}", value[i:i + 4]) is not None,
                        "invalid Unicode property escape")
                out.append(chr(int(value[i:i + 4], 16))); i += 4
            else:
                out.append({"n": "\n", "r": "\r", "t": "\t", "f": "\f"}.get(c, c))
        return "".join(out).encode("utf-16", "surrogatepass").decode("utf-16")

    result = {}
    for line in raw.splitlines():
        if not line or line.lstrip().startswith(("#", "!")): continue
        escaped = False
        for i, c in enumerate(line):
            if escaped: escaped = False; continue
            if c == "\\": escaped = True; continue
            if c in "=:":
                result[decode(line[:i])] = decode(line[i + 1:]); break
    for k in ("storeFile", "storePassword", "keyAlias", "keyPassword"):
        require(result.get(k) and "\x00" not in result[k], f"invalid/missing private signing field: {k}")
    raw_store = Path(result["storeFile"]).expanduser()
    require(not raw_store.is_symlink(), "keystore must not be symlinked")
    store = raw_store.resolve()
    require(store.is_file() and not store.is_relative_to(ROOT.resolve()),
            "keystore absent or stored inside repository")
    result["storeFile"] = str(store)
    return result


def sign(ns: argparse.Namespace) -> None:
    folder, out = Path(ns.bundle_dir).resolve(), Path(ns.out_dir).resolve()
    receipt, apk, aab = load_manifest(folder, expected_sha=ns.expected_sha, expected_run_id=ns.expected_run_id)
    module = receipt["module"]
    require(not out.exists() and not out.is_relative_to(ROOT.resolve()),
            "signed output must be a new directory outside repository")
    ensure_unsigned(apk, aab, ns.apksigner)
    inspect_apk(apk, module, ns.apkanalyzer)
    props = java_properties(Path(ns.signing_properties).expanduser())
    env = os.environ.copy()
    env["STUDYFLOW_PRIVATE_STORE_PASS"] = props["storePassword"]
    env["STUDYFLOW_PRIVATE_KEY_PASS"] = props["keyPassword"]
    out.mkdir(mode=0o700, parents=True)
    try:
        with tempfile.TemporaryDirectory(prefix="studyflow-aligned-") as temp:
            aligned = Path(temp) / "aligned.apk"
            invoke([ns.zipalign, "-f", "-p", "4", str(apk), str(aligned)])
            signed_apk = out / f"{module}-signed.apk"
            invoke([ns.apksigner, "sign", "--ks", props["storeFile"], "--ks-key-alias", props["keyAlias"],
                    "--ks-pass", "env:STUDYFLOW_PRIVATE_STORE_PASS", "--key-pass", "env:STUDYFLOW_PRIVATE_KEY_PASS",
                    "--out", str(signed_apk), str(aligned)], env=env)
            signer_report = invoke([ns.apksigner, "verify", "--print-certs", str(signed_apk)]).stdout
            require("Signer #1 certificate SHA-256 digest:" in signer_report,
                    "signed APK certificate fingerprint missing")
            fingerprint = next(x.split(":", 1)[1].strip() for x in signer_report.splitlines()
                               if "Signer #1 certificate SHA-256 digest:" in x)
            signed_aab = out / f"{module}-signed.aab"
            invoke([ns.jarsigner, "-keystore", props["storeFile"], "-storetype", "PKCS12",
                    "-storepass:env", "STUDYFLOW_PRIVATE_STORE_PASS", "-keypass:env", "STUDYFLOW_PRIVATE_KEY_PASS",
                    "-sigalg", "SHA256withRSA", "-digestalg", "SHA-256", "-signedjar", str(signed_aab),
                    str(aab), props["keyAlias"]], env=env)
            check = invoke([ns.jarsigner, "-verify", "-verbose", str(signed_aab)], check=True)
            require("jar verified" in (check.stdout + check.stderr).lower(), "signed AAB not verified")
            verify_zip(signed_apk, aab=False)
            inspect_apk(signed_apk, module, ns.apkanalyzer)
            with zipfile.ZipFile(signed_aab) as signed_zip:
                require(signed_zip.testzip() is None and "base/manifest/AndroidManifest.xml" in signed_zip.namelist(),
                        "signed AAB ZIP is corrupt or missing its manifest")
            # Ensure all unsigned original AAB payload members are covered by JAR signatures.
            with zipfile.ZipFile(aab) as original, zipfile.ZipFile(signed_aab) as signed:
                original_names = {x.filename for x in original.infolist() if not x.is_dir()}
                signed_names = set(signed.namelist())
                require(original_names <= signed_names, "signed AAB lost source entries")
                require(any(x.startswith("META-INF/") and x.endswith(".SF") for x in signed_names),
                        "AAB JAR signature file missing")
            signed_receipt = {
                "schema": "studyflow.hosted_candidate_signed.v1", "status": "SIGNED_STAGE_ONLY",
                "sourceSha": ns.expected_sha, "githubRunId": ns.expected_run_id, "module": module,
                "applicationId": receipt["applicationId"], "versionCode": receipt["versionCode"],
                "versionName": receipt["versionName"], "certificateSha256": fingerprint,
                "sourceArtifacts": receipt["artifacts"],
                "signedArtifacts": {
                    "apk": {"file": signed_apk.name, "sha256": digest(signed_apk), "bytes": signed_apk.stat().st_size},
                    "aab": {"file": signed_aab.name, "sha256": digest(signed_aab), "bytes": signed_aab.stat().st_size},
                },
                "releaseReady": False, "releaseOpsReady": False,
                "connectedTests": "NOT_RUN", "restoreRestart": "NOT_RUN", "physicalDevice": "NOT_VERIFIED",
            }
            (out / "hosted-candidate-signing-receipt.json").write_text(
                json.dumps(signed_receipt, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    except Exception:
        shutil.rmtree(out)
        raise
    finally:
        env.pop("STUDYFLOW_PRIVATE_STORE_PASS", None)
        env.pop("STUDYFLOW_PRIVATE_KEY_PASS", None)
    report_dir = Path(ns.report_dir).expanduser().resolve()
    report_dir.mkdir(parents=True, exist_ok=True)
    report_path = report_dir / f"hosted-{module}-signed-stage.json"
    require(not report_path.is_symlink(), "evidence destination must not be symlinked")
    report_path.write_text(json.dumps(signed_receipt, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(f"HOSTED_CANDIDATE_SIGNING: SIGNED_STAGE_ONLY {module} GitHub-run {ns.expected_run_id}")
    print(f"Receipt: {out / 'hosted-candidate-signing-receipt.json'}")
    print("REL-FINAL remains PENDING: connected tests, restore/restart, device evidence and operator seal are not asserted")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    subs = parser.add_subparsers(dest="action", required=True)
    st = subs.add_parser("stage")
    st.add_argument("--module", choices=MODULES, required=True)
    st.add_argument("--expected-sha", required=True)
    st.add_argument("--run-id", required=True)
    st.add_argument("--out-dir", required=True)
    st.add_argument("--apksigner", default="apksigner")
    st.add_argument("--apkanalyzer", default="apkanalyzer")
    sg = subs.add_parser("sign")
    sg.add_argument("--bundle-dir", required=True)
    sg.add_argument("--out-dir", required=True)
    sg.add_argument("--expected-sha", required=True)
    sg.add_argument("--expected-run-id", required=True)
    sg.add_argument("--report-dir", default=str(ROOT / "build/reports/studyflow"))
    sg.add_argument("--signing-properties", default=str(Path.home() / ".config/studyflow/android-signing.properties"))
    sg.add_argument("--apksigner", default="apksigner")
    sg.add_argument("--apkanalyzer", default="apkanalyzer")
    sg.add_argument("--zipalign", default="zipalign")
    sg.add_argument("--jarsigner", default="jarsigner")
    args = parser.parse_args()
    try:
        if args.action == "stage": stage(args)
        else: sign(args)
    except (UnsafeCandidate, subprocess.CalledProcessError, OSError, ValueError, zipfile.BadZipFile) as exc:
        print(f"REL_HOSTED_01_BLOCKED: {type(exc).__name__}: {exc if not isinstance(exc, subprocess.CalledProcessError) else exc.cmd[0] + ' failed'}", file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
