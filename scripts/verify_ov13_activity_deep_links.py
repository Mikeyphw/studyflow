#!/usr/bin/env python3
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def text(p): return (ROOT/p).read_text(encoding="utf-8")
def require(cond,msg):
    if not cond: raise SystemExit("OV13 verify failed: "+msg)
planner=text("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/StudyActivityLaunchPlanner.kt")
today=text("feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/TodayWorkspaceScreen.kt")
practice=text("feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/PracticeWorkspaceScreen.kt")
routes=text("core/navigation/src/main/kotlin/com/mikeyphw/studyflow/core/navigation/StudyFlowRoutes.kt")
nav=text("app/src/main/java/com/mikeyphw/studyflow/StudyFlowV2Navigation.kt")
for token in ["MOCK_EXAM_STUDIO", "WRITING_WORKSPACE", "QUESTIONS", "REVIEWS", "FOCUS_SESSION", "matchesQuestion", "matchesReview"]:
    require(token in planner, f"planner missing {token}")
require("StudyActivityLaunchPlanner.plan(activity)" in today, "Today does not use canonical activity launch planner")
require("onOpenQuestions(intent.subjectId, intent.topicId)" in today, "question deep link missing typed context")
require("onOpenReviews(intent.subjectId, intent.topicId)" in today, "review deep link missing typed context")
require("openMockStudio = true" in nav, "mock activity does not request Mock Exam Studio")
for token in ["val subjectId: String? = null", "val topicId: String? = null", "val openMockStudio: Boolean = false"]:
    require(token in routes, f"Practice route missing {token}")
require("StudyActivityLaunchPlanner.matchesQuestion" in practice, "Practice does not filter questions by activity context")
require("StudyActivityLaunchPlanner.matchesReview" in practice, "Practice does not filter reviews by activity context")
require("rememberSaveable(openMockStudio)" in practice, "Mock Studio does not honor deep-link open request")
print("OV13 activity deep-link source contract: PASS")
