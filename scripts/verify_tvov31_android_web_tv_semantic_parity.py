#!/usr/bin/env python3
from pathlib import Path
import subprocess, sys
R=Path(__file__).resolve().parents[1]; F=[]
def t(p): return (R/p).read_text()
def q(c,m):
    if not c:F.append(m)
fixture=R/"protocol/tv-parity/v1/semantic-fixtures.tsv"
q(fixture.is_file(),"common semantic fixture missing")
android=t("app-tv/src/test/java/com/mikeyphw/studyflow/tv/StudyFlowTvCommonSemanticFixtureTest.kt")
web=t("web/src/tv_semantic_parity.ts")
for x in ("StudyFlowTvMaterialViewer.reduce","StudyFlowTvProgressAnalytics.goalActions","StudyFlowTvReviewNotebook.nextErrorCategory","StudyFlowTvSearch.updateQuery"):
    q(x in android,"Android common-fixture consumer missing "+x)
for x in ("tvParityPage","tvParityZoom","tvParityGoalActions","tvParityNextErrorCategory","tvParityErrorResolutionAction","tvParitySearchScore"):
    q(x in web,"Web TV parity semantic missing "+x)
q('versionCode = 31' in t("app-tv/build.gradle.kts"),"TV terminal versionCode must be 31")
q('versionName = "1.0.0-tv31"' in t("app-tv/build.gradle.kts"),"TV terminal versionName missing")
q('"completed_through": "TVOV31"' in t("docs/roadmap/CROSS_PLATFORM_REMAINING_WORK.json"),"remaining-work truth not advanced")
progress=t("app-tv/src/main/java/com/mikeyphw/studyflow/tv/progress/StudyFlowTvProgressAnalytics.kt")
progress_test=t("app-tv/src/test/java/com/mikeyphw/studyflow/tv/progress/StudyFlowTvProgressAnalyticsTest.kt")
review=t("app-tv/src/main/java/com/mikeyphw/studyflow/tv/reviews/StudyFlowTvReviewNotebook.kt")
review_test=t("app-tv/src/test/java/com/mikeyphw/studyflow/tv/reviews/StudyFlowTvReviewNotebookTest.kt")
q('const val introducedOverlay = "TVOV12"' in progress and 'const val overlay = "TVOV28"' in progress,"progress overlay provenance/current ownership mismatch")
q('StudyFlowTvProgressAnalytics.introducedOverlay' in progress_test and 'assertEquals("TVOV28", StudyFlowTvProgressAnalytics.overlay)' in progress_test,"progress roadmap assertions are stale")
q('const val introducedOverlay = "TVOV11"' in review and 'const val overlay = "TVOV29"' in review,"review overlay provenance/current ownership mismatch")
q('StudyFlowTvReviewNotebook.introducedOverlay' in review_test and 'assertEquals("TVOV29", StudyFlowTvReviewNotebook.overlay)' in review_test,"review roadmap assertions are stale")
if not F:
    result=subprocess.run(["node",str(R/"web/tests/tvov31_fixture_runner.cjs")],cwd=R,text=True,capture_output=True)
    if result.returncode!=0:F.append("Web TV fixture runner failed: "+(result.stderr or result.stdout).strip())
    elif "PASS" not in result.stdout:F.append("Web TV fixture runner did not report PASS")
b=t("build.gradle.kts");d=t(".devtool.toml")
q('tasks.register<Exec>("verifyStudyFlowTvAndroidWebSemanticParity")' in b,"Gradle TVOV31 task missing")
q('tasks.register("verifyStudyFlowTvTvov27Tvov31Batch")' in b,"TVOV27-31 aggregate task missing")
q("verifyStudyFlowTvAndroidWebSemanticParity" in d,"Devtool TVOV31 task missing")
if F:
 print("TVOV31_ANDROID_WEB_TV_SEMANTIC_PARITY: FAIL");[print("-",x) for x in F];sys.exit(1)
print("TVOV31_ANDROID_WEB_TV_SEMANTIC_PARITY: PASS")
