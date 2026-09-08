#!/usr/bin/env python3
from __future__ import annotations

import sys
import tomllib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FAILURES: list[str] = []


def require(condition: bool, message: str) -> None:
    if not condition:
        FAILURES.append(message)


def read(rel: str) -> str:
    path = ROOT / rel
    require(path.is_file(), f"missing required file: {rel}")
    return path.read_text(encoding="utf-8") if path.is_file() else ""


def require_tokens(rel: str, *tokens: str) -> str:
    text = read(rel)
    for token in tokens:
        require(token in text, f"{rel} missing token: {token}")
    return text


domain = require_tokens(
    "core/tvcontrol/src/main/kotlin/com/mikeyphw/studyflow/core/tvcontrol/StudyFlowTvDomainAction.kt",
    "object StudyFlowTvDomainActionProtocol",
    "canonicalDataOwnerRequired = true",
    "tvRepositoryMutationForbidden = true",
    "optimisticScoringForbidden = true",
    "data class StudyFlowTvDomainAction(",
    "data class StudyFlowTvDomainActionResult(",
    "object StudyFlowTvDomainActionMapper",
    "SESSION_START",
    "SESSION_PAUSE",
    "SESSION_RESUME",
    "SESSION_COMPLETE",
    "PRACTICE_ANSWER",
    "PRACTICE_MARK_REVIEW",
    "PRACTICE_FINISH",
    "MOCK_START",
    "MOCK_FINISH",
    "REVIEW_GRADE",
    "REVIEW_ERROR_NOTE",
    "current.today?.nextActivity?.activityId",
    "questionSet?.questionSetId",
    "reviewItem?.questionId",
)

presentation = require_tokens(
    "core/tvcontrol/src/main/kotlin/com/mikeyphw/studyflow/core/tvcontrol/StudyFlowTvPresentation.kt",
    "data class StudyFlowTvQuestionAnswerPresentation(",
    "val started: Boolean = false",
    "val submitted: Boolean = false",
    "val currentQuestionId: String? = null",
    "val answers: List<StudyFlowTvQuestionAnswerPresentation>",
    "val elapsedMinutes: Int",
    "val questionId: String? = null",
)

projector = require_tokens(
    "app/src/main/java/com/mikeyphw/studyflow/tvcontrol/StudyFlowTvPresentationProjector.kt",
    "StudySessionTimer.activeSeconds(session, nowEpochMillis)",
    "PracticeExecutionEngine().stateForTest",
    "StudyFlowTvQuestionAnswerPresentation(",
    "questionId = item.questionId?.value",
    "scorePercent = mock.scorePercent?.toInt()?.coerceIn(0, 100)",
)
require("* 7 / 10" not in projector, "phone projector must not contain synthetic 70% mock scoring")

executor = require_tokens(
    "app/src/main/java/com/mikeyphw/studyflow/tvcontrol/StudyFlowTvDomainActionExecutor.kt",
    'object StudyFlowTvDomainPersistenceContract',
    'overlay = "TVOV25"',
    "interface StudyFlowTvDomainActionJournal",
    "SharedPreferencesStudyFlowTvDomainActionJournal",
    "fun interface StudyFlowTvCanonicalMutationGateway",
    "class DefaultStudyFlowTvCanonicalMutationGateway(",
    "class StudyFlowTvDomainActionExecutor(",
    "journal.read(action.actionId)?.let { return it }",
    "journal.write(result)",
    "sessionManager.startFromActivity(",
    "sessionManager.pause(",
    "sessionManager.resume(",
    "sessionManager.complete(",
    "practiceManager.answerPracticeQuestion(",
    "practiceManager.finishPracticeAttempt(",
    "practiceManager.startMockExamAttempt(",
    "practiceManager.finishMockExamAttempt(",
    "reviewManager.scheduleQuestionReview(",
    "reviewManager.gradeReview(",
    "reviewManager.createErrorFromQuestion(",
)
for forbidden in ("RoomDatabase", "@Dao", "7 / 10"):
    require(forbidden not in executor, f"phone persistence executor contains forbidden shortcut: {forbidden}")

