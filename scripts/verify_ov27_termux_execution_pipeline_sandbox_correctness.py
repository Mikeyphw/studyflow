#!/usr/bin/env python3
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FAILURES: list[str] = []
CHECKS = 0


def read(path: str) -> str:
    target = ROOT / path
    if not target.exists():
        FAILURES.append(f"missing file: {path}")
        return ""
    return target.read_text(encoding="utf-8")


def require(condition: bool, message: str) -> None:
    global CHECKS
    CHECKS += 1
    if not condition:
        FAILURES.append(message)


def require_in(needle: str, haystack: str, label: str) -> None:
    require(needle in haystack, f"expected {label}: {needle!r}")


def require_not_in(needle: str, haystack: str, label: str) -> None:
    require(needle not in haystack, f"forbidden {label}: {needle!r}")


manifest = read("app/src/main/AndroidManifest.xml")
require_in("com.termux.permission.RUN_COMMAND", manifest, "narrow Termux RUN_COMMAND permission")
require_in("<queries>", manifest, "Android package visibility declaration")
require_in('<package android:name="com.termux" />', manifest, "Termux package visibility")
require_in('<action android:name="com.termux.RUN_COMMAND" />', manifest, "Termux RUN_COMMAND query")
require_in(".termux.StudyFlowTermuxResultReceiver", manifest, "Termux result receiver")

bridge = read("app/src/main/java/com/mikeyphw/studyflow/termux/StudyFlowTermuxRunCommandBridge.kt")
require_in("isTermuxRunCommandAvailable", bridge, "Termux availability check")
require_in("PackageManager.MATCH_DEFAULT_ONLY", bridge, "RUN_COMMAND service package resolution")
require_in("com.termux.app.RunCommandService", bridge, "public Termux service component")
require_in("com.termux.RUN_COMMAND_PATH", bridge, "RUN_COMMAND path extra")
require_in("com.termux.RUN_COMMAND_ARGUMENTS", bridge, "RUN_COMMAND arguments extra")
require_in("com.termux.RUN_COMMAND_WORKDIR", bridge, "RUN_COMMAND workdir extra")
require_in("com.termux.RUN_COMMAND_PENDING_INTENT", bridge, "pending result intent extra")
require_in("PendingIntent.FLAG_ONE_SHOT", bridge, "one-shot pending intent")
require_in("MessageDigest.getInstance(\"SHA-256\")", bridge, "SHA-256 request-code derivation")
require_in("data = Uri.parse", bridge, "request-id data URI scoping")
require_in("termuxWorkspaceWorkdir(request.workspace", bridge, "configured workspace workdir")
require_in("termuxExecutablePath(invocation.executable)", bridge, "executable path allowlist")
require_not_in("stablePendingIntentRequestCode", bridge, "old hash-code request-code helper")
require_not_in("putExtra(EXTRA_COMMAND_PATH, template.commandLine)", bridge, "raw command line as executable path")

receiver = read("app/src/main/java/com/mikeyphw/studyflow/termux/StudyFlowTermuxResultReceiver.kt")
require_in("goAsync()", receiver, "async BroadcastReceiver persistence")
require_in("recordTermuxCommandResult(result)", receiver, "repository result persistence")
require_in("requireStudyFlowAppContainer", receiver, "app container repository path")
require_in("REQUEST_ID", receiver, "result request id parsing")
require_not_in("StudyFlowTermuxResultCache", receiver, "volatile result cache")
require_not_in("latestResult", receiver, "latest result singleton")
require_not_in("@Volatile", receiver, "volatile state")

coordinator = read("app/src/main/java/com/mikeyphw/studyflow/termux/StudyFlowTermuxExecutionCoordinator.kt")
require_in("enqueueTermuxExerciseRun", coordinator, "durable request enqueue before launch")
require_in("launchRunCommand", coordinator, "Android bridge launch")
require_in("markTermuxRequestWaitingForTermux", coordinator, "WAITING_FOR_TERMUX transition after send")
require_in("CREATE_WORKSPACE, WRITE_EXERCISE_FILES", coordinator, "four-stage pipeline documentation")
require_in("RUN_COMMAND and READ_RESULT", coordinator, "four-stage pipeline documentation")
require_in("sanitizeTermuxWorkspaceSlug", coordinator, "collision-resistant request id prefix sanitization")

repo_interface = read("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/ProgrammingCourseRepository.kt")
for token in [
    "suspend fun enqueueTermuxExerciseRun",
    "suspend fun markTermuxRequestWaitingForTermux",
    "suspend fun recordTermuxCommandResult",
]:
    require_in(token, repo_interface, "domain Termux repository API")
require_in("repository.enqueueTermuxExerciseRun", repo_interface, "manager enqueue forwarding")
require_in("repository.recordTermuxCommandResult", repo_interface, "manager result forwarding")

repo = read("core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/RoomProgrammingCourseRepository.kt")
for token in [
    "database.withTransaction",
    "plan.requests.forEach",
    "dao.upsertTermuxCommandRequest",
    "dao.findTermuxCommandRequest(result.requestId)",
    "dao.upsertTermuxCommandResult(result.asEntity())",
    "dao.updateTermuxCommandRequestState(request.id, result.state.name)",
    "exercise.applyTermuxResult(result",
    "result.requestId == request.id",
    "request.exerciseId",
    "dao.findCodingExercise(request.studyPlanId.value, exerciseId.value)",
]:
    require_in(token, repo, "Room-backed durable Termux pipeline")
