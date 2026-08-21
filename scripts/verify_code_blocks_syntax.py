#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(f"CMOV-03 code block syntax failed: {message}")


def require_contains(path: str, *needles: str) -> str:
    text = read(path)
    missing = [needle for needle in needles if needle not in text]
    require(not missing, f"{path} missing {missing}")
    return text


def main() -> None:
    source = require_contains(
        "core/designsystem/src/main/java/com/mikeyphw/studyflow/core/designsystem/StudyFlowCodeBlock.kt",
        "fun StudyFlowCodeBlock(",
        "object StudyFlowSyntaxHighlighter",
        "data class StudyFlowSyntaxTheme",
        "LocalClipboardManager",
        "SelectionContainer",
        "horizontalScroll",
        "lineNumberColumn",
        "FontFamily.Monospace",
        "AnnotatedString",
    )
    for language in (
        "KOTLIN",
        "JAVA",
        "PYTHON",
        "JAVASCRIPT",
        "TYPESCRIPT",
        "SHELL",
        "SQL",
        "HTML",
        "CSS",
        "JSON",
        "YAML",
        "MARKDOWN",
        "C",
        "CPP",
        "GO",
        "RUST",
    ):
        require(f"ProgrammingLanguage.{language}" in source, f"missing {language} syntax branch")
    require_contains(
        "core/designsystem/src/test/java/com/mikeyphw/studyflow/core/designsystem/StudyFlowCodeBlockContractTest.kt",
        "code block exposes syntax highlighting and programming-language support",
        "StudyFlowSyntaxHighlighter",
        "LocalClipboardManager",
    )
    require_contains(
        "build.gradle.kts",
        "verifyCodeBlocksSyntax",
        "scripts/verify_code_blocks_syntax.py",
    )
    require_contains(
        "CMOV03_CODE_BLOCKS_SYNTAX_HIGHLIGHTING.md",
        "StudyFlowCodeBlock",
        "Termux bridge",
    )
    print("CODE_BLOCKS_SYNTAX: PASS")


if __name__ == "__main__":
    main()
