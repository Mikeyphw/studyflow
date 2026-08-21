#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(f"CMOV-04 Termux runtime foundation failed: {message}")


def require_contains(path: str, *needles: str) -> str:
    text = read(path)
    missing = [needle for needle in needles if needle not in text]
    require(not missing, f"{path} missing {missing}")
    return text


def main() -> None:
    runtime = require_contains(
        "core/model/src/main/kotlin/com/mikeyphw/studyflow/core/model/TermuxProgrammingRuntime.kt",
        "enum class TermuxBridgeAction",
        "OPEN_TERMUX",
        "CREATE_WORKSPACE",
        "WRITE_EXERCISE_FILES",
        "RUN_COMMAND",
        "READ_RESULT",
        "data class TermuxWorkspace",
        "data class TermuxCommandTemplate",
        "data class TermuxCommandRequest",
        "data class TermuxCommandResult",
        "data class TermuxRuntimePolicy",
        "requiresExplicitUserAction",
        "AllowedTermuxExecutables",
        "python -m pytest",
        "npm test",
        "./gradlew test",
        "isAllowedTermuxCommandTemplate",
    )
    require('"curl"' not in runtime, "curl must not be whitelisted for programming runtime commands")
    require('"bash"' not in runtime and '"sh"' not in runtime, "raw shell executables must not be whitelisted")

    content_mode = require_contains(
        "core/model/src/main/kotlin/com/mikeyphw/studyflow/core/model/StudyContentMode.kt",
        "StudyContentMode.PROGRAMMING_COURSE",
        "supportsCodeBlocks = true",
        "supportsTermuxRuntime = true",
        "supportsCodingExercises = true",
    )
    require("supportsTermuxRuntime = true" in content_mode, "programming mode must advertise the runtime foundation")

    require_contains(
        "core/model/src/main/kotlin/com/mikeyphw/studyflow/core/model/ProgrammingCourse.kt",
        "isAllowedTermuxCommandTemplate(runCommandTemplate)",
        "isAllowedTermuxCommandTemplate(testCommandTemplate)",
        "allowedCommandTemplates.all(::isAllowedTermuxCommandTemplate)",
    )
    require_contains(
        "core/model/src/test/kotlin/com/mikeyphw/studyflow/core/model/TermuxProgrammingRuntimeTest.kt",
        "python main.py && rm -rf ~/studyflow",
        "assertFalse(isAllowedTermuxCommandTemplate",
        "requiresExplicitUserAction",
    )
    require_contains(
        "build.gradle.kts",
        "verifyTermuxRuntimeFoundation",
        "scripts/verify_termux_runtime_foundation.py",
    )

    print("TERMUX_RUNTIME_FOUNDATION: PASS")


if __name__ == "__main__":
    main()
