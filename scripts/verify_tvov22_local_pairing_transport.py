#!/usr/bin/env python3
from __future__ import annotations

import sys
import tomllib
from pathlib import Path
import xml.etree.ElementTree as ET

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


settings = read("settings.gradle.kts")
require('' not in settings, "settings file contains terminal escape data")
require('' not in read(".devtool.toml"), "Devtool config contains terminal escape data")
require('' not in read("build.gradle.kts"), "root build file contains terminal escape data")
require('' not in read("app-tv/src/main/AndroidManifest.xml"), "TV manifest contains terminal escape data")
require('' not in read("app/src/main/AndroidManifest.xml"), "phone manifest contains terminal escape data")
require('":core:tvlink"' in settings, "settings.gradle.kts must include :core:tvlink")

link_build = require_tokens(
    "core/tvlink/build.gradle.kts",
    'id("studyflow.android.library")',
    'namespace = "com.mikeyphw.studyflow.core.tvlink"',
    'api(project(":core:tvcontrol"))',
    'testImplementation(libs.junit4)',
)
wire = require_tokens(
    "core/tvcontrol/src/main/kotlin/com/mikeyphw/studyflow/core/tvcontrol/StudyFlowTvWireProtocol.kt",
    'object StudyFlowTvWireProtocol',
    'const val serviceType = "_studyflow-tv._tcp."',
    'const val maxFrameBytes = 1_048_576',
    'const val controlSessionTtlSeconds = 86_400L',
    'const val maxPairingAttemptsPerOffer = 10',
    'localPairingDiscoveryAndTransportImplemented = true',
    'PAIRING_INFO', 'PAIR', 'COMMAND', 'PRESENTATION', 'CONTINUITY', 'REVOKE', 'PING',
    'data class StudyFlowTvPairingOffer',
    'data class StudyFlowTvPairingInfo',
    'data class StudyFlowTvSessionCredential',
    'data class StudyFlowTvWireRequest',
    'data class StudyFlowTvWireResponse',
    'class StudyFlowTvPairingEngine',
    'object StudyFlowTvWireCodec',
    'DataInputStream', 'DataOutputStream',
    'constantTimeEquals',
)
for forbidden in ("import android.", "RoomDatabase", "@Dao", "ViewGroup", "CastContext", "MediaRouter"):
    require(forbidden not in wire, f"pure :core:tvcontrol wire protocol leaked forbidden token: {forbidden}")

require_tokens(
    "core/tvcontrol/src/test/kotlin/com/mikeyphw/studyflow/core/tvcontrol/StudyFlowTvWireProtocolTest.kt",
    'pairingOfferIsSixDigitShortLivedAndCreatesExpiringCredential',
    'framedRequestRoundTripsWithoutDatabaseOrUiPayloads',
    'pairingInfoDoesNotExposePairingCodeThroughDiscoveryHandshake',
    'unauthenticatedPairingMetadataCarriesNoContinuityState',
)

link = require_tokens(
    "core/tvlink/src/main/kotlin/com/mikeyphw/studyflow/core/tvlink/StudyFlowTvLocalLink.kt",
    'object StudyFlowTvLocalLinkContract',
    'androidNsdDiscovery = true',
    'localTcpTransport = true',
    'class StudyFlowTvSessionStore',
    'class StudyFlowTvReceiverDiscovery',
    'class StudyFlowTvLocalTransportClient',
    'class StudyFlowTvLocalTransportHost',
    'NsdManager', 'ServerSocket', 'Socket',
    'StudyFlowTvWireCodec.writeRequest',
    'StudyFlowTvWireCodec.readResponse',
    'StudyFlowTvWireCodec.readRequest',
    'StudyFlowTvWireCodec.writeResponse',
    'StudyFlowTvWireProtocol.maxPairingAttemptsPerOffer',
    'sessionStore.save', 'sessionStore.clear',
    'StudyFlowTvControlAction.REVOKE_PAIRING',
    'delegate.applyRemoteCommand',
    'delegate.applyPresentationSnapshot',
)
for forbidden in ("import androidx.compose", "import androidx.tv", "import com.google.android.gms.cast", "import androidx.mediarouter", "RoomDatabase", "@Dao"):
    require(forbidden not in link + link_build, f":core:tvlink must stay lightweight; found {forbidden}")
