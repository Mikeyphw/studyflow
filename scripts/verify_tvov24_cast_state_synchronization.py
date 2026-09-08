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

sync = require_tokens(
    "app/src/main/java/com/mikeyphw/studyflow/tvcontrol/StudyFlowTvCastSynchronization.kt",
    'object StudyFlowTvCastSynchronizationContract',
    'overlay = "TVOV24"',
    'explicitStartAndStopRequired = true',
    'canonicalPresentationFeedRequired = true',
    'authenticatedTvov22TransportRequired = true',
    'serializedLatestSnapshotDeliveryRequired = true',
    'receiverReconnectResyncRequired = true',
    'presentationReceiptAcknowledgementRequired = true',
    'pendingRemoteStopRetriedAfterReconnect = true',
    'enum class StudyFlowTvCastSyncStatus',
    'data class StudyFlowTvCastSyncState',
    'interface StudyFlowTvPresentationFeed',
    'fun acknowledges(',
)
for forbidden in ('CastContext', 'MediaRouter', 'RoomDatabase', '@Dao', 'ServerSocket('):
    require(forbidden not in sync, f"TVOV24 synchronization policy introduced forbidden stack: {forbidden}")

source = require_tokens(
    "app/src/main/java/com/mikeyphw/studyflow/tvcontrol/StudyFlowTvPresentationSource.kt",
    'class StudyFlowTvPresentationSource(',
    ') : StudyFlowTvPresentationFeed',
    'override fun observe(planId: StudyPlanId)',
)
require('RoomDatabase' not in source and '@Dao' not in source, "presentation feed must stay repository/domain based")

controller = require_tokens(
    "app/src/main/java/com/mikeyphw/studyflow/tvcontrol/StudyFlowTvPhoneController.kt",
    'presentationCastSynchronizationDeferredToTvov24 = false',
    'livePresentationSynchronizationImplemented = true',
    'fun sendPresentation(endpoint: StudyFlowTvReceiverEndpoint, snapshot: StudyFlowTvPresentationSnapshot)',
    'client.sendPresentation(endpoint, snapshot)',
    'private val presentationFeed: StudyFlowTvPresentationFeed',
    'fun startCast(planId: StudyPlanId?)',
    'presentationFeed.observe(planId).collect',
    'fun resyncCast()',
    'fun stopCast()',
    'private fun requestCastDelivery()',
    'private suspend fun deliverLatestPresentation(',
    'gateway.sendPresentation(endpoint, snapshot)',
    'StudyFlowTvCastSynchronizationPolicy.acknowledges(snapshot, response)',
    'remoteStopPending = true',
    'StudyFlowTvControlAction.STOP_CAST',
    'private fun reconcilePresentationReceipt(response: StudyFlowTvWireResponse)',
    'receipt.generatedAtEpochMillis == latest.generatedAtEpochMillis',
)
for forbidden in ('CastContext', 'MediaRouter', 'RoomDatabase', '@Dao', 'ServerSocket('):
    require(forbidden not in controller, f"phone cast synchronization introduced forbidden/duplicate stack: {forbidden}")

screen = require_tokens(
    "app/src/main/java/com/mikeyphw/studyflow/tvcontrol/StudyFlowTvPhoneControllerScreen.kt",
    'presentationSource: StudyFlowTvPresentationSource',
    'defaultPlanId: StudyPlanId?',
    'presentationFeed = presentationSource',
    'tv_controller_cast_title',
    'state.castSync.status.name',
    'controller.startCast(defaultPlanId)',
    'controller::resyncCast',
    'controller::stopCast',
    'state.castSync.acknowledgedSnapshotId',
    'state.castSync.remoteStopPending',
)
require('tv_controller_cast_deferred' not in screen, "TV controller must no longer present cast as deferred")

app = require_tokens(
    "app/src/main/java/com/mikeyphw/studyflow/StudyFlowApp.kt",
    'presentationSource = container.tvPresentationSource',
    'defaultPlanId = preferences.defaultStudyPlanId',
)

presentation = require_tokens(
    "core/tvcontrol/src/main/kotlin/com/mikeyphw/studyflow/core/tvcontrol/StudyFlowTvPresentation.kt",
    'data class StudyFlowTvPresentationReceipt(',
    'val snapshotId: String',
    'val generatedAtEpochMillis: Long',
)
wire = require_tokens(
    "core/tvcontrol/src/main/kotlin/com/mikeyphw/studyflow/core/tvcontrol/StudyFlowTvWireProtocol.kt",
    'val presentationReceipt: StudyFlowTvPresentationReceipt? = null',
)
link = require_tokens(
    "core/tvlink/src/main/kotlin/com/mikeyphw/studyflow/core/tvlink/StudyFlowTvLocalLink.kt",
    'val presentationReceipt: StudyFlowTvPresentationReceipt? = null',
    'fun currentPresentationReceipt(): StudyFlowTvPresentationReceipt?',
    'presentationReceipt = applied.presentationReceipt',
    'presentationReceipt = safePresentationReceipt()',
    'private fun safePresentationReceipt()',
)
for forbidden in ('play-services-cast', 'androidx.mediarouter', 'RoomDatabase', '@Dao'):
    require(forbidden not in link, f":core:tvlink cast sync must reuse lightweight link; found {forbidden}")

