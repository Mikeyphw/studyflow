#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
failures: list[str] = []


def read(path: str) -> str:
    target = ROOT / path
    if not target.exists():
        failures.append(f"Missing {path}")
        return ""
    return target.read_text()


def require(token: str, haystack: str, message: str) -> None:
    if token not in haystack:
        failures.append(message)


model = read("core/model/src/main/kotlin/com/mikeyphw/studyflow/core/model/ContentImportTemplates.kt")
test = read("core/model/src/test/kotlin/com/mikeyphw/studyflow/core/model/ContentImportTemplatesTest.kt")

sample_expectations = {
    "samples/import/programming_course_template.csv": "type,module,topic,title,prompt",
    "samples/import/programming_course_template.md": "Programming course import template",
    "samples/import/language_idioms_template.csv": "expression,meaning,literal_translation",
    "samples/import/language_idioms_template.md": "Language idioms import template",
}
for path, token in sample_expectations.items():
    source = read(path)
    require(token, source, f"Sample template content missing in {path}")

for token in [
    "StudyFlowImportTemplateType",
    "PROGRAMMING_COURSE_CSV",
    "LANGUAGE_IDIOMS_CSV",
    "previewProgrammingCourseCsv",
    "previewLanguageIdiomsCsv",
    "previewProgrammingCourseMarkdown",
    "previewLanguageIdiomsMarkdown",
    "StudyFlowImportPreview",
    "StudyFlowImportIssue",
    "Missing required column",
]:
    require(token, model, f"Import template model missing {token}")

for token in [
    "programming csv preview validates rows before import",
    "language idiom csv preview reports missing required headers",
    "markdown previews support programming and idiom templates",
]:
    require(token, test, f"Import template regression test missing {token}")

for path in [
    "core/model/src/main/kotlin/com/mikeyphw/studyflow/core/model/ContentImportTemplates.kt",
    "core/model/src/test/kotlin/com/mikeyphw/studyflow/core/model/ContentImportTemplatesTest.kt",
]:
    for idx, line in enumerate(read(path).splitlines(), 1):
        if len(line) > 120:
            failures.append(f"{path}:{idx} exceeds 120 characters")

if failures:
    raise SystemExit("CMOV-09 verification failed:\n" + "\n".join(failures))
print("IMPORT_TEMPLATES_VALIDATION_OK")
