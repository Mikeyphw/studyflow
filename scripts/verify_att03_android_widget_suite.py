#!/usr/bin/env python3
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
errors = []

def read(path: str) -> str:
    p = ROOT / path
    if not p.exists():
        errors.append(f"missing {path}")
        return ""
    return p.read_text(encoding="utf-8")

def require(path: str, *tokens: str):
    text = read(path)
    for token in tokens:
        if token not in text:
            errors.append(f"{path}: missing {token!r}")

def forbid(path: str, *tokens: str):
    text = read(path)
    for token in tokens:
        if token in text:
            errors.append(f"{path}: forbidden {token!r}")

models = "app/src/main/java/com/mikeyphw/studyflow/widget/StudyFlowWidgetModels.kt"
provider = "app/src/main/java/com/mikeyphw/studyflow/widget/StudyFlowTodayWidgetProvider.kt"
coordinator = "app/src/main/java/com/mikeyphw/studyflow/reminders/StudyFlowWidgetUpdateCoordinator.kt"
config = "app/src/main/java/com/mikeyphw/studyflow/widget/StudyFlowWidgetConfigurationStore.kt"
config_activity = "app/src/main/java/com/mikeyphw/studyflow/widget/StudyFlowWidgetConfigureActivity.kt"
freshness = "app/src/main/java/com/mikeyphw/studyflow/widget/StudyFlowWidgetFreshnessScheduler.kt"
actions = "app/src/main/java/com/mikeyphw/studyflow/widget/StudyFlowWidgetActionReceiver.kt"
manifest = "app/src/main/AndroidManifest.xml"
info = "app/src/main/res/xml/studyflow_today_widget_info.xml"

require(models, "TODAY", "NEXT", "REVIEWS", "SESSION", "COMPACT", "nextRefreshEpochMillis", "sessionMinuteTick")
require(config, "studyflow_widget_configuration_v2", "SchemaVersion = 2", "fun restore", "showReviewCount")
require(config_activity, "EXTRA_APPWIDGET_ID", "StudyFlowWidgetMode.entries", "widget_config_plan", "RESULT_OK")
require(freshness, "AlarmManager.RTC_WAKEUP", "setWindow", "StudyFlowWidgetRefreshReceiver", "nextScheduledRefresh")
require(actions, "START_NEXT", "TOGGLE_SESSION", "startFromActivity", "manageStudySession.pause", "manageStudySession.resume")
require(provider, "onAppWidgetOptionsChanged", "onRestored", "renderToday", "renderNext", "renderReviews", "renderSession", "renderCompact", "setContentDescription")
require(coordinator, "StudyFlowWidgetConfigurationStore", "StudyFlowWidgetSnapshotProjector", "StudyFlowWidgetFreshnessScheduler.schedule", "modeCounts")
forbid(coordinator, "POST_NOTIFICATIONS", "notificationPermissionGranted")
require(manifest, ".widget.StudyFlowWidgetActionReceiver", ".widget.StudyFlowWidgetRefreshReceiver", ".widget.StudyFlowWidgetConfigureActivity")
require(info, "previewLayout", "reconfigurable|configuration_optional", "StudyFlowWidgetConfigureActivity", 'android:updatePeriodMillis="0"')
require("app/src/main/java/com/mikeyphw/studyflow/StudyFlowApplication.kt", "startWidgetInvalidationObserver", "activePlans.flatMap", "StudyFlowWidgetUpdateCoordinator.refreshAll")
require("app/src/main/java/com/mikeyphw/studyflow/StudyFlowDebugProbeRegistry.kt", "configured_instances", "next_refresh_epoch_millis", "modes")
require("app/src/test/java/com/mikeyphw/studyflow/widget/StudyFlowWidgetSnapshotProjectorAtt03Test.kt", "MinuteFreshness", "nextRefreshEpochMillis")
require("app/src/androidTest/java/com/mikeyphw/studyflow/widget/StudyFlowWidgetConfigurationStoreAtt03Test.kt", "restore", "StudyFlowWidgetMode.SESSION")
require("app/src/test/java/com/mikeyphw/studyflow/widget/StudyFlowWidgetAtt03SourceContractTest.kt", "freshness", "configuration")
require("ATT03_ANDROID_WIDGET_SUITE_2_0.md", "five widget modes", "notification permission", "Migration")
require("docs/product/ANDROID_WIDGET_SUITE.md", "Today", "Next", "Reviews", "Session", "Compact")
require("build.gradle.kts", "verifyAtt03AndroidWidgetContracts", "verifyAtt03AndroidWidgetSuite")
require(".devtool.toml", '"verifyAttentionRoadmapPostsealCompletion"')

for xml in [manifest, info, "app/src/main/res/layout/widget_configure.xml", "app/src/main/res/layout/widget_today.xml", "app/src/main/res/layout/widget_today_preview.xml", "app/src/main/res/values/strings.xml", "app/src/main/res/values-pt-rBR/strings.xml"]:
    try:
        ET.parse(ROOT / xml)
    except Exception as exc:
        errors.append(f"{xml}: XML parse failed: {exc}")

if errors:
    print("ATT03 Android Widget Suite verification FAILED")
    for error in errors:
        print(f"- {error}")
    sys.exit(1)
print("ATT03 Android Widget Suite verification PASSED")