application = require_tokens(
    "app/src/main/java/com/mikeyphw/studyflow/StudyFlowApplication.kt",
    "val tvDomainActionExecutor: StudyFlowTvDomainActionExecutor",
    "DefaultStudyFlowTvCanonicalMutationGateway(",
    "sessionManager = manageStudySession",
    "practiceManager = manageStudyPractice",
    "reviewManager = manageStudyReview",
    "sessionRepository = studySessionRepository",
    "practiceRepository = studyPracticeRepository",
    "scheduleRepository = studyScheduleRepository",
    "SharedPreferencesStudyFlowTvDomainActionJournal(application)",
)

wire = require_tokens(
    "core/tvcontrol/src/main/kotlin/com/mikeyphw/studyflow/core/tvcontrol/StudyFlowTvWireProtocol.kt",
    "DOMAIN_ACTION_RESULT",
    "val domainActionResult: StudyFlowTvDomainActionResult? = null",
    "val pendingDomainAction: StudyFlowTvDomainAction? = null",
    "val lastDomainActionResult: StudyFlowTvDomainActionResult? = null",
)
link = require_tokens(
    "core/tvlink/src/main/kotlin/com/mikeyphw/studyflow/core/tvlink/StudyFlowTvLocalLink.kt",
    "fun pendingDomainAction(): StudyFlowTvDomainAction?",
    "fun acknowledgeDomainAction(result: StudyFlowTvDomainActionResult): Boolean",
    "fun acknowledgeDomainAction(",
    "StudyFlowTvWireOperation.DOMAIN_ACTION_RESULT",
    "pendingDomainAction = safePendingDomainAction()",
    "lastDomainActionResult = safeLastDomainActionResult()",
)

outbox = require_tokens(
    "app-tv/src/main/java/com/mikeyphw/studyflow/tv/domain/StudyFlowTvDomainActionOutbox.kt",
    "class StudyFlowTvDomainActionOutbox",
    "LinkedHashMap<String, StudyFlowTvDomainAction>()",
    "maxPendingActions",
    "StudyFlowTvDomainActionMapper.fromCommand",
    "fun acknowledge(result: StudyFlowTvDomainActionResult): Boolean",
)
activity = require_tokens(
    "app-tv/src/main/java/com/mikeyphw/studyflow/tv/StudyFlowTvActivity.kt",
    "private val domainActionOutbox = StudyFlowTvDomainActionOutbox()",
    "override fun pendingDomainAction(): StudyFlowTvDomainAction?",
    "override fun acknowledgeDomainAction(result: StudyFlowTvDomainActionResult): Boolean",
    "StudyFlowTvDomainActionProtocol.requiresPersistence(command)",
    "origin = StudyFlowTvControlOrigin.PAIRED_PHONE",
    "origin = StudyFlowTvControlOrigin.LOCAL_REMOTE",
)

controller = require_tokens(
    "app/src/main/java/com/mikeyphw/studyflow/tvcontrol/StudyFlowTvPhoneController.kt",
    "private val domainActionExecutor: StudyFlowTvDomainActionExecutor",
    "private var latestDomainSnapshot: StudyFlowTvPresentationSnapshot?",
    "StudyFlowTvDomainActionProtocol.requiresPersistence(command)",
    "StudyFlowTvDomainActionMapper.fromCommand(",
    "domainActionExecutor.execute(domainAction)",
    "domainMutationPollingIndependentOfCast = true",
    "private fun startDomainActionPoll()",
    "startDomainActionPoll()",
    "response.pendingDomainAction ?: return",
    "gateway.acknowledgeDomainAction(endpoint, result)",
    "presentationFeed.observe(planId).collect",
)

