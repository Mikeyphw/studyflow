#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def read(path: str) -> str:
    return (ROOT / path).read_text()

def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(message)

manifest = read("app/src/main/AndroidManifest.xml")
bridge = read("app/src/main/java/com/mikeyphw/studyflow/termux/StudyFlowTermuxRunCommandBridge.kt")
receiver = read("app/src/main/java/com/mikeyphw/studyflow/termux/StudyFlowTermuxResultReceiver.kt")
termux = read("core/model/src/main/kotlin/com/mikeyphw/studyflow/core/model/TermuxProgrammingRuntime.kt")
execution = read("core/model/src/main/kotlin/com/mikeyphw/studyflow/core/model/CodingExerciseTermuxExecution.kt")
imports = read("core/model/src/main/kotlin/com/mikeyphw/studyflow/core/model/ContentImportTemplates.kt")
analytics = read("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/ModeAwareAnalytics.kt")
code_block = read("core/designsystem/src/main/java/com/mikeyphw/studyflow/core/designsystem/StudyFlowCodeBlock.kt")

for marker in [
    "com.termux.permission.RUN_COMMAND",
    ".termux.StudyFlowTermuxResultReceiver",
]:
    require(marker in manifest, f"Missing manifest Termux bridge marker: {marker}")
require("<queries>" not in manifest, "Termux bridge must not broaden package visibility")

for marker in [
    "com.termux.app.RunCommandService",
    "com.termux.RUN_COMMAND_PATH",
    "com.termux.RUN_COMMAND_ARGUMENTS",
    "com.termux.RUN_COMMAND_WORKDIR",
    "com.termux.RUN_COMMAND_PENDING_INTENT",
    "PendingIntent.FLAG_ONE_SHOT",
    "splitTermuxCommandTemplate",
    "termuxExecutablePath",
    "context.startService(intent)",
]:
    require(marker in bridge, f"Missing Android Termux RUN_COMMAND bridge marker: {marker}")

for marker in ['EXTRA_PLUGIN_RESULT_BUNDLE = \"result\"', 'exitCode', 'stdout', 'stderr', 'StudyFlowTermuxResultCache.record']:
    require(marker in receiver, f"Missing Termux result receiver marker: {marker}")

for marker in ["TermuxCommandInvocation", "splitTermuxCommandTemplate", "termuxExecutablePath"]:
    require(marker in termux, f"Missing safe command invocation marker: {marker}")

require("safeCommandTemplateFor" in execution, "Coding exercises must guard safe Termux commands")
require("No safe Termux command is available" in execution, "Unsupported languages must fail closed")
require('ProgrammingLanguage.SHELL -> "sh main.sh"' not in execution, "Shell default must not call sh")
require('ProgrammingLanguage.SQL -> "sqlite3 < query.sql"' not in execution, "SQL default must not use redirection")
require('""' in execution and "ProgrammingLanguage.SQL" in execution, "Unsupported language defaults must be blank")

require("while (index < line.length)" in imports, "CSV parser must skip escaped quote pairs")
require("while (index < lines.size && !lines[index].startsWith(headingPrefix))" in imports,
        "Markdown preview must parse row-local blocks")
require("Import heading title is blank" in imports, "Markdown preview must report row-level heading errors")

for marker in ["StudyContentMode.PROGRAMMING_COURSE", "StudyContentMode.LANGUAGE_DECK", "WEAK_CONCEPTS", "MISTAKE_PATTERNS"]:
    require(marker in analytics, f"Missing mode-aware analytics marker: {marker}")

for marker in ["StudyFlowCodeBlock", "LocalClipboard", "setClipEntry", "SelectionContainer", "showLineNumbers"]:
    require(marker in code_block, f"Missing syntax code block marker: {marker}")

print("CONTENT_MODES_FINAL_INTEGRATION_SEAL_R3_OK")
