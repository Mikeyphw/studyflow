#!/usr/bin/env python3
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
checks={
 "persistence":("desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopReviewPersistence.kt",[
   "reviews-v1.properties","ATOMIC_MOVE","DesktopReviewSnapshot"]),
 "repository":("desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/InMemoryStudyReviewRepository.kt",[
   "completeReviewAndScheduleSuccessor","recordWrongAnswerAndEnsureReview","purgePlan","ReviewDuePolicy"]),
 "services":("desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopStudyFlowServices.kt",[
   "ManageStudyReview","attachWrongAnswerSink","reviewWorkspaceController"]),
 "ui":("desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopReviewScreen.kt",[
   "Flashcards and SRS","Error Notebook","Grade due 4/5"]),
 "test":("desktopApp/src/test/kotlin/com/mikeyphw/studyflow/desktop/DesktopReviewWorkspacePersistenceTest.kt",[
   "reviewFlashcardAndWrongAnswerEvidencePersistAcrossRestart","reviews-v1.properties"]),
}
for label,(name,tokens) in checks.items():
 text=(ROOT/name).read_text(); missing=[x for x in tokens if x not in text]
 if missing: raise SystemExit(f"{label} missing: {missing}")
if "fun reviewFlashcardAndWrongAnswerEvidencePersistAcrossRestart() = runBlocking" in (ROOT/checks["test"][0]).read_text():
 raise SystemExit("DOV14 JUnit test must use a block body")
print("DESKTOP_DOV14_REVIEWS_SRS_ERROR_NOTEBOOK: PASS")