activity = require_tokens(
    "app-tv/src/main/java/com/mikeyphw/studyflow/tv/StudyFlowTvActivity.kt",
    'override fun currentPresentationReceipt(): StudyFlowTvPresentationReceipt?',
    'presentationInbox.receipt()',
    'command.action == com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvControlAction.STOP_CAST',
    'presentationInbox.clear()',
    'StudyFlowTvPresentationAdapter.waitingState(controlState)',
)
adapter = require_tokens(
    "app-tv/src/main/java/com/mikeyphw/studyflow/tv/presentation/StudyFlowTvPresentationAdapter.kt",
    'fun clear()',
    'fun receipt(): StudyFlowTvPresentationReceipt?',
)
reducer = require_tokens(
    "app-tv/src/main/java/com/mikeyphw/studyflow/tv/control/StudyFlowTvControlStateReducer.kt",
    'command.action == StudyFlowTvControlAction.STOP_CAST',
    'return state',
)

require_tokens(
    "app/src/test/java/com/mikeyphw/studyflow/tvcontrol/StudyFlowTvCastSynchronizationTest.kt",
    'receiptMustMatchTheExactSnapshotToAcknowledgeSynchronization',
    'synchronizationContractRequiresExplicitStopReconnectAndAuthenticatedTransport',
    'expiredSessionResponsesAreRecognizedAsCastAuthorizationFailures',
)
require_tokens(
    "core/tvcontrol/src/test/kotlin/com/mikeyphw/studyflow/core/tvcontrol/StudyFlowTvWireProtocolTest.kt",
    'presentationReceiptRoundTripsForExactCastAcknowledgement',
)
require_tokens(
    "app-tv/src/test/java/com/mikeyphw/studyflow/tv/presentation/StudyFlowTvPresentationAdapterTest.kt",
    'inboxReceiptTracksCurrentSnapshotAndClearEndsCast',
)
require_tokens(
    "app-tv/src/test/java/com/mikeyphw/studyflow/tv/control/StudyFlowTvControlStateReducerTest.kt",
    'stopCastIsAcceptedAsARevisionedPresentationLifecycleCommand',
)

build_contract = require_tokens(
    "app-tv/src/main/java/com/mikeyphw/studyflow/tv/StudyFlowTvBuildContract.kt",
    'currentTvOverlay = "TVOV24"',
    'packageVersionCode = 24',
    'livePresentationSynchronizationDeferredToTvov24 = false',
    'object StudyFlowTvCastSynchronizationBuildContract',
    'realCastStateSynchronizationIntroduced = true',
    'presentationReceiptAcknowledgementRequired = true',
    'reconnectResyncRequired = true',
    'tvStopCastClearsPresentationInbox = true',
)
require_tokens(
    "app-tv/src/test/java/com/mikeyphw/studyflow/tv/StudyFlowTvBuildContractTest.kt",
    'realCastStateSynchronizationIsProductionWired',
)

tv_build = read("app-tv/build.gradle.kts")
require('versionCode = 24' in tv_build and 'versionName = "0.1.0-tv24"' in tv_build, "TV package must advance to TVOV24 identity")
for forbidden in (':app:', ':feature:', ':core:data', ':core:database', 'androidx.tv', 'play-services-cast', 'androidx.mediarouter'):
    require(forbidden not in tv_build, f"TV APK dependency boundary regressed: {forbidden}")

root_gradle = read("build.gradle.kts")
devtool = read(".devtool.toml")
require('tasks.register<Exec>("verifyStudyFlowTvCastStateSynchronization")' in root_gradle, "missing TVOV24 Gradle verifier task")
require(devtool.count('"verifyStudyFlowTvCastStateSynchronization"') >= 8, "studyflow_tv must wire TVOV24 verifier across tasks/phases")
with (ROOT / ".devtool.toml").open("rb") as fh:
    config = tomllib.load(fh)
tv_target = config.get("targets", {}).get("studyflow_tv", {})
flat = repr(tv_target.get("tasks", {})) + repr(tv_target.get("validation", {}).get("phases", {}))
for forbidden in (":app:", ":feature:", ":core:data:", ":core:database:"):
    require(forbidden not in flat, f"studyflow_tv target isolation regressed with {forbidden}")

roadmap = read("ANDROID_TV_ROADMAP.md")
rules = read("TV_ANDROID_TV_FULL_ROADMAP_AND_WORKING_RULES.md")
readme = read("README.md")
notes = read("TVOV24_ANDROID_TV_REAL_CAST_STATE_SYNCHRONIZATION.md")
quality = read("docs/quality/TVOV24_REAL_CAST_STATE_SYNCHRONIZATION_CONTRACTS.md")
require("TVOV24  Real cast/state synchronization [implemented]" in roadmap, "roadmap must mark TVOV24 implemented")
require("TVOV25  Domain truth and persistence convergence [next]" in roadmap, "roadmap must mark TVOV25 next")
require("TVOV24  Real cast/state synchronization [implemented]" in rules, "working rules must mark TVOV24 implemented")
require("TVOV25  Domain truth and persistence convergence [next]" in rules, "working rules must mark TVOV25 next")
require("TVOV00 through TVOV24" in rules, "working rules must record current TV chain")
for token in ("explicit", "receipt", "reconnect", "STOP_CAST", "TVOV25"):
    require(token.lower() in notes.lower(), f"TVOV24 implementation notes missing {token}")
for token in ("canonical", "authenticated", "serialized", "pending", "Cast SDK"):
    require(token.lower() in quality.lower(), f"TVOV24 quality contract missing {token}")
require("TVOV24" in readme and "cast" in readme.lower(), "README missing TVOV24 summary")

if FAILURES:
    print("TVOV24_CAST_STATE_SYNCHRONIZATION: FAIL")
    for failure in FAILURES:
        print(f"- {failure}")
    sys.exit(1)
print("TVOV24_CAST_STATE_SYNCHRONIZATION: PASS")
