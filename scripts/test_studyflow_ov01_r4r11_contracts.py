#!/usr/bin/env python3
"""OV01-R4R11: pinned and source-checked hosted deep pytest runtime.

Source-only, with deliberately broken dependency/workflow negatives. No Gradle.
"""
from pathlib import Path
import sys
ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
import studyflow_ci_gradle_profile as profile


def must_fail(ci: str, req: str, reason: str) -> None:
    try:
        profile.validate_deep_web_pytest_hosted_prereqs(ci, req)
    except ValueError:
        return
    raise AssertionError("Broken pytest prerequisite accepted: " + reason)


def main() -> None:
    ci = (ROOT / ".github/workflows/studyflow-ci.yml").read_text()
    req = (ROOT / "web/requirements-deep-pytest.txt").read_text()
    profile.validate_deep_web_pytest_hosted_prereqs(ci, req)
    # The repository-wide source check is separately exercised by the hosted source job.
    expected = profile.DEEP_WEB_PYTEST_PINS
    assert expected[0] == "pytest==9.0.2"
    must_fail(ci, req.replace("pytest==9.0.2", "pytest>=9"), "unbounded pytest version")
    must_fail(ci, req.replace("pluggy==1.6.0", ""), "missing transitive dependency")
    must_fail(ci, req.replace("Pygments==2.20.0", "Pygments"), "unbounded transitive version")
    must_fail(ci, req + "pytest==9.0.2\n", "duplicate wheels")
    must_fail(ci.replace("matrix.profile == 'android-contracts' }}\n        shell: bash",
                          "matrix.profile == 'parity-contracts' }}\n        shell: bash"),
              req, "wrong deep profile")
    must_fail(ci.replace("--no-deps --only-binary=:all:", "--only-binary=:all:"),
              req, "implicit unpinned transitive resolver")
    must_fail(ci.replace("python3 -m pytest --version", "true"),
              req, "missing hosted import probe")
    must_fail(ci.replace("-r web/requirements-deep-pytest.txt", "-r web/requirements-test.txt"),
              req, "image tests only")
    install = "      - name: Install pinned pytest for deep Android Web regression\n"
    step_start = ci.index(install)
    next_step = ci.index("\n      - ", step_start + len(install))
    step = ci[step_start:next_step+1]
    moved = ci[:step_start] + ci[next_step+1:]
    marker = "      - name: Run canonical StudyFlow Devtool Gradle target profile\n"
    assert marker in moved
    must_fail(moved.replace(marker, marker + step, 1), req, "late pytest setup")
    # Regressions still require pytest, not unittest discovery, which ignores
    # module-level tests such as test_sflp16_17_control_input.py.
    gradle = (ROOT / "build.gradle.kts").read_text()
    assert 'commandLine("python3", "-m", "pytest", "tests", "-q")' in gradle
    web = (ROOT / "web/tests/test_sflp16_17_control_input.py").read_text()
    assert "def test_web_runtime_exports_control_input_runtime" in web
    assert "python3 scripts/test_studyflow_ov01_r4r11_contracts.py" in ci
    print("STUDYFLOW_OV01_R4R11_HOSTED_PYTEST: PASS (9 mutation negatives, no local Gradle)")


if __name__ == "__main__":
    main()
