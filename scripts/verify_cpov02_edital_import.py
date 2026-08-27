from __future__ import annotations

import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
failures: list[str] = []


def require(condition: bool, message: str) -> None:
    if not condition:
        failures.append(message)


def text(rel: str) -> str:
    return (ROOT / rel).read_text(encoding="utf-8")


parser = text("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/ExamNoticeImport.kt")
syllabus_parser = text("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/SyllabusImportParser.kt")
dialogs = text("feature/subjects/src/main/java/com/mikeyphw/studyflow/feature/subjects/StudyContentDialogs.kt")
screen = text("feature/subjects/src/main/java/com/mikeyphw/studyflow/feature/subjects/StudyContentScreen.kt")
loader = text("feature/subjects/src/main/java/com/mikeyphw/studyflow/feature/subjects/ExamNoticeDocumentLoader.kt")
build = text("feature/subjects/build.gradle.kts")
tests = text("core/domain/src/test/kotlin/com/mikeyphw/studyflow/core/domain/ExamNoticeImportParserTest.kt")

profile_section = text("feature/plans/src/main/java/com/mikeyphw/studyflow/feature/plans/PublicExamProfileSection.kt")
require(
    "import androidx.compose.foundation.layout.weight" not in profile_section,
    "PublicExamProfileSection must rely on RowScope.weight instead of importing Compose internal weight",
)
require(
    profile_section.count("Modifier.weight(1f)") >= 2,
    "PublicExamProfileSection stage editor must retain weighted row fields",
)

for token in (
    "ExamNoticeImportParser",
    "ExamNoticeImportPreview",
    "ExamNoticeImportIssueKind",
    "DUPLICATE_SUBJECT",
    "DUPLICATE_TOPIC",
    "UNRECOGNIZED_HEADING",
    "ORPHAN_TOPIC",
    "existing.studyPlanId == planId",
    "destructiveChanges: Int = 0",
):
    require(token in parser, f"CPOV02 parser contract missing: {token}")

require("TSV" in syllabus_parser and "parseTsv" in syllabus_parser, "TSV syllabus normalization is missing")
require("parseDelimited(input, '\\t', \"TSV\")" in syllabus_parser, "TSV must use tab-delimited parsing")
require("SyllabusImportDraft" in parser, "Edital preview must normalize to SyllabusImportDraft")
require("@Entity" not in parser, "CPOV02 must not introduce a persistence entity")

for token in (
    "ActivityResultContracts.OpenDocument()",
    "ExamNoticeDocumentLoader.load",
    "ExamNoticeImportParser.preview",
    "existingSyllabus = syllabus",
    "plan.type == StudyType.PUBLIC_EXAMINATION",
):
    require(token in dialogs + screen, f"Android edital import UX contract missing: {token}")

require("result?.takeIf(ExamNoticeImportPreview::canApply)" in dialogs, "Apply must require a successful preview")
require("onImport(it.draft)" in dialogs, "Preview draft must be the only explicit apply payload")
require("onValueChange" in dialogs and "preview = null" in dialogs, "Editing source must invalidate the preview")
require("destructiveChanges" in dialogs, "Preview must surface destructive-change count")
require("duplicateSubjects" in dialogs and "duplicateTopics" in dialogs, "Preview must surface duplicates")
require("ExamNoticeImportIssueKind.ORPHAN_TOPIC" in dialogs, "Preview must surface orphan topics")
require("ExamNoticeImportIssueKind.UNRECOGNIZED_HEADING" in dialogs, "Preview must surface headings")

for token in (
    "PdfRenderer",
    "page.textContents",
    "Build.VERSION.SDK_INT < 35",
    "PDF_HAS_NO_TEXT_LAYER",
    "PDF_TEXT_REQUIRES_ANDROID_15",
    "DOCUMENT_TOO_LARGE",
):
    require(token in loader, f"Local document loader contract missing: {token}")
require("CancellationException" in loader and "throw cancellation" in loader, "Document loader must rethrow cancellation")
require("tesseract" not in loader.lower() and "mlkit" not in loader.lower(), "CPOV02 must not add implicit OCR")
require("implementation(libs.androidx.activity.compose)" in build, "File picker dependency is missing")

for token in (
    "numberedEditalBuildsNestedHierarchyDeterministically",
    "duplicateSubjectsAndTopicsAreVisibleWithoutMutatingSource",
    "orphanNumberedTopicIsReportedInsteadOfInventingParent",
    "tsvTemplateNormalizesIntoExistingSyllabusDraft",
    "malformedOrCrossPlanPreviewFailsBeforeAnyApplyBoundaryExists",
    "additivePreviewNeverAdvertisesDestructiveChanges",
):
    require(token in tests, f"CPOV02 regression test missing: {token}")

prefixes = ("exam_notice_", "import_exam_notice", "choose_exam_notice_file", "tsv")
resource_names: dict[str, set[str]] = {}
for locale, rel in {
    "en": "feature/subjects/src/main/res/values/strings.xml",
    "pt-BR": "feature/subjects/src/main/res/values-pt-rBR/strings.xml",
}.items():
    root = ET.parse(ROOT / rel).getroot()
    resource_names[locale] = {
        node.attrib["name"]
        for node in root
        if node.tag == "string" and node.attrib.get("name", "").startswith(prefixes)
    }
require(resource_names["en"] == resource_names["pt-BR"], "CPOV02 EN/pt-BR resource parity mismatch")
require(len(resource_names["en"]) >= 20, "CPOV02 bilingual resource surface is unexpectedly incomplete")

if failures:
    print("CPOV02_EDITAL_IMPORT: FAILED")
    for failure in failures:
        print(f"- {failure}")
    raise SystemExit(1)

print(
    "CPOV02_EDITAL_IMPORT: PASSED "
    f"({len(resource_names['en'])} bilingual strings, preview-first parser + local document lanes green)"
)