require_not_in("StudyFlowTermuxResultCache", repo, "repository should not use volatile result cache")

dao = read("core/database/src/main/java/com/mikeyphw/studyflow/core/database/dao/ProgrammingCourseDao.kt")
for token in [
    "findCodingExercise(planId: String, exerciseId: String)",
    "findTermuxCommandRequest(requestId: String)",
    "updateTermuxCommandRequestState(requestId: String, state: String)",
]:
    require_in(token, dao, "DAO correlation/query support")

runtime = read("core/model/src/main/kotlin/com/mikeyphw/studyflow/core/model/TermuxProgrammingRuntime.kt")
for token in [
    "tokenizeTermuxCommandTemplate",
    "InlineInterpreterExecutables",
    "InlineInterpreterFlags",
    "TermuxCommandInvocation",
    "Termux result request id must be a stable safe slug.",
    "isAllowedStudyFlowInvocation",
    "arguments.none { it in InlineInterpreterFlags }",
    "termuxWorkspaceWorkdir",
    "require(isSafeRelativeTermuxPath(workingDirectory))",
    '"$" + "PREFIX/bin/$executable"',
]:
    require_in(token, runtime, "Termux runtime sandbox hardening")
require_not_in("commandLine.split(Regex", runtime, "old whitespace tokenizer")
require_not_in("command.split(Regex", runtime, "old whitespace tokenizer")
require_not_in("startsWith(\"~/\")", re.sub(r"fun termuxExecutablePath[\s\S]*?\n}\n", "", runtime), "executable path must not accept home-relative executable")

course = read("core/model/src/main/kotlin/com/mikeyphw/studyflow/core/model/ProgrammingCourse.kt")
for token in [
    "private val DangerousShellFragments",
    "tokenizeTermuxCommandTemplate(command)",
    "normalized.startsWith(\"/\")",
    'normalized == "$" + "HOME"',
    'normalized.startsWith("$" + "HOME/")',
]:
    require_in(token, course, "safe path/template model guard")

execution = read("core/model/src/main/kotlin/com/mikeyphw/studyflow/core/model/CodingExerciseTermuxExecution.kt")
for token in [
    "workspace.courseSlug == sanitizeTermuxWorkspaceSlug",
    "result.requestId.isNotBlank()",
    "result.requestId.endsWith(\"-run\")",
    "result.requestId.contains(sanitizeTermuxWorkspaceSlug(id.value))",
]:
    require_in(token, execution, "exercise result correlation safety")

workspace = read("feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/TodayWorkspaceScreen.kt")
require_in("onRunTermuxExercise: suspend (CodingExerciseId) -> Boolean", workspace, "UI Termux run callback")
require_in("nextRunnableTermuxExercise", workspace, "next runnable exercise selection")
require_in("programming_termux_launch_sent", workspace, "successful launch snackbar")
require_in("programming_termux_launch_failed", workspace, "failed launch snackbar")
require_in("catch (cancelled: CancellationException)", workspace, "cancellation-safe launch error handling")

panel = read("feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/ProgrammingCourseWorkspacePanel.kt")
require_in("nextTermuxExerciseTitle", panel, "Termux exercise-specific UI title")
require_in("onRunTermuxExercise", panel, "Termux exercise run action")
require_in("programming_termux_run_next", panel, "Termux run-next label")

nav = read("app/src/main/java/com/mikeyphw/studyflow/StudyFlowV2Navigation.kt")
require_in("StudyFlowTermuxExecutionCoordinator", nav, "navigation coordinator import/use")
require_in("LocalContext.current", nav, "Android context for Termux bridge")
require_in("onRunTermuxExercise = { exerciseId ->", nav, "Today route Termux launch wiring")
require_in("container.manageProgrammingCourse", nav, "repository manager passed to coordinator")

strings = read("feature/workspace/src/main/res/values/strings.xml")
strings_pt = read("feature/workspace/src/main/res/values-pt-rBR/strings.xml")
for token in ["programming_termux_run_next", "programming_termux_launch_sent", "programming_termux_launch_failed"]:
    require_in(token, strings, "English UI strings")
    require_in(token, strings_pt, "Brazilian Portuguese UI strings")

tests = read("core/model/src/test/kotlin/com/mikeyphw/studyflow/core/model/TermuxProgrammingRuntimeTest.kt")
for token in [
    "python -c 'print(1)'",
    "node -e",
    "bash -c echo hi",
    "tokenizeTermuxCommandTemplate",
    "/data/data/com.termux/files/home/studyflow",
]:
    require_in(token, tests, "OV27 runtime regression tests")

app_tests = read("app/src/test/java/com/mikeyphw/studyflow/termux/StudyFlowTermuxBridgeContractTest.kt")
for token in [
    "manifest declares Termux permission package visibility",
    "result receiver persists correlated results",
    "coordinator durably queues four stage pipeline",
    "MessageDigest.getInstance",
    "assertFalse(\"StudyFlowTermuxResultCache\" in receiver)",
]:
    require_in(token, app_tests, "OV27 app contract tests")

if FAILURES:
    print("OV27 Termux execution pipeline contract: FAIL")
    for failure in FAILURES:
        print(f" - {failure}")
    sys.exit(1)

print(f"OV27 Termux execution pipeline & sandbox correctness contract: PASS ({CHECKS} checks)")
