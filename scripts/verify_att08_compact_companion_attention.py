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

require(
    "core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowCompactCompanionAttention.kt",
    "StudyFlowCompactCompanionProfile",
    "StudyFlowCompactCompanionProjector",
    "StudyFlowCompactAttentionProjection",
    "Compact companions mirror ATT07 parent ownership",
    "suppressesCompactProjection",
    "sensitiveTextHidden",
)
require(
    "core/link/src/test/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowCompactCompanionAttentionTest.kt",
    "compactCompanionsMirrorParentRouteButNeverBecomeOwners",
    "disconnectedMutedAndStaleCompanionsAreSuppressed",
    "handledLedgerEntrySuppressesCompactReplay",
    "compactProjectionHidesSensitiveTextUnlessAllowed",
)
require(
    "app/src/main/java/com/mikeyphw/studyflow/reminders/AndroidCompactCompanionAttentionBridge.kt",
    "android-wearable-primary",
    "android-lock-screen-chip",
    "android-quick-tile",
    "projectPhoneOwnedCompanions",
)
require(
    "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopCompactCompanionAttentionBridge.kt",
    "desktop-mini-dashboard",
    "projectMiniDashboard",
)
require(
    "app-tv/src/main/java/com/mikeyphw/studyflow/tv/attention/StudyFlowTvCompactCompanionBridge.kt",
    "tv-remote-overlay",
    "projectForegroundOverlay",
)
require(
    "web/src/compact_attention.ts",
    "projectAttentionRouteToCompactCompanions",
    "projectWebAttentionToCompactCompanions",
    "defaultWebCompactCompanionProfiles",
    "compact companions suppress replay",
)
require("web/runtime/compact_attention.js", "exports.projectWebAttentionToCompactCompanions")
require("web/dist/runtime/compact_attention.js", "exports.projectAttentionRouteToCompactCompanions")
require("web/src/react/App.tsx", "Compact companion surface", "projectWebAttentionToCompactCompanions")
require("web/tests/test_att08_compact_attention.py", "Att08CompactAttentionTests")
require("web/tests/att08_compact_runner.mjs", "ATT08 Web compact companion attention tests passed")
require("ATT08_COMPACT_COMPANION_ATTENTION.md", "verifyAtt08CompactCompanionAttention", "phone-owned")
require("docs/product/COMPACT_COMPANION_ATTENTION.md", "phone-owned", "never become independent owners")
require("docs/product/CROSS_PLATFORM_CAPABILITY_MATRIX.md", "Compact companion attention", "ATT08 compact companion")
require("README.md", "ATT08 — Compact Companion Attention")
require("CHANGELOG.md", "ATT08 — Compact Companion Attention")
require("build.gradle.kts", "verifyAtt08CompactCompanionAttention")
require(".devtool.toml", '"verifyAttentionRoadmapPostsealCompletion"')

if errors:
    print("ATT08 Compact Companion Attention verification FAILED")
    for error in errors:
        print(f"- {error}")
    sys.exit(1)
print("ATT08 Compact Companion Attention verification PASSED")
print("- shared compact companion model + phone-owned projection")
print("- Android/Desktop/TV/Web compact bridge surfaces")
print("- Web runtime/dist parity + Notification Center compact section")
print("- tests/docs/Devtool gate present")
