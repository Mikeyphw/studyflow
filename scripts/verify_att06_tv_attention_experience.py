#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
errors = []

def read(path: str) -> str:
    p = ROOT / path
    if not p.is_file():
        errors.append(f"missing {path}")
        return ""
    return p.read_text(encoding="utf-8")

def require(path: str, *tokens: str) -> None:
    text = read(path)
    for token in tokens:
        if token not in text:
            errors.append(f"{path}: missing {token!r}")

require("app-tv/src/main/java/com/mikeyphw/studyflow/tv/attention/StudyFlowTvAttentionSurface.kt",
        "foregroundOnly", "backgroundDeliveryRequiresDesignatedNotifier", "doesNotClaimSystemNotifications",
        "doesNotClaimBackgroundAlarms", "MOVE_TO_PHONE", "routeFor", "DUE_REVIEW", "EXAM_COUNTDOWN")
require("app-tv/src/main/java/com/mikeyphw/studyflow/tv/attention/StudyFlowTvAttentionRuntime.kt",
        "StudyFlowTvAttentionRuntime", "StudyFlowTvNotificationMediaReceiver", "StudyFlowAttentionLedger",
        "transitionByOccurrenceId", "closedAppDeliveryTruth")
require("app-tv/src/main/java/com/mikeyphw/studyflow/tv/StudyFlowTvActivity.kt",
        "StudyFlowTvAttentionRuntime.persistent", "renderTvAttentionBannerIfNeeded", "tv_settings_action_attention")
require("app-tv/src/main/java/com/mikeyphw/studyflow/tv/settings/StudyFlowTvSettingsSurface.kt",
        "foregroundAttentionBannersVisible", "backgroundDeliveryIsNotClaimedOnTv", "designatedNotifierFallbackVisible", "OPEN_ATTENTION_STATUS")
require("app-tv/src/test/java/com/mikeyphw/studyflow/tv/attention/StudyFlowTvAttentionSurfaceTest.kt",
        "foregroundOnlyCapabilityDoesNotClaimTvBackgroundDelivery", "dueReviewBecomesDpadSafeReviewBanner")
require("app-tv/src/test/java/com/mikeyphw/studyflow/tv/attention/StudyFlowTvAttentionRuntimeTest.kt",
        "acceptedRemoteDeliveryCreatesForegroundBannerAndLedgerEntry", "deliveriesThatDisallowTvSurfacesAreAcknowledgedWithoutBanner")
require("web/src/tv_attention.ts",
        "StudyFlowWebTvAttentionRuntime", "foreground-only", "designated-notifier-fallback",
        "projectWebAttentionToTv", "webTvAttentionCapabilities", "move_to_phone")
require("web/src/tv.ts", "studyflowTvAttention", "studyflowTvAttentionFallback", "tv_attention")
require("web/runtime/tv_attention.js", "StudyFlowWebTvAttentionRuntime", "exports.webTvAttentionCapabilities")
require("web/dist/runtime/tv_attention.js", "StudyFlowWebTvAttentionRuntime", "exports.projectWebAttentionToTv")
require("web/tests/test_att06_web_tv_attention.py", "Att06WebTvAttentionTests", "foreground-only")
require("ATT06_TV_ATTENTION_EXPERIENCE.md", "verifyAtt06TvAttentionExperience", "foreground-only")
require("docs/product/TV_ATTENTION_EXPERIENCE.md", "foreground-first", "ATT07")
require("docs/product/CROSS_PLATFORM_CAPABILITY_MATRIX.md", "Android TV ATT06 foreground banners", "Web TV ATT06 foreground banners")
require("build.gradle.kts", "verifyAtt06TvAttentionContracts", "verifyAtt06TvAttentionExperience")
require(".devtool.toml", '"verifyAtt06TvAttentionExperience"')

if errors:
    print("ATT06 TV Attention Experience verification FAILED")
    for error in errors:
        print(f"- {error}")
    sys.exit(1)
print("ATT06 TV Attention Experience verification PASSED")
