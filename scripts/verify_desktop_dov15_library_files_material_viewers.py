#!/usr/bin/env python3
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
checks={
 "persistence":("desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopLibraryPersistence.kt",[
  "library-v1.properties","DesktopLibrarySnapshot","ATOMIC_MOVE","removeDesktopImportedLibraryFiles"]),
 "repository":("desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/InMemoryStudyLibraryRepository.kt",[
  "onChanged","purgePlan","currentMaterials","onImportedFilesRemoved"]),
 "controller":("desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopLibraryWorkspaceController.kt",[
  "content://studyflow-desktop/","Files.copy","resolveContentUri","DesktopMaterialViewerKind.TEXT",
  "DesktopMaterialViewerKind.IMAGE","DesktopMaterialViewerKind.PDF","addWebResource","countPdfPageTokens"]),
 "services":("desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopStudyFlowServices.kt",[
  "DesktopLibraryPersistence.open","libraryWorkspaceController","libraryRepository = libraryRepository"]),
 "ui":("desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopLibraryScreen.kt",[
  "Add resource","Import file","Material viewer","Resource detail"]),
 "test":("desktopApp/src/test/kotlin/com/mikeyphw/studyflow/desktop/DesktopLibraryPersistenceViewerTest.kt",[
  "importedLocalFileAndViewerPersistAcrossRestart","content://studyflow-desktop/","DesktopMaterialViewerKind.TEXT",
  "library-v1.properties","deleteSelectedPermanently"]),
}
for label,(name,tokens) in checks.items():
 text=(ROOT/name).read_text(); missing=[x for x in tokens if x not in text]
 if missing: raise SystemExit(f"{label} missing: {missing}")
if "fun importedLocalFileAndViewerPersistAcrossRestart() = runBlocking" in (ROOT/checks["test"][0]).read_text():
 raise SystemExit("DOV15 JUnit test must use a block body")
cap=(ROOT/'docs/product/CROSS_PLATFORM_CAPABILITY_MATRIX.md').read_text()
rows=[
'| Questions and practice | FULL | FULL | FULL | FULL | FULL |',
'| Mock Exam Studio | FULL | FULL | FULL | FULL | FULL |',
'| Reviews / SRS | FULL | FULL | FULL | LIMITED | FULL |',
'| Flashcards | FULL | FULL | FULL | LIMITED | FULL |',
'| Error Notebook | FULL | FULL | FULL | LIMITED | FULL |',
'| Library catalog | FULL | FULL | FULL | LIMITED | FULL |',
]
missing=[row for row in rows if row not in cap]
if missing: raise SystemExit(f"capability matrix missing: {missing}")
if not any(row in cap for row in (
    '| Full material viewing | FULL | FULL | LIMITED | LIMITED | FULL |',
    '| Full material viewing | FULL | FULL | FULL | LIMITED | FULL |',
)):
    raise SystemExit("capability matrix lost the DOV15-or-later full-material-viewing state")
build = (ROOT / "build.gradle.kts").read_text()
if 'tasks.register<Exec>("verifyDesktopDov15LibraryFilesMaterialViewers")' not in build:
    raise SystemExit("DOV15 Gradle verification task is not registered")
devtool = (ROOT / ".devtool.toml").read_text()
if '"verifyDesktopDov15LibraryFilesMaterialViewers"' not in devtool:
    raise SystemExit("DOV15 Devtool target does not include its verification task")
print("DESKTOP_DOV15_LIBRARY_FILES_MATERIAL_VIEWERS: PASS")
