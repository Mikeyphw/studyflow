#!/usr/bin/env python3
from pathlib import Path
import re
import sys
import tomllib
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
REQUIRED_MODULES = [
    "app",
    "core/data",
    "core/designsystem",
    "core/domain",
    "core/i18n",
    "core/model",
    "core/navigation",
    "core/portability",
    "core/testing",
    "feature/dashboard",
    "feature/plans",
    "feature/settings",
]

failures = []

for module in REQUIRED_MODULES:
    build_file = ROOT / module / "build.gradle.kts"
    if not build_file.is_file():
        failures.append(f"missing module build file: {build_file.relative_to(ROOT)}")

required_files = [
    ".devtool.toml",
    "gradle/wrapper/gradle-wrapper.properties",
    "build-logic/convention/src/main/kotlin/AndroidApplicationConventionPlugin.kt",
    "app/src/main/AndroidManifest.xml",
    "app/src/main/res/xml/locales_config.xml",
    "app/src/main/java/com/mikeyphw/studyflow/MainActivity.kt",
    "core/i18n/src/main/java/com/mikeyphw/studyflow/core/i18n/DataStoreAppPreferencesRepository.kt",
    "core/designsystem/src/main/java/com/mikeyphw/studyflow/core/designsystem/StudyFlowTheme.kt",
    "feature/plans/src/main/java/com/mikeyphw/studyflow/feature/plans/PlanListScreen.kt",
    "feature/plans/src/main/java/com/mikeyphw/studyflow/feature/plans/CreatePlanScreen.kt",
    "feature/dashboard/src/main/java/com/mikeyphw/studyflow/feature/dashboard/DashboardScreen.kt",
    "feature/settings/src/main/java/com/mikeyphw/studyflow/feature/settings/SettingsScreen.kt",
]
for relative_path in required_files:
    if not (ROOT / relative_path).is_file():
        failures.append(f"missing foundation file: {relative_path}")

module_build_scripts = "\n".join(
    path.read_text(encoding="utf-8")
    for module in REQUIRED_MODULES
    for path in [(ROOT / module / "build.gradle.kts")]
    if path.is_file()
)
if "org.jetbrains.kotlin.android" in module_build_scripts:
    failures.append("Android modules must receive Kotlin through convention plugins, not direct application")

gradle_properties = (ROOT / "gradle.properties").read_text(encoding="utf-8")
external_kotlin_mode = "android.builtInKotlin=false" in gradle_properties
if external_kotlin_mode:
    for convention in (
        "AndroidApplicationConventionPlugin.kt",
        "AndroidLibraryConventionPlugin.kt",
    ):
        source = (
            ROOT / "build-logic/convention/src/main/kotlin" / convention
        ).read_text(encoding="utf-8")
        if 'pluginManager.apply("org.jetbrains.kotlin.android")' not in source:
            failures.append(f"{convention}: external Kotlin compatibility mode is incomplete")

android_namespace = "http://schemas.android.com/apk/res/android"
def read_string_keys(path: Path) -> set[str]:
    tree = ET.parse(path)
    return {
        element.attrib["name"]
        for element in tree.getroot()
        if element.tag == "string" and element.attrib.get("translatable", "true") != "false"
    }

for module in ["app", "core/designsystem", "feature/plans", "feature/dashboard", "feature/settings"]:
    base = ROOT / module / "src/main/res/values/strings.xml"
    pt = ROOT / module / "src/main/res/values-pt-rBR/strings.xml"
    if not base.is_file() or not pt.is_file():
        failures.append(f"{module}: missing English or pt-BR strings")
        continue
    try:
        base_keys = read_string_keys(base)
        pt_keys = read_string_keys(pt)
    except ET.ParseError as error:
        failures.append(f"{module}: invalid strings XML: {error}")
        continue
    if base_keys != pt_keys:
        missing_pt = sorted(base_keys - pt_keys)
        extra_pt = sorted(pt_keys - base_keys)
        failures.append(f"{module}: translation mismatch; missing_pt={missing_pt}, extra_pt={extra_pt}")

hardcoded_pattern = re.compile(r'\b(?:Text|ButtonText)\s*\(\s*"[A-Za-zÀ-ÿ]')
for source_file in list((ROOT / "app").rglob("*.kt")) + list((ROOT / "feature").rglob("*.kt")):
    for line_number, line in enumerate(source_file.read_text(encoding="utf-8").splitlines(), 1):
        if hardcoded_pattern.search(line):
            failures.append(f"{source_file.relative_to(ROOT)}:{line_number}: hardcoded user-facing text")

manifest = ROOT / "app/src/main/AndroidManifest.xml"
if manifest.is_file():
    manifest_root = ET.parse(manifest).getroot()
    application = manifest_root.find("application")
    locale_config = application.attrib.get(f"{{{android_namespace}}}localeConfig") if application is not None else None
    if locale_config != "@xml/locales_config":
        failures.append("Android manifest does not declare @xml/locales_config")

# Devtool must remain a first-class execution path for all future overlays.
devtool_path = ROOT / ".devtool.toml"
if devtool_path.is_file():
    try:
        devtool = tomllib.loads(devtool_path.read_text(encoding="utf-8"))
        target = devtool.get("targets", {}).get("studyflow_android", {})
        if target.get("type") != "android":
            failures.append(".devtool.toml: studyflow_android target must have type=android")
        if target.get("root") != ".":
            failures.append(".devtool.toml: studyflow_android target must use repository root")
        tasks = target.get("tasks", {})
        if ":app:assembleDebug" not in tasks.get("build", []):
            failures.append(".devtool.toml: build task must include :app:assembleDebug")
        validation = target.get("validation", {})
        if validation.get("android_safe") is not True:
            failures.append(".devtool.toml: android_safe validation must be enabled")
        phases = validation.get("phases", {})
        for phase in ("preflight", "compile", "resources", "unit-test", "package", "lint"):
            if not phases.get(phase):
                failures.append(f".devtool.toml: missing validation phase {phase}")
    except tomllib.TOMLDecodeError as error:
        failures.append(f".devtool.toml: invalid TOML: {error}")

wrapper_path = ROOT / "gradle/wrapper/gradle-wrapper.properties"
if wrapper_path.is_file():
    wrapper = wrapper_path.read_text(encoding="utf-8")
    version_match = re.search(r"gradle-(\d+\.\d+(?:\.\d+)?)-bin\.zip", wrapper)
    if version_match is None:
        failures.append("Gradle wrapper URL does not contain a stable Gradle version")
    else:
        version = tuple(int(part) for part in version_match.group(1).split("."))
        version = version + (0,) * (3 - len(version))
        if version < (9, 7, 0):
            failures.append("Gradle wrapper must use stable Gradle >= 9.7.0")
    if re.search(r"(?m)^distributionSha256Sum=[0-9a-f]{64}$", wrapper) is None:
        failures.append("Gradle wrapper must pin a lowercase SHA-256 distribution checksum")

if failures:
    print("PHASE_1_CONTRACT: FAILED")
    print("\n".join(f"- {failure}" for failure in failures))
    sys.exit(1)

print(f"PHASE_1_CONTRACT: PASSED ({len(REQUIRED_MODULES)} modules)")
