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

controller = require_tokens(
    "app/src/main/java/com/mikeyphw/studyflow/tvcontrol/StudyFlowTvPhoneController.kt",
    'object StudyFlowTvPhoneControllerContract',
    'overlay = "TVOV23"',
    'class StudyFlowTvPhoneController(',
    'interface StudyFlowTvPhoneLinkGateway',
    'class DefaultStudyFlowTvPhoneLinkGateway(',
    'class StudyFlowTvPhoneControllerState' if False else 'data class StudyFlowTvPhoneControllerState',
    'StudyFlowTvReceiverDiscovery.Listener',
    'gateway.savedCredential()',
    'gateway.startDiscovery(this)',
    'gateway.pairingInfo(endpoint)',
    'gateway.pair(endpoint, normalizedCode, phoneDeviceName.trim())',
    'gateway.sendCommand(endpoint, command)',
    'gateway.fetchContinuity(endpoint)',
    'gateway.revoke(endpoint)',
    'gateway.clearSavedSession()',
    'requiresUserPresence = true',
    'presentationCastSynchronizationDeferredToTvov24 = false',
)
for action in (
    'StudyFlowTvControlAction.OPEN', 'StudyFlowTvControlAction.START', 'StudyFlowTvControlAction.PAUSE',
    'StudyFlowTvControlAction.RESUME', 'StudyFlowTvControlAction.NEXT', 'StudyFlowTvControlAction.PREVIOUS',
    'StudyFlowTvControlAction.SELECT_ANSWER', 'StudyFlowTvControlAction.SHOW_EXPLANATION',
    'StudyFlowTvControlAction.MARK_FOR_REVIEW', 'StudyFlowTvControlAction.SUBMIT',
    'StudyFlowTvControlAction.REVIEW_RESULTS', 'StudyFlowTvControlAction.COMPLETE',
    'StudyFlowTvControlAction.BACK', 'StudyFlowTvControlAction.STOP_CAST', 'StudyFlowTvControlAction.REVOKE_PAIRING',
):
    require(action in controller, f"phone controller contract missing action {action}")
for forbidden in ('CastContext', 'MediaRouter', 'RoomDatabase', '@Dao', 'ServerSocket('):
    require(forbidden not in controller, f"phone controller introduced forbidden/duplicate stack: {forbidden}")

screen = require_tokens(
    "app/src/main/java/com/mikeyphw/studyflow/tvcontrol/StudyFlowTvPhoneControllerScreen.kt",
    'StudyFlowTvPhoneControllerScreen(',
    'rememberLauncherForActivityResult',
    'ActivityResultContracts.RequestPermission()',
    'StudyFlowTvLocalNetworkPermission.permission',
    'controller.startDiscovery()',
    'controller.selectReceiver(endpoint)',
    'controller::checkPairing',
    'controller.pair(pairingCode, phoneName)',
    'controller::refreshContinuity',
    'controller::revoke',
    'controller::forgetSavedSession',
    'StudyFlowTvSurface.entries.forEach',
    'StudyFlowTvControlAction.SELECT_ANSWER',
    'StudyFlowTvControlAction.SHOW_EXPLANATION',
    'StudyFlowTvControlAction.MARK_FOR_REVIEW',
    'StudyFlowTvControlAction.SUBMIT',
    'StudyFlowTvControlAction.REVIEW_RESULTS',
    'StudyFlowTvControlAction.BACK',
    'tv_controller_cast_title',
    'controller.startCast(defaultPlanId)',
    'controller::stopCast',
)
require('tv_controller_cast_deferred' not in screen, "TVOV24 must retire the TVOV23 cast-deferred notice")

settings = require_tokens(
    "feature/settings/src/main/java/com/mikeyphw/studyflow/feature/settings/SettingsScreen.kt",
    'onOpenTvController: () -> Unit = {}',
    'tv_controller_settings_title',
    'Button(onClick = onOpenTvController)',
)
for forbidden in ('StudyFlowTvLocalTransportClient', 'StudyFlowTvReceiverDiscovery', 'StudyFlowTvControlCommand'):
    require(forbidden not in settings, f"feature:settings must stay transport-agnostic; found {forbidden}")

routes = require_tokens(
    "core/navigation/src/main/kotlin/com/mikeyphw/studyflow/core/navigation/StudyFlowRoutes.kt",
    'data object TvControllerRoute',
)
link_source = require_tokens(
    "core/tvlink/src/main/kotlin/com/mikeyphw/studyflow/core/tvlink/StudyFlowTvLocalLink.kt",
    'class StudyFlowTvReceiverDiscovery',
    'listener = null',
)

