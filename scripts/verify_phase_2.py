#!/usr/bin/env python3
from pathlib import Path
import re
import sys
import tomllib

ROOT = Path(__file__).resolve().parents[1]
failures: list[str] = []

required_files = [
    "core/database/build.gradle.kts",
    "build-logic/convention/src/main/kotlin/AndroidLibraryRoomConventionPlugin.kt",
    "build-logic/convention/src/main/kotlin/KotlinLibrarySerializationConventionPlugin.kt",
    "core/database/src/main/AndroidManifest.xml",
    "core/database/src/main/java/com/mikeyphw/studyflow/core/database/StudyFlowDatabase.kt",
    "core/database/src/main/java/com/mikeyphw/studyflow/core/database/entity/StudyFlowEntities.kt",
    "core/database/src/main/java/com/mikeyphw/studyflow/core/database/dao/StudyPlanDao.kt",
    "core/database/src/main/java/com/mikeyphw/studyflow/core/database/dao/PlanOwnershipDao.kt",
    "core/database/src/main/java/com/mikeyphw/studyflow/core/database/mapper/StudyPlanEntityMapper.kt",
    "core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/RoomStudyPlanRepository.kt",
    "docs/architecture/ADR-0004-room-domain-database.md",
    "docs/quality/PHASE_2_ACCEPTANCE.md",
    "PHASE_2_IMPLEMENTATION.md",
]
for relative_path in required_files:
    if not (ROOT / relative_path).is_file():
        failures.append(f"missing Phase 2 file: {relative_path}")

root_build = (ROOT / "build.gradle.kts").read_text(encoding="utf-8")
if "toPath().none { pathSegment ->" not in root_build:
    failures.append("root Gradle formatting filter must iterate over path segments")
if re.search(r"&&\s*none\s*\{", root_build):
    failures.append("root Gradle formatting filter contains receiverless none invocation")

settings = (ROOT / "settings.gradle.kts").read_text(encoding="utf-8")
if '":core:database"' not in settings:
    failures.append("settings.gradle.kts does not include :core:database")

versions = tomllib.loads((ROOT / "gradle/libs.versions.toml").read_text(encoding="utf-8"))
if versions.get("versions", {}).get("room") != "2.8.0":
    failures.append("Room must be pinned to 2.8.0 for the Phase 2 baseline")
if versions.get("versions", {}).get("ksp") != "2.3.10":
    failures.append("KSP must be pinned to 2.3.10 for the Phase 2 baseline")

gradle_properties = (ROOT / "gradle.properties").read_text(encoding="utf-8")
if "android.builtInKotlin=false" in gradle_properties:
    failures.append("AGP built-in Kotlin must not be disabled")
if "android.builtInKotlin=true" not in gradle_properties:
    failures.append("AGP built-in Kotlin must be explicitly enabled for the Phase 2 baseline")
if "android.newDsl=false" in gradle_properties:
    failures.append("AGP new DSL must remain enabled")

build_logic = (ROOT / "build-logic/convention/build.gradle.kts").read_text(encoding="utf-8")
for dependency in (
    'org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.10',
    'org.jetbrains.kotlin:kotlin-serialization:2.4.10',
    'com.google.devtools.ksp:symbol-processing-gradle-plugin:2.3.10',
):
    if dependency not in build_logic:
        failures.append(f"build logic is missing plugin-classloader dependency: {dependency}")
for convention_id in (
    'studyflow.android.library.room',
    'studyflow.kotlin.library.serialization',
):
    if convention_id not in build_logic:
        failures.append(f"build logic is missing convention plugin: {convention_id}")

application_convention = (
    ROOT / "build-logic/convention/src/main/kotlin/AndroidApplicationConventionPlugin.kt"
).read_text(encoding="utf-8")
library_convention = (
    ROOT / "build-logic/convention/src/main/kotlin/AndroidLibraryConventionPlugin.kt"
).read_text(encoding="utf-8")
for source_name, source in (
    ("application", application_convention),
    ("library", library_convention),
):
    if 'pluginManager.apply("org.jetbrains.kotlin.android")' in source:
        failures.append(f"{source_name} convention must use AGP built-in Kotlin")

