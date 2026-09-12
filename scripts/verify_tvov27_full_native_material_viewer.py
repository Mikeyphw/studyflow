#!/usr/bin/env python3
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
FAIL=[]
def text(p): return (ROOT/p).read_text(encoding="utf-8")
def req(cond,msg):
    if not cond: FAIL.append(msg)
presentation=text("core/tvcontrol/src/main/kotlin/com/mikeyphw/studyflow/core/tvcontrol/StudyFlowTvPresentation.kt")
viewer=text("app-tv/src/main/java/com/mikeyphw/studyflow/tv/library/StudyFlowTvMaterialViewer.kt")
activity=text("app-tv/src/main/java/com/mikeyphw/studyflow/tv/StudyFlowTvActivity.kt")
projector=text("app/src/main/java/com/mikeyphw/studyflow/tvcontrol/StudyFlowTvPresentationProjector.kt")
build=text("build.gradle.kts")
dev=text(".devtool.toml")
for token in ("StudyFlowTvMaterialViewerPresentation", "StudyFlowTvMaterialViewerKind", "val viewer:"):
    req(token in presentation, f"presentation schema missing {token}")
for token in ("PdfRenderer", "VideoView", "bindImage", "renderPdfPage", "PLAY_PAUSE", "paginate"):
    req(token in viewer, f"native viewer missing {token}")
for token in ("renderLibraryMaterialViewer", "StudyFlowTvMaterialViewer.open", "StudyFlowTvNativeMaterialViewer"):
    req(token in activity, f"Activity viewer integration missing {token}")
req("viewer = StudyFlowTvMaterialViewerPresentation" in projector, "phone projector must emit viewer descriptors")
req('tasks.register<Exec>("verifyStudyFlowTvFullNativeMaterialViewer")' in build, "Gradle task missing")
req("verifyStudyFlowTvAllContracts" in dev, "Devtool target missing aggregate TV contract gate")
tv_aggregate = build.split('tasks.register("verifyStudyFlowTvAllContracts")', 1)[1].split('tasks.register("verifyStudyFlowAndroidPreflightContracts")', 1)[0]
req("verifyStudyFlowTvFullNativeMaterialViewer" in tv_aggregate, "aggregate TV contract gate missing verifyStudyFlowTvFullNativeMaterialViewer")
req("TVOV27" in text("TVOV27_ANDROID_TV_FULL_NATIVE_MATERIAL_VIEWER.md"), "TVOV27 implementation note missing")
if FAIL:
    print("TVOV27_FULL_NATIVE_MATERIAL_VIEWER: FAIL")
    for f in FAIL: print("-",f)
    raise SystemExit(1)
req("pageIndex in 0 until pageCount" in viewer, "PDF/text page index must validate against effective page count")
print("TVOV27_FULL_NATIVE_MATERIAL_VIEWER: PASS")
