#!/usr/bin/env python3
from pathlib import Path
root = Path(__file__).resolve().parents[1]
failures=[]
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
    "SCHEMA_VERSION = 2", "version in 1..SCHEMA_VERSION", "updateContent")
req("desktopApp/src/test/kotlin/com/mikeyphw/studyflow/desktop/DesktopPlanContentLifecycleTest.kt",
    "PersistsAcrossRestart", "deletingSubjectCascades", "archivedPlanRejectsContentMutation")
req(".devtool.toml", '"verifyDesktopDov09PlanContentLifecycle"')
if failures: raise SystemExit("DOV09 lifecycle violations:\n- " + "\n- ".join(failures))
print("DESKTOP_DOV09_PLAN_CONTENT_LIFECYCLE: PASS")
