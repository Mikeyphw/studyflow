#!/usr/bin/env python3
from pathlib import Path
import sys
from phase_version import phase_version_at_least

ROOT = Path(__file__).resolve().parents[1]

def read(rel):
    path = ROOT / rel
    if not path.exists():
        raise AssertionError(f"missing required file: {rel}")
    return path.read_text()

def require(text, needle, label):
    if needle not in text:
        raise AssertionError(f"missing {label}: {needle}")

if not phase_version_at_least(ROOT, 26):
    raise AssertionError("app version must be Phase 26 or later")

root_build = read("build.gradle.kts")
require(root_build, 'through Phase', 'repository contract description')
require(root_build, 'verify_phase_${', 'contract phase loop')

model = read("core/model/src/main/kotlin/com/mikeyphw/studyflow/core/model/ReleaseValidation.kt")
for gate in [
    'ARTIFACT_MANIFEST', 'SOURCE_CONTRACTS', 'GRADLE_CONFIGURATION', 'MAIN_COMPILATION',
    'TEST_COMPILATION', 'UNIT_TESTS', 'ROOM_SCHEMA', 'RESOURCE_MERGE', 'LINT', 'ASSEMBLE',
    'DEVICE_SMOKE', 'FINAL_STAGING'
]:
    require(model, gate, f'release gate {gate}')

factory = read("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/ReleaseValidationSealFactory.kt")
require(factory, 'private const val CurrentPhase = 26', 'current phase marker')
require(factory, 'sourceOnlySeal', 'source-only seal')
require(factory, 'completedSeal', 'completed seal')
require(factory, 'ReleaseValidationGateKind.GRADLE_CONFIGURATION', 'Gradle gate')
require(factory, 'blocked(', 'blocked gate helper usage')
require(factory, 'ReleaseValidationGateKind.DEVICE_SMOKE', 'device smoke gate')
require(factory, 'ReleaseValidationGateKind.values().toSet()', 'all gate coverage')

tests = read("core/domain/src/test/kotlin/com/mikeyphw/studyflow/core/domain/ReleaseValidationSealFactoryTest.kt")
require(tests, 'sourceOnlySealBlocksReleaseUntilRealBuildEvidenceExists', 'source-only blocking test')
require(tests, 'completedSealCoversEveryRequiredGate', 'completed gate coverage test')

docs = read("docs/quality/PHASE_26_RELEASE_VALIDATION_SEAL.md")
require(docs, 'Promise audit:', 'promise audit docs')
require(docs, 'No Room migration, network permission', 'negative scope docs')

print('PHASE_26_CONTRACT: PASSED (release validation seal)')

if __name__ == '__main__':
    sys.exit(0)
