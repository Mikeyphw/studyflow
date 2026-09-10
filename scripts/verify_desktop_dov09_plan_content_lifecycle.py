#!/usr/bin/env python3
from pathlib import Path
import re
root = Path(__file__).resolve().parents[1]
failures=[]
workspace_text=(root / "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopWorkspaceStore.kt").read_text()
schema_match = re.search(r"(?:CURRENT_SCHEMA_VERSION|SCHEMA_VERSION)\s*(?::\s*Int)?\s*=\s*(\d+)", workspace_text)
if not schema_match or int(schema_match.group(1)) < 2:
    failures.append("DesktopWorkspaceStore schema must be >= 2")
def req(path,*tokens):
    text=(root/path).read_text()
    for token in tokens:
        if token not in text: failures.append(f"{path}: missing {token!r}")
req("desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopStudyContentRepository.kt",
    "StudyContentRepository", "reorderSubjects", "moveTopic", "bulkUpdateTopics", "importSyllabus",
    "recalculateProgress", "descendantsOf", "requireMutablePlan", "purgePlan")
req("desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopPlanContentController.kt",
    "createPlan", "createSubject", "createTopic", "deleteSelectedPermanently")
req("desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopPlanContentScreen.kt",
    "Study Plans", "Subjects", "Topics")
req("desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopWorkspaceStore.kt",
"version in 1..SCHEMA_VERSION", "updateContent")
req("desktopApp/src/test/kotlin/com/mikeyphw/studyflow/desktop/DesktopPlanContentLifecycleTest.kt",
    "PersistsAcrossRestart", "deletingSubjectCascades", "archivedPlanRejectsContentMutation")

plan_test = (
    root / "desktopApp/src/test/kotlin/com/mikeyphw/studyflow/desktop/DesktopPlanContentLifecycleTest.kt"
).read_text()
if re.search(r"@Test\s*\n\s*fun\s+\w+\([^)]*\)\s*=\s*runBlocking", plan_test):
    failures.append("DesktopPlanContentLifecycleTest must use block-bodied JUnit tests around runBlocking")
req(".devtool.toml", '"verifyDesktopDov09PlanContentLifecycle"')
if failures: raise SystemExit("DOV09 lifecycle violations:\n- " + "\n- ".join(failures))
print("DESKTOP_DOV09_PLAN_CONTENT_LIFECYCLE: PASS")
