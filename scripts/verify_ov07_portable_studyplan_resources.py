#!/usr/bin/env python3
from pathlib import Path
import sys
import xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[1]
errors=[]
def read(rel): return (ROOT/rel).read_text()
models=read("core/portability/src/main/kotlin/com/mikeyphw/studyflow/core/portability/StudyPlanPackageModels.kt")
resources=read("core/portability/src/main/kotlin/com/mikeyphw/studyflow/core/portability/StudyPlanPortableResources.kt")
codec=read("core/portability/src/main/kotlin/com/mikeyphw/studyflow/core/portability/StudyPlanPackageCodec.kt")
store=read("core/database/src/main/java/com/mikeyphw/studyflow/core/database/StudyPlanGraphPortabilityStore.kt")
screen=read("feature/plans/src/main/java/com/mikeyphw/studyflow/feature/plans/PlanPortabilityScreen.kt")
for marker in ("PortableResourceHandler", "StudyPlanPortableResource", "resources: List<StudyPlanPortableResource>"):
    if marker not in models: errors.append(f"models missing {marker}")
for marker in ("application/pdf", "wordprocessingml.document", "text/", "image/", "audio/", "video/"):
    if marker not in resources: errors.append(f"handler resolver missing {marker}")
if "resources = StudyPlanPortableResources.forManifest(payload.manifest)" not in codec:
    errors.append("codec preview does not expose portable resources")
if "StudyPlanGraphDuplicator.PortabilityTableSpecs.filter" not in store:
    errors.append("portability export still excludes AnnexSpec from selected specs")
if "StudyPlanGraphDuplicator.TableSpecs.filter { it.tableName in selectedTableNames(options) }" in store:
    errors.append("legacy annex-dropping selectedSpecs path remains")
for marker in ("portable_resource_count", "portableResourceHandlerLabel", "resource.checksumSha256.take(12)"):
    if marker not in screen: errors.append(f"portability UI missing {marker}")
if screen.count("modifier = Modifier.fillMaxWidth().padding(16.dp),") >= 2 and "private fun SnapshotCard" in screen:
    # exact historical duplicate named argument must not remain adjacent
    if "modifier = Modifier.fillMaxWidth().padding(16.dp),\n            modifier = Modifier.fillMaxWidth().padding(16.dp)," in screen:
        errors.append("SnapshotCard retains duplicate modifier named argument")
for rel in ("feature/plans/src/main/res/values/strings.xml","feature/plans/src/main/res/values-pt-rBR/strings.xml"):
    ET.parse(ROOT/rel)
if errors:
    print("OV07 portable studyplan resources FAILED", file=sys.stderr)
    print("\n".join(f"- {e}" for e in errors), file=sys.stderr)
    raise SystemExit(1)
print("OV07_PORTABLE_STUDYPLAN_RESOURCES: PASS")