room_convention = (
    ROOT / "build-logic/convention/src/main/kotlin/AndroidLibraryRoomConventionPlugin.kt"
).read_text(encoding="utf-8")
for contract in (
    'pluginManager.apply("com.google.devtools.ksp")',
    'extensions.configure<KspExtension>',
    'arg("room.schemaLocation"',
):
    if contract not in room_convention:
        failures.append(f"Room convention is missing contract: {contract}")

database_build = (ROOT / "core/database/build.gradle.kts").read_text(encoding="utf-8")
if 'id("studyflow.android.library.room")' not in database_build:
    failures.append("core:database does not use the Room convention plugin")
if 'add("ksp", libs.androidx.room.compiler)' not in database_build:
    failures.append("core:database does not register the Room compiler through the KSP configuration")
if "alias(libs.plugins.ksp)" in database_build or "ksp {" in database_build:
    failures.append("core:database bypasses the unified KSP convention classloader")

navigation_build = (ROOT / "core/navigation/build.gradle.kts").read_text(encoding="utf-8")
if 'id("studyflow.kotlin.library.serialization")' not in navigation_build:
    failures.append("core:navigation does not use the serialization convention plugin")

if "libs.plugins.ksp" in root_build or "libs.plugins.kotlin.serialization" in root_build:
    failures.append("root build script must not preload KSP or Serialization in a separate classloader")

# Plain JVM modules cannot consume Android libraries without selecting a build variant.
# Keep fixture and domain modules Android-free so Gradle can resolve their runtime classpaths
# deterministically for both desktop and Termux validation.
for jvm_build in sorted(ROOT.rglob("build.gradle.kts")):
    source = jvm_build.read_text(encoding="utf-8")
    if 'id("studyflow.kotlin.library")' not in source and (
        'id("studyflow.kotlin.library.serialization")' not in source
    ):
        continue
    for dependency in re.findall(r'project\("(:[^"\)]+)"\)', source):
        dependency_build = ROOT / dependency.removeprefix(":").replace(":", "/") / "build.gradle.kts"
        if not dependency_build.is_file():
            continue
        dependency_source = dependency_build.read_text(encoding="utf-8")
        if 'id("studyflow.android.' in dependency_source or 'com.android.' in dependency_source:
            module = jvm_build.parent.relative_to(ROOT).as_posix().replace("/", ":")
            failures.append(
                f"JVM module :{module} must not depend on Android module {dependency}"
            )

testing_build = (ROOT / "core/testing/build.gradle.kts").read_text(encoding="utf-8")
if 'project(":core:data")' in testing_build:
    failures.append(
        ":core:testing must not depend on Android module :core:data; use domain/model fixtures instead"
    )
for dependency in ('project(":core:domain")', 'project(":core:model")'):
    if dependency not in testing_build:
        failures.append(f":core:testing is missing JVM-safe dependency {dependency}")

model_sources = "\n".join(
    path.read_text(encoding="utf-8")
    for path in sorted((ROOT / "core/model/src/main/kotlin").rglob("*.kt"))
)
required_models = [
    "StudyPlan",
    "StudyTarget",
    "ExamStage",
    "Subject",
    "Topic",
    "StudyStrategy",
    "ScheduleRule",
    "PlannedActivity",
    "StudySession",
    "StudyGoal",
    "StudyMaterial",
    "VideoResource",
    "Annex",
    "Question",
    "StudyTest",
    "MockExam",
    "ReviewItem",
    "Flashcard",
    "ErrorEntry",
    "StudyNote",
    "PlanSnapshot",
    "StudyPlanSettings",
]
for model in required_models:
    if not re.search(rf"\b(?:data class|class)\s+{re.escape(model)}\b", model_sources):
        failures.append(f"missing domain model: {model}")