app_nav = require_tokens(
    "app/src/main/java/com/mikeyphw/studyflow/StudyFlowApp.kt",
    'import com.mikeyphw.studyflow.core.navigation.TvControllerRoute',
    'import com.mikeyphw.studyflow.tvcontrol.StudyFlowTvPhoneControllerScreen',
    'onOpenTvController = { navController.navigate(TvControllerRoute) }',
    'composable<TvControllerRoute>',
    'discovery = container.tvReceiverDiscovery',
    'client = container.tvLinkClient',
    'presentationSource = container.tvPresentationSource',
    'defaultPlanId = preferences.defaultStudyPlanId',
)

require_tokens(
    "app/src/test/java/com/mikeyphw/studyflow/tvcontrol/StudyFlowTvPhoneControllerContractTest.kt",
    'controllerCoversEveryTvDestinationAndRequiredAction',
    'commandFactoryPreservesSurfaceOptionAndUserPresence',
    'persistedCredentialSelectsMatchingRediscoveredTv',
)
require_tokens(
    "feature/settings/src/test/java/com/mikeyphw/studyflow/feature/settings/SettingsContractTest.kt",
    'settings exposes production tv controller entry without tv dependencies',
)

build_contract = require_tokens(
    "app-tv/src/main/java/com/mikeyphw/studyflow/tv/StudyFlowTvBuildContract.kt",
    'currentTvOverlay = "TVOV24"',
    'packageVersionCode = 24',
    'phoneControllerUiStillDeferredToTvov23 = false',
    'object StudyFlowTvPhoneControllerBuildContract',
    'phoneControllerIntroduced = true',
    'productionDiscoveryClientConsumed = true',
    'allTvDestinationsControllable = true',
    'livePresentationSynchronizationDeferredToTvov24 = false',
)
require_tokens(
    "app-tv/src/test/java/com/mikeyphw/studyflow/tv/StudyFlowTvBuildContractTest.kt",
    'phoneControllerIsProductionWiredFromSettings',
    'assertEquals("TVOV24", StudyFlowTvBuildContract.currentTvOverlay)',
    'assertEquals(24, StudyFlowTvPackagingBuildContract.packageVersionCode)',
)

tv_build = read("app-tv/build.gradle.kts")
require('versionCode = 24' in tv_build and 'versionName = "0.1.0-tv24"' in tv_build, "TV package must retain the current TVOV24 identity")
for forbidden in (':app:', ':feature:', ':core:data', ':core:database'):
    require(forbidden not in tv_build, f"TV APK dependency boundary regressed: {forbidden}")

root_gradle = read("build.gradle.kts")
devtool = read(".devtool.toml")
require('tasks.register<Exec>("verifyStudyFlowTvPhoneController")' in root_gradle, "missing TVOV23 Gradle verifier task")
require(devtool.count('"verifyStudyFlowTvPhoneController"') >= 8, "studyflow_tv must wire TVOV23 source verifier across tasks/phases")
with (ROOT / ".devtool.toml").open("rb") as fh:
    config = tomllib.load(fh)
tv_target = config.get("targets", {}).get("studyflow_tv", {})
flat = repr(tv_target.get("tasks", {})) + repr(tv_target.get("validation", {}).get("phases", {}))
for forbidden in (":app:", ":feature:", ":core:data:", ":core:database:"):
    require(forbidden not in flat, f"studyflow_tv target isolation regressed with {forbidden}")

roadmap = read("ANDROID_TV_ROADMAP.md")
rules = read("TV_ANDROID_TV_FULL_ROADMAP_AND_WORKING_RULES.md")
readme = read("README.md")
notes = read("TVOV23_ANDROID_PHONE_TV_CONTROLLER.md")
quality = read("docs/quality/TVOV23_ANDROID_PHONE_TV_CONTROLLER_CONTRACTS.md")
require("TVOV23  Android phone TV controller [implemented]" in roadmap, "roadmap must mark TVOV23 implemented")
require("TVOV24  Real cast/state synchronization [implemented]" in roadmap, "roadmap must mark TVOV24 implemented")
require("TVOV23  Android phone TV controller [implemented]" in rules, "working rules must mark TVOV23 implemented")
require("TVOV24  Real cast/state synchronization [implemented]" in rules, "working rules must mark TVOV24 implemented")
require("TVOV00 through TVOV24" in rules, "working rules must record current TV chain")
for token in ("Settings", "six-digit", "persisted", "all ten", "TVOV24"):
    require(token.lower() in notes.lower(), f"TVOV23 implementation notes missing {token}")
for token in ("receiver selection", "user-present", "revoke", "Cast SDK", "TVOV24"):
    require(token.lower() in quality.lower(), f"TVOV23 quality contract missing {token}")
require("TVOV23" in readme and "phone controller" in readme.lower(), "README missing TVOV23 summary")

if FAILURES:
    print("TVOV23_PHONE_CONTROLLER: FAIL")
    for failure in FAILURES:
        print(f"- {failure}")
    sys.exit(1)
print("TVOV23_PHONE_CONTROLLER: PASS")
