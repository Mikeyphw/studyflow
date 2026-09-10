#!/usr/bin/env python3
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
checks = {
    "controller": ("desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopWritingWorkspaceController.kt", [
        "WritingSessionNoteCodec", "WritingWorkspaceAnalytics", "saveWritingDraft", "completeWriting",
    ]),
    "screen": ("desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopWritingWorkspaceScreen.kt", [
        "Writing Workspace", "0–100 writing rubric", "Completion checklist", "Writing history",
    ]),
    "services": ("desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopStudyFlowServices.kt", [
        "writingWorkspaceController = DesktopWritingWorkspaceController",
    ]),
    "route": ("desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopAppState.kt", [
        "WRITING(\"Writing\")", "completeWritingSession", "WritingRubric", "WritingCompletionChecklist",
    ]),
    "test": ("desktopApp/src/test/kotlin/com/mikeyphw/studyflow/desktop/DesktopWritingWorkspacePersistenceTest.kt", [
        "writingDraftRubricAndHistoryPersistAcrossRestart", "workspace-v1.properties", "assertEquals(85",
    ]),
}
for label, (name, tokens) in checks.items():
    text = (ROOT / name).read_text()
    missing = [token for token in tokens if token not in text]
    if missing:
        raise SystemExit(f"{label} missing: {missing}")
if "fun writingDraftRubricAndHistoryPersistAcrossRestart() = runBlocking" in (ROOT / checks["test"][0]).read_text():
    raise SystemExit("DOV16 JUnit test must use a block body")
cap = (ROOT / "docs/product/CROSS_PLATFORM_CAPABILITY_MATRIX.md").read_text()
if "| Writing Workspace | FULL | FULL | FULL | HANDOFF | HANDOFF |" not in cap:
    raise SystemExit("Desktop Writing Workspace capability is not FULL")
build = (ROOT / "build.gradle.kts").read_text()
if 'tasks.register<Exec>("verifyDesktopDov16WritingWorkspace")' not in build:
    raise SystemExit("DOV16 root Gradle task is not registered")
devtool = (ROOT / ".devtool.toml").read_text()
if '"verifyDesktopDov16WritingWorkspace"' not in devtool:
    raise SystemExit("DOV16 Devtool target is not wired")
print("DESKTOP_DOV16_WRITING_WORKSPACE: PASS")