require_tokens(
    "core/tvlink/src/test/kotlin/com/mikeyphw/studyflow/core/tvlink/StudyFlowTvLocalLinkContractTest.kt",
    'transportIsLocalExplicitAndDependencyLight',
)

phone_manifest = read("app/src/main/AndroidManifest.xml")
tv_manifest = read("app-tv/src/main/AndroidManifest.xml")
for rel, text in (("phone", phone_manifest), ("TV", tv_manifest)):
    for permission in ("android.permission.INTERNET", "android.permission.ACCESS_NETWORK_STATE", "android.permission.NEARBY_WIFI_DEVICES"):
        require(permission in text, f"{rel} manifest missing {permission}")
    try:
        ET.fromstring(text)
    except ET.ParseError as error:
        require(False, f"{rel} manifest XML invalid: {error}")

app_build = read("app/build.gradle.kts")
tv_build = read("app-tv/build.gradle.kts")
for text, label in ((app_build, "phone"), (tv_build, "TV")):
    require('implementation(project(":core:tvlink"))' in text, f"{label} app must depend on :core:tvlink")
require('versionCode = 24' in tv_build and 'versionName = "0.1.0-tv24"' in tv_build, "TVOV22 package version not installed")
for forbidden in (':core:data', ':core:database', ':feature:', 'androidx.tv', 'play-services-cast', 'androidx.mediarouter'):
    require(forbidden not in tv_build, f"TV dependency boundary regressed: {forbidden}")

app_container = require_tokens(
    "app/src/main/java/com/mikeyphw/studyflow/StudyFlowApplication.kt",
    'val tvReceiverDiscovery: StudyFlowTvReceiverDiscovery',
    'val tvLinkClient: StudyFlowTvLocalTransportClient',
    'override val tvReceiverDiscovery = StudyFlowTvReceiverDiscovery(application)',
    'override val tvLinkClient = StudyFlowTvLocalTransportClient(application)',
)
require('StudyFlowTvLocalTransportHost' not in app_container, "phone container must own client/discovery, not TV host")

# Pre-pair discovery/health traffic must never disclose the current StudyFlow surface/state.
pairing_branch = link.split('StudyFlowTvWireOperation.PAIRING_INFO ->', 1)[1].split('StudyFlowTvWireOperation.PAIR ->', 1)[0]
ping_line = next((line for line in link.splitlines() if 'StudyFlowTvWireOperation.PING ->' in line), '')
require('continuity = safeContinuity()' not in pairing_branch, "unauthenticated PAIRING_INFO must not expose TV continuity")
require('continuity = safeContinuity()' not in ping_line, "unauthenticated PING must not expose TV continuity")

activity = require_tokens(
    "app-tv/src/main/java/com/mikeyphw/studyflow/tv/StudyFlowTvActivity.kt",
    'StudyFlowTvLocalTransportHost(',
    'override fun onStart()', 'localTransportHost.start()',
    'override fun onStop()', 'localTransportHost.stop()',
    'override fun onRequestPermissionsResult(',
    'private fun bindTransportSession(session: StudyFlowTvControlSession)',
    'private fun beginPairingFromTv()',
    'private fun revokePairingFromTv()',
    'override fun applyRemoteCommand(',
    'this@StudyFlowTvActivity.applyPairedPhoneCommand',
    'this@StudyFlowTvActivity.applyPresentationSnapshot',
    'tv_settings_pairing_code_format',
)
reducer = require_tokens(
    "app-tv/src/main/java/com/mikeyphw/studyflow/tv/control/StudyFlowTvControlStateReducer.kt",
    'transportDeferred = false',
    'fun bindSession(',
)
protocol = read("core/tvcontrol/src/main/kotlin/com/mikeyphw/studyflow/core/tvcontrol/StudyFlowTvControlProtocol.kt")
presentation = read("core/tvcontrol/src/main/kotlin/com/mikeyphw/studyflow/core/tvcontrol/StudyFlowTvPresentation.kt")
projector = read("app/src/main/java/com/mikeyphw/studyflow/tvcontrol/StudyFlowTvPresentationProjector.kt")
require('transportsDeferredUntilImplementation = false' in protocol, "shared control protocol must mark local transport implemented")
require('transportStillDeferred = false' in presentation, "presentation protocol must mark local transport implemented")
require('transportDeferred = false' in projector, "phone projector must mark local transport implemented")

