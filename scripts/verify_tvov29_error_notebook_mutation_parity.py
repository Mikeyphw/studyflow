#!/usr/bin/env python3
from pathlib import Path
R=Path(__file__).resolve().parents[1]; F=[]
def t(p): return (R/p).read_text()
def q(c,m):
    if not c:F.append(m)
p=t("core/tvcontrol/src/main/kotlin/com/mikeyphw/studyflow/core/tvcontrol/StudyFlowTvPresentation.kt")
d=t("core/tvcontrol/src/main/kotlin/com/mikeyphw/studyflow/core/tvcontrol/StudyFlowTvDomainAction.kt")
e=t("app/src/main/java/com/mikeyphw/studyflow/tvcontrol/StudyFlowTvDomainActionExecutor.kt")
r=t("app-tv/src/main/java/com/mikeyphw/studyflow/tv/reviews/StudyFlowTvReviewNotebook.kt")
a=t("app-tv/src/main/java/com/mikeyphw/studyflow/tv/StudyFlowTvActivity.kt")
s=t("app-tv/src/main/res/values/strings.xml")
for x in ("errorEntryId", "errorCategory"): q(x in p,"review projection missing "+x)
for x in ("ERROR_RECLASSIFY","ERROR_RESOLVE","ERROR_REOPEN"): q(x in d,"domain action missing "+x);q(x in e,"executor missing "+x)
q("reclassifyErrorEntry" in e,"canonical reclassification missing")
q("resolveErrorEntry" in e,"canonical resolve missing")
q("createErrorFromQuestion" in e and "ERROR_REOPEN" in e,"canonical reopen rematerialization missing")
q("locallyResolvedSourceIds" in r and "REOPEN_ERROR" in r,"TV resolve/reopen state missing")
for action, resource in (("RECLASSIFY_ERROR", "tv_reviews_action_reclassify_error"), ("RESOLVE_ERROR", "tv_reviews_action_resolve_error"), ("REOPEN_ERROR", "tv_reviews_action_reopen_error")):
 q(f"StudyFlowTvReviewAction.{action} -> R.string.{resource}" in a,"TV Activity action label mapping missing "+action)
 q(f'name="{resource}"' in s,"TV string resource missing "+resource)
b=t("build.gradle.kts"); c=t(".devtool.toml")
q('tasks.register<Exec>("verifyStudyFlowTvErrorNotebookMutationParity")' in b,"Gradle task missing")
q("verifyStudyFlowTvAllContracts" in c, "Devtool target missing aggregate TV contract gate")
tv_aggregate = b.split('tasks.register("verifyStudyFlowTvAllContracts")', 1)[1].split('tasks.register("verifyStudyFlowAndroidPreflightContracts")', 1)[0]
q("verifyStudyFlowTvErrorNotebookMutationParity" in tv_aggregate, "aggregate TV contract gate missing verifyStudyFlowTvErrorNotebookMutationParity")
if F:
 print("TVOV29_ERROR_NOTEBOOK_MUTATION_PARITY: FAIL");[print("-",x) for x in F];raise SystemExit(1)
print("TVOV29_ERROR_NOTEBOOK_MUTATION_PARITY: PASS")
