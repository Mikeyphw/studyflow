from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]

REQUIRED = {
    "core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowAttentionFocusPolicy.kt": [
        "StudyFlowAttentionFocusProfile", "StudyFlowAttentionQuietWindow", "StudyFlowAttentionPolicyOutcome",
        "DEFER_UNTIL_QUIET_END", "SILENT_DELIVERY", "COMPACT_ONLY", "ROUTE_TO_PHONE",
        "ESCALATE_MIRROR", "SUPPRESS_DUPLICATE", "REQUIRE_USER_ACTION", "ZoneId",
    ],
    "core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowAttention.kt": [
        "policyAudit", "recordPolicyDecision", "StudyFlowAttentionPolicyAudit",
    ],
    "core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowAttentionDeliveryRouter.kt": [
        "routeWithPolicy", "policyOutcome", "requestedMirrorCount", "WIDGET_OR_BADGE",
    ],
    "core/link/src/test/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowAttentionFocusPolicyTest.kt": [
        "overnightQuietHoursDeferToLocalQuietEnd", "minimalTvRoutesToPhoneRatherThanPretendingTvBackgroundDelivery",
    ],
    "core/link/src/test/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowAttentionPolicyRouterTest.kt": [
        "sleepProfileDefersWithoutLosingCanonicalOccurrence", "overdueReviewEscalationRemainsBounded",
    ],
    "app/src/main/java/com/mikeyphw/studyflow/reminders/AndroidStudyFlowAttentionFocusPolicyStore.kt": [
        "StudyFlowAttentionFocusPolicy", "defaultAttentionEscalationPolicies", "TimeZone.getDefault",
    ],
    "app/src/main/java/com/mikeyphw/studyflow/reminders/StudyReminderDeliveryCoordinator.kt": [
        "StudyFlowAttentionFocusPolicyEngine.evaluate", "DEFER_UNTIL_QUIET_END", "decision.deliverSilently",
        "recordPolicyDecision", "setSilent(silent)",
    ],
    "feature/reminders/src/main/java/com/mikeyphw/studyflow/feature/reminders/StudyRemindersScreen.kt": [
        "attentionFocusPolicy", "att10_focus_policy_title", "StudyFlowAttentionFocusProfile", "StudyFlowAttentionEscalationMode",
    ],
    "app/src/test/java/com/mikeyphw/studyflow/reminders/AndroidStudyFlowAttentionFocusPolicyContractTest.kt": [
        "AndroidStudyFlowAttentionFocusPolicyStore", "DEFER_UNTIL_QUIET_END", "recordPolicyDecision",
    ],
    "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopAttentionPreferences.kt": [
        "focusPolicy", "saveFocusPolicy", "StudyFlowAttentionQuietWindow", "defaultAttentionEscalationPolicies",
    ],
    "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopAttentionRuntime.kt": [
        "StudyFlowAttentionFocusPolicyEngine.evaluate", "lastPolicyOutcome", "cycleFocusProfile", "recordPolicyDecision",
    ],
    "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopAnalyticsSearchSettingsScreens.kt": [
        "ATT10 focus policy", "Cycle focus profile", "Disable quiet hours",
    ],
    "desktopApp/src/test/kotlin/com/mikeyphw/studyflow/desktop/DesktopAttentionPreferencesTest.kt": [
        "persists ATT10 focus profile quiet hours and mirror cap", "America/Sao_Paulo",
    ],
    "app-tv/src/main/java/com/mikeyphw/studyflow/tv/attention/StudyFlowTvAttentionDeliveryRouterBridge.kt": [
        "focusProjection", "controlsReadOnly = true", "remoteControlRequired = true", "ANDROID_TV",
    ],
    "app-tv/src/test/java/com/mikeyphw/studyflow/tv/attention/StudyFlowTvAttentionFocusPolicyTest.kt": [
        "minimalTvPolicyIsReadOnlyAndRoutesAwayFromTv", "ROUTE_TO_PHONE",
    ],
    "web/src/attention_focus_policy.ts": [
        "StudyFlowAttentionFocusProfile", "defer_until_quiet_end", "nextQuietEnd", "Intl.DateTimeFormat",
        "DEFAULT_CATEGORY_POLICIES", "mirror_if_unhandled", "require_user_action",
    ],
    "web/src/attention_router.ts": [
        "routeStudyFlowAttentionDeliveryWithPolicy", "policyOutcome", "requestedMirrorCount",
    ],
    "web/src/attention.ts": [
        "focusPolicy", "recordPolicyDecision", "evaluateWebAttentionFocusPolicy", "routeStudyFlowAttentionDeliveryWithPolicy",
    ],
    "web/src/tv_attention.ts": [
        "focusControlsReadOnly", "closedBrowserDelivery: false", "remote-safe-read-only",
    ],
    "web/src/react/App.tsx": [
        "ATT10 quiet hours and focus policy", "Per-category escalation", "closed browser is not treated as a guaranteed alarm owner",
    ],
    "web/service-worker.js": ["./runtime/attention_focus_policy.js"],
    "web/tests/test_att10_focus_policy.py": ["ATT10 Web focus policy tests passed", "closed browser"],
    "docs/product/QUIET_HOURS_FOCUS_ESCALATION.md": ["ATT01 ledger", "ATT07", "foreground-only"],
    "docs/product/CROSS_PLATFORM_CAPABILITY_MATRIX.md": ["ATT10 quiet hours / focus profiles / escalation", "READ_ONLY (foreground projection; phone fallback)"],
    "ATT10_QUIET_HOURS_FOCUS_PROFILES_ESCALATION_POLICY.md": [
        "verifyAtt10QuietHoursFocusProfilesEscalationPolicy", "No second attention truth store", "runtime/dist",
    ],
    "README.md": ["ATT10 — Quiet Hours / Focus Profiles / Escalation Policy", "ATT01 ledger", "ATT07"],
    "CHANGELOG.md": ["ATT10 — Quiet Hours / Focus Profiles / Escalation Policy", "verifyAtt10QuietHoursFocusProfilesEscalationPolicy"],
    "build.gradle.kts": ["verifyAtt10QuietHoursFocusProfilesEscalationPolicy", "verifyAtt10WebFocusPolicyTests"],
    ".devtool.toml": ["verifyAtt10QuietHoursFocusProfilesEscalationPolicy"],
}


