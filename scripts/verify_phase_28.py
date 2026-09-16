#!/usr/bin/env python3
from pathlib import Path
import re
import sys
from phase_version import phase_version_at_least

ROOT = Path(__file__).resolve().parents[1]

def read(rel):
    path = ROOT / rel
    if not path.exists():
        raise AssertionError(f"missing required file: {rel}")
    data = path.read_bytes()
    if b"\x00" in data:
        raise AssertionError(f"NUL byte found in text source: {rel}")
    return data.decode()

def require(text, needle, label):
    if needle not in text:
        raise AssertionError(f"missing {label}: {needle}")

def forbid(text, needle, label):
    if needle in text:
        raise AssertionError(f"forbidden {label}: {needle}")

if not phase_version_at_least(ROOT, 28):
    raise AssertionError("app version must be Phase 28 or later")

roadmap = read('docs/roadmap/ACTIVE_ROADMAP.md')
require(roadmap, 'single active roadmap', 'active roadmap boundary')

release_factory = read("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/ReleaseValidationSealFactory.kt")
match = re.search(r'private const val CurrentPhase = (\d+)', release_factory)
if not match or int(match.group(1)) < 28:
    raise AssertionError('release validation seal must move to Phase 28 or later')
require(release_factory, 'fun sourceOnlySeal(', 'source-only validation seal')
require(release_factory, 'fun evidenceSeal(', 'evidence-backed release seal')
require(release_factory, 'Required evidence was not supplied.', 'fail-closed missing evidence')

phase27 = read("scripts/verify_phase_27.py")
require(phase27, 'phase_version_at_least(ROOT, 27)', 'Phase 27 verifier phase-forward version check')
forbid(phase27, 'versionCode = 27', 'stale exact Phase 27 versionCode check')
forbid(phase27, '0.27.0-phase27', 'stale exact Phase 27 versionName check')

seal = read("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/BackupRestoreDrillSeal.kt")
for drill in [
    'ENCRYPTED_EXPORT', 'WRONG_PASSWORD', 'TAMPER_DETECTION', 'METADATA_PRIVATE_ANNEX',
    'RESTORE_DRY_RUN', 'RETENTION_CLEANUP', 'RECOVERY_REPORT'
]:
    require(seal, drill, f'backup drill {drill}')
require(seal, 'StudyPlanEncryptedPackageCodec', 'encrypted package codec drill')
require(seal, 'inspectBackup(encryptedPackageBytes, password)', 'real backup inspection callback')
require(seal, 'dryRunImport(', 'real dry-run callback')
require(seal, 'portability.dryRunImport', 'managed dry-run path')
require(seal, 'portability.inspectBackup', 'managed inspect path')
require(seal, 'encryptedPackageBytes.tamperedCopy()', 'tamper drill')
require(seal, 'metadataPrivateAnnexInvariantHolds', 'metadata-private annex drill')
require(seal, 'dryRunValue != null', 'restore dry-run null guard')
require(seal, 'dryRunValue.databaseRowsChanged == 0', 'zero-write restore dry-run assertion')
forbid(seal, 'dryRunValue?.databaseRowsChanged', 'nullable safe-call restore dry-run warning regression')
require(seal, 'retentionCleanupTruthfulnessHolds', 'retention cleanup truthfulness drill')
require(seal, 'recoveryReport(', 'human-readable recovery report')
require(seal, 'releaseReady', 'drill release readiness')
forbid(seal, "fill('\x00')", 'literal NUL password fill')
forbid(seal, 'databaseRowsChanged = 0,', 'production source manually constructing dry-run success')
forbid(seal, 'databaseRowsChanged = 1', 'destructive dry-run write count')

tests = read("core/domain/src/test/kotlin/com/mikeyphw/studyflow/core/domain/BackupRestoreDrillSealTest.kt")
require(tests, 'packageDrillPassesEveryRequiredScenarioUsingRealDryRunCallback', 'complete callback-backed drill test')
require(tests, 'dryRunCalled', 'dry-run callback exercise assertion')
require(tests, 'dryRunDrillBlocksWhenDryRunWritesRows', 'zero-write negative test')
require(tests, 'drillResultsStayNonDestructive', 'non-destructive drill test')
require(tests, 'Dry-run database writes: 0', 'recovery report dry-run assertion')

docs = read("docs/quality/PHASE_28_BACKUP_RESTORE_DRILL_SEAL.md")
require(docs, 'encrypted export drill', 'encrypted drill docs')
require(docs, 'wrong-password drill', 'wrong-password drill docs')
require(docs, 'tamper-detection drill', 'tamper drill docs')
require(docs, 'metadata-private annex drill', 'annex drill docs')
require(docs, 'restore dry-run drill', 'dry-run drill docs')
require(docs, 'retention cleanup drill', 'retention drill docs')
require(docs, 'human-readable recovery report', 'recovery report docs')

print('PHASE_28_CONTRACT: PASSED (backup/restore drill seal r2)')

if __name__ == '__main__':
    sys.exit(0)