contract = require_tokens(
    "app-tv/src/main/java/com/mikeyphw/studyflow/tv/StudyFlowTvBuildContract.kt",
    'currentTvOverlay = "TVOV24"',
    'castTransportDeferred = false',
    'object StudyFlowTvLocalLinkBuildContract',
    'localPairingDiscoveryTransportIntroduced = true',
    'sharedAndroidLinkModule = ":core:tvlink"',
    'phoneControllerUiStillDeferredToTvov23 = false',
    'packageVersionCode = 24',
)
require_tokens(
    "app-tv/src/test/java/com/mikeyphw/studyflow/tv/StudyFlowTvBuildContractTest.kt",
    'localPairingDiscoveryAndTransportAreProductionWired',
)

root_gradle = read("build.gradle.kts")
devtool_text = read(".devtool.toml")
require('tasks.register<Exec>("verifyStudyFlowTvLocalPairingTransport")' in root_gradle, "missing TVOV22 Gradle verifier task")
require(devtool_text.count('"verifyStudyFlowTvLocalPairingTransport"') >= 8, "studyflow_tv must wire TVOV22 verifier across tasks/phases")
for task in (":core:tvlink:compileDebugKotlin", ":core:tvlink:testDebugUnitTest", ":core:tvlink:lintDebug"):
    require(task in devtool_text, f"studyflow_tv Devtool target missing {task}")

with (ROOT / ".devtool.toml").open("rb") as fh:
    config = tomllib.load(fh)
tv_target = config.get("targets", {}).get("studyflow_tv", {})
flat = repr(tv_target.get("tasks", {})) + repr(tv_target.get("validation", {}).get("phases", {}))
for forbidden in (":app:", ":feature:", ":core:data:", ":core:database:"):
    require(forbidden not in flat, f"studyflow_tv target leaked forbidden task prefix {forbidden}")

roadmap = read("ANDROID_TV_ROADMAP.md")
rules = read("TV_ANDROID_TV_FULL_ROADMAP_AND_WORKING_RULES.md")
readme = read("README.md")
quality = read("docs/quality/TVOV22_LOCAL_PAIRING_DISCOVERY_TRANSPORT_CONTRACTS.md")
notes = read("TVOV22_ANDROID_TV_LOCAL_PAIRING_DISCOVERY_TRANSPORT.md")
require("TVOV22  Local pairing, discovery, and transport [implemented]" in roadmap, "roadmap must mark TVOV22 implemented")
require("TVOV23  Android phone TV controller [implemented]" in roadmap, "roadmap must mark TVOV23 next")
require("TVOV22  Local pairing, discovery, and transport [implemented]" in rules, "working rules must mark TVOV22 implemented")
require("TVOV23  Android phone TV controller [implemented]" in rules, "working rules must mark TVOV23 next")
require("TVOV00 through TVOV24" in rules, "working rules must record TVOV00 through TVOV24")
for token in ("NSD", "six-digit", "nonce", "session secret", "TVOV23"):
    require(token.lower() in notes.lower(), f"TVOV22 implementation notes missing {token}")
for token in ("explicit", "revoked", "Cast SDK", "no raw database rows", "background"):
    require(token.lower() in quality.lower(), f"TVOV22 quality contract missing {token}")
require("TVOV22" in readme and "local pairing" in readme.lower(), "README missing TVOV22 summary")

if FAILURES:
    print("TVOV22_LOCAL_PAIRING_TRANSPORT: FAIL")
    for failure in FAILURES:
        print(f"- {failure}")
    sys.exit(1)
print("TVOV22_LOCAL_PAIRING_TRANSPORT: PASS")