def require(path: str, *needles: str) -> str:
    candidate = ROOT / path
    if not candidate.exists():
        raise AssertionError(f"Missing required ATT10 file: {path}")
    text = candidate.read_text(encoding="utf-8")
    for needle in needles:
        if needle not in text:
            raise AssertionError(f"{path} missing ATT10 contract text: {needle}")
    return text


for path, needles in REQUIRED.items():
    require(path, *needles)

# ATT10 must not add a permission as a substitute for real platform capability.
manifest = require("app/src/main/AndroidManifest.xml")
if "ATT10" in manifest or "attention_focus" in manifest.lower():
    raise AssertionError("ATT10 must not add manifest permissions/components solely for focus policy")

# Web runtime and packaged dist must match for every ATT10-touched runtime module.
for relative in (
    "attention_focus_policy.js",
    "attention_router.js",
    "attention.js",
    "tv_attention.js",
    "sflp_link.js",
    "react/App.js",
):
    runtime = require(f"web/runtime/{relative}")
    dist_runtime = require(f"web/dist/runtime/{relative}")
    if runtime != dist_runtime:
        raise AssertionError(f"ATT10 runtime/dist parity failed for {relative}")

source_sw = require("web/service-worker.js", "./runtime/attention_focus_policy.js")
dist_sw = require("web/dist/service-worker.js", "./runtime/attention_focus_policy.js")
if "runtime/attention_focus_policy.js" not in require("web/dist/release-manifest.json"):
    raise AssertionError("ATT10 focus runtime missing from Web release manifest")
if "foreground-only" not in require("web/src/tv_attention.ts"):
    raise AssertionError("ATT10 must preserve truthful foreground-only Web TV delivery")

# The new Web evaluator/router and existing ATT06/ATT07 behavior are checked together.
result = subprocess.run(
    [
        "python3", "-m", "unittest",
        "tests/test_att10_focus_policy.py",
        "tests/test_att06_web_tv_attention.py",
        "tests/test_att07_attention_router.py",
    ],
    cwd=ROOT / "web",
    text=True,
    stdout=subprocess.PIPE,
    stderr=subprocess.STDOUT,
    check=False,
)
if result.returncode != 0:
    raise AssertionError(result.stdout)

print("ATT10 quiet hours / focus profiles / escalation policy verification passed")