# TV-originated canonical mutations must keep draining while paired even when presentation casting is stopped.
start_discovery = controller.split("fun startDiscovery()", 1)[1].split("fun stopDiscovery()", 1)[0]
require("startDomainActionPoll()" in start_discovery, "controller must start domain-action polling with the controller lifecycle")
poll_loop = controller.split("private fun startDomainActionPoll()", 1)[1].split("private suspend fun handlePendingDomainAction", 1)[0]
require("state.credential == null" in poll_loop, "domain-action poll must gate on pairing credential")
require("castSync.enabled" not in poll_loop, "domain-action poll must not depend on cast being enabled")
stop_cast = controller.split("fun stopCast()", 1)[1].split("fun refreshContinuity()", 1)[0]
require("domainPollJob?.cancel()" not in stop_cast, "Stop cast must not stop canonical TV-domain action polling")

screen = require_tokens(
    "app/src/main/java/com/mikeyphw/studyflow/tvcontrol/StudyFlowTvPhoneControllerScreen.kt",
    "domainActionExecutor: StudyFlowTvDomainActionExecutor",
    "domainActionExecutor = domainActionExecutor",
)
app = require_tokens(
    "app/src/main/java/com/mikeyphw/studyflow/StudyFlowApp.kt",
    "domainActionExecutor = container.tvDomainActionExecutor",
)

session = read("app-tv/src/main/java/com/mikeyphw/studyflow/tv/session/StudyFlowTvStudySessionExperience.kt")
practice = read("app-tv/src/main/java/com/mikeyphw/studyflow/tv/practice/StudyFlowTvQuestionPractice.kt")
mock = read("app-tv/src/main/java/com/mikeyphw/studyflow/tv/mockexam/StudyFlowTvMockExamStudio.kt")
review = read("app-tv/src/main/java/com/mikeyphw/studyflow/tv/reviews/StudyFlowTvReviewNotebook.kt")
for text, label, forbidden in (
    (session, "session", "elapsedMinutes = (state.elapsedMinutes +"),
    (practice, "practice", "selectedChoices = selectedChoices +"),
    (mock, "mock", "correctAnswerCount = state.totalQuestions * 7 / 10"),
    (review, "review", "knownReviewIds = state.knownReviewIds +"),
    (review, "review", "errorNoteIds = state.errorNoteIds +"),
):
    require(forbidden not in text, f"TV {label} still contains pseudo-domain mutation: {forbidden}")
require("StudyFlowTvMockExamAction.PAUSE," not in mock.split("StudyFlowTvMockExamStatus.RUNNING -> listOf(",1)[-1].split(")",1)[0], "running mock actions must not expose fake canonical pause")

adapter = require_tokens(
    "app-tv/src/main/java/com/mikeyphw/studyflow/tv/presentation/StudyFlowTvPresentationAdapter.kt",
    "val selectedChoices = source.answers.mapNotNull",
    "completed = source.submitted",
    "elapsedMinutes = source.elapsedMinutes",
)

require_tokens(
    "core/tvcontrol/src/test/kotlin/com/mikeyphw/studyflow/core/tvcontrol/StudyFlowTvDomainActionTest.kt",
    "mapperUsesCanonicalIdsAndAnswerLabelsFromPresentation",
    "startReviewAndMarkForReviewResolveOnlyFromCanonicalContext",
)
require_tokens(
    "app/src/test/java/com/mikeyphw/studyflow/tvcontrol/StudyFlowTvDomainActionExecutorTest.kt",
    "executorJournalsSuccessfulActionAndDoesNotReplayMutation",
    "executorJournalsTerminalFailureSoTransportRetryCannotDuplicateIt",
)
require_tokens(
    "app-tv/src/test/java/com/mikeyphw/studyflow/tv/domain/StudyFlowTvDomainActionOutboxTest.kt",
    "outboxQueuesCanonicalRequestAndRemovesOnlyMatchingAcknowledgement",
)

