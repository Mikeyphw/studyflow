#!/usr/bin/env python3
from pathlib import Path
root=Path(__file__).resolve().parents[1]
checks={
 "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/PublicExamQuestionQuotaPlanner.kt":[
   "0.35 * weightShares[index]",
   "0.20 * underPracticeShares[index]",
   "0.20 * accuracyShares[index]",
   "0.15 * errorShares[index]",
   "0.10 * reviewShares[index]",
   "subjects.sumOf(PublicExamQuestionQuotaSubject::weeklyQuestions) == nextSevenDaysQuestions",
 ],
 "feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/ProgressWorkspaceScreen.kt":[
   "PublicExamQuestionQuotaPlanner.plan(",
   "question_quota_formula",
   "questionQuota?.let",
 ],
}
for rel,needles in checks.items():
 text=(root/rel).read_text()
 for n in needles: assert n in text, f"{rel}: missing {n}"
print("OV16 question quota source contract: PASS")