entity_path = ROOT / "core/database/src/main/java/com/mikeyphw/studyflow/core/database/entity/StudyFlowEntities.kt"
if entity_path.is_file():
    entity_source = entity_path.read_text(encoding="utf-8")
    plan_owned_entities = [
        "StudyTargetEntity",
        "ExamStageEntity",
        "SubjectEntity",
        "TopicEntity",
        "StudyStrategyEntity",
        "ScheduleRuleEntity",
        "PlannedActivityEntity",
        "StudySessionEntity",
        "StudyGoalEntity",
        "StudyMaterialEntity",
        "VideoResourceEntity",
        "AnnexEntity",
        "QuestionEntity",
        "StudyTestEntity",
        "MockExamEntity",
        "ReviewItemEntity",
        "FlashcardEntity",
        "ErrorEntryEntity",
        "StudyNoteEntity",
        "PlanSnapshotEntity",
        "StudyPlanSettingsEntity",
    ]
    for entity in plan_owned_entities:
        marker = f"data class {entity}"
        if marker not in entity_source:
            failures.append(f"missing Room entity: {entity}")
            continue
        prefix = entity_source[: entity_source.index(marker)]
        annotation_start = prefix.rfind("@Entity(")
        annotation = prefix[annotation_start:]
        body_start = entity_source.index(marker)
        next_entity = entity_source.find("\n@Entity", body_start)
        body = entity_source[body_start: next_entity if next_entity != -1 else None]
        if "studyPlanId" not in body:
            failures.append(f"{entity} is not owned by studyPlanId")
        if "StudyPlanEntity::class" not in annotation or "ForeignKey.CASCADE" not in annotation:
            failures.append(f"{entity} does not cascade from StudyPlanEntity")

    if 'tableName = "database_metadata"' not in entity_source:
        failures.append("database metadata table is missing")

room_database = ROOT / "core/database/src/main/java/com/mikeyphw/studyflow/core/database/StudyFlowDatabase.kt"
if room_database.is_file():
    database_source = room_database.read_text(encoding="utf-8")
    if "version = StudyFlowDatabase.SCHEMA_VERSION" not in database_source:
        failures.append("StudyFlowDatabase does not declare a stable schema version")
    if "exportSchema = true" not in database_source:
        failures.append("Room schema export must be enabled")
    if "const val SCHEMA_VERSION = 1" not in database_source:
        failures.append("Phase 2 database schema version must be 1")

repository_path = ROOT / "core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/RoomStudyPlanRepository.kt"
if repository_path.is_file():
    repository = repository_path.read_text(encoding="utf-8")
    for contract in (
        "StudyPlanRepository",
        "database.withTransaction",
        "seedFoundationSamplesOnce",
        "FOUNDATION_SEED_KEY",
        "DatabaseMetadataEntity",
    ):
        if contract not in repository:
            failures.append(f"RoomStudyPlanRepository missing contract: {contract}")

application = (
    ROOT / "app/src/main/java/com/mikeyphw/studyflow/StudyFlowApplication.kt"
).read_text(encoding="utf-8")
if "RoomStudyPlanRepository" not in application:
    failures.append("application container does not use RoomStudyPlanRepository")
if "InMemoryStudyPlanRepository" in application:
    failures.append("application container still uses InMemoryStudyPlanRepository")

try:
    devtool = tomllib.loads((ROOT / ".devtool.toml").read_text(encoding="utf-8"))
    target = devtool.get("targets", {}).get("studyflow_android", {})
    phases = target.get("validation", {}).get("phases", {})
    compile_tasks = phases.get("compile", [])
    unit_tasks = phases.get("unit-test", [])
    if ":core:database:compileDebugKotlin" not in compile_tasks:
        failures.append("Devtool compile phase does not include :core:database")
    if ":core:database:testDebugUnitTest" not in unit_tasks:
        failures.append("Devtool unit-test phase does not include :core:database")
except (OSError, tomllib.TOMLDecodeError) as error:
    failures.append(f"unable to validate .devtool.toml: {error}")

if failures:
    print("PHASE_2_CONTRACT: FAILED")
    print("\n".join(f"- {failure}" for failure in failures))
    sys.exit(1)

print(f"PHASE_2_CONTRACT: PASSED ({len(required_models)} domain roots)")