build_contract = require_tokens(
    "app-tv/src/main/java/com/mikeyphw/studyflow/tv/StudyFlowTvBuildContract.kt",
    'currentTvOverlay = "TVOV25"',
    "packageVersionCode = 25",
    "object StudyFlowTvDomainPersistenceBuildContract",
    "domainPersistenceConvergenceIntroduced = true",
    "phoneOwnsCanonicalMutations = true",
    "idempotentActionJournalRequired = true",
    "fakeMockScoringForbidden = true",
)
require_tokens(
    "app-tv/src/test/java/com/mikeyphw/studyflow/tv/StudyFlowTvBuildContractTest.kt",
    "domainTruthAndPersistenceConvergenceIsExplicit",
    "assertEquals(25, StudyFlowTvPackagingBuildContract.packageVersionCode)",
)

tv_build = read("app-tv/build.gradle.kts")
require('versionCode = 25' in tv_build and 'versionName = "0.1.0-tv25"' in tv_build, "TV package must advance to TVOV25 identity")
for forbidden in (":app:", ":feature:", ":core:data", ":core:database", "androidx.tv", "play-services-cast", "androidx.mediarouter"):
    require(forbidden not in tv_build, f"TV APK dependency boundary regressed: {forbidden}")

root_gradle = read("build.gradle.kts")
devtool = read(".devtool.toml")
require('tasks.register<Exec>("verifyStudyFlowTvDomainPersistenceConvergence")' in root_gradle, "missing TVOV25 Gradle verifier task")
require(devtool.count('"verifyStudyFlowTvDomainPersistenceConvergence"') >= 8, "studyflow_tv must wire TVOV25 verifier across tasks/phases")
with (ROOT / ".devtool.toml").open("rb") as fh:
    config = tomllib.load(fh)
tv_target = config.get("targets", {}).get("studyflow_tv", {})
flat = repr(tv_target.get("tasks", {})) + repr(tv_target.get("validation", {}).get("phases", {}))
for forbidden in (":app:", ":feature:", ":core:data:", ":core:database:"):
    require(forbidden not in flat, f"studyflow_tv target isolation regressed with {forbidden}")

roadmap = read("ANDROID_TV_ROADMAP.md")
rules = read("TV_ANDROID_TV_FULL_ROADMAP_AND_WORKING_RULES.md")
readme = read("README.md")
notes = read("TVOV25_ANDROID_TV_DOMAIN_TRUTH_PERSISTENCE_CONVERGENCE.md")
quality = read("docs/quality/TVOV25_DOMAIN_TRUTH_PERSISTENCE_CONTRACTS.md")
require("TVOV25  Domain truth and persistence convergence [implemented]" in roadmap, "roadmap must mark TVOV25 implemented")
require("TVOV26  End-to-end physical-device final seal [next]" in roadmap, "roadmap must mark TVOV26 next")
require("TVOV25  Domain truth and persistence convergence [implemented]" in rules, "working rules must mark TVOV25 implemented")
require("TVOV26  End-to-end physical-device final seal [next]" in rules, "working rules must mark TVOV26 next")
require("TVOV00 through TVOV25" in rules, "working rules must record current TV chain")
for token in ("canonical", "idempotent", "outbox", "persist", "TVOV26"):
    require(token.lower() in notes.lower(), f"TVOV25 implementation notes missing {token}")
for token in ("managers", "action id", "projection", "mock score", "25 MiB"):
    require(token.lower() in quality.lower(), f"TVOV25 quality contract missing {token}")
require("TVOV25" in readme and "persistence" in readme.lower(), "README missing TVOV25 summary")

if FAILURES:
    print("TVOV25_DOMAIN_PERSISTENCE_CONVERGENCE: FAIL")
    for failure in FAILURES:
        print(f"- {failure}")
    sys.exit(1)
print("TVOV25_DOMAIN_PERSISTENCE_CONVERGENCE: PASS")
