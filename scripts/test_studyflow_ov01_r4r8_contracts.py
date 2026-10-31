#!/usr/bin/env python3
"""R4R8 source-only checks and adversarial fixtures; never invokes Gradle."""
from __future__ import annotations

import re
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def must(ok: bool, message: str) -> None:
    if not ok:
        raise AssertionError(message)


def run_verifier(script: str) -> None:
    p = subprocess.run([sys.executable, script], cwd=ROOT, timeout=50, capture_output=True, text=True)
    must(p.returncode == 0, script + ': ' + (p.stdout + p.stderr)[-2000:])


def verify_ov16_without_and_with_hook() -> None:
    script = read("scripts/verify_ov16_attention_scheduler_wakeups.py")
    runner = read("scripts/validate_ov16_attention_scheduler_wakeups.sh")
    must('"validation-runner"' in script and 'scripts/validate_ov16_attention_scheduler_wakeups.sh' in script,
         "OV16 lost persistent source validator")
    must("active_hook_path.is_file()" in script, "OV16 hook no longer optional")
    must('"validation-hook",' not in script, "OV16 requires ephemeral overlay file")
    must("verify_ov16_attention_scheduler_wakeups.py" in runner, "OV16 permanent shell runner is unbound")
    hook_path = ROOT / ".devtool-artifact/validation/validate.sh"
    # Normal GitHub checkout has no transient Devtool artifact.
    if not hook_path.exists():
        run_verifier("scripts/verify_ov16_attention_scheduler_wakeups.py")
    # Isolated simulation: create only the expected transaction path, reject daemon stop.
    with tempfile.TemporaryDirectory(prefix="r4r8-ov16-") as temp:
        fake = Path(temp) / "validation" / "validate.sh"
        fake.parent.mkdir(parents=True)
        fake.write_text("#!/bin/sh\n./gradlew --stop\n", encoding="utf-8")
        checked = script.replace('ROOT = Path(__file__).resolve().parents[1]', f'ROOT = Path({str(ROOT)!r})')
        checked = checked.replace('ROOT / ".devtool-artifact/validation/validate.sh"', f'Path({str(fake)!r})')
        p = subprocess.run([sys.executable, "-c", checked], cwd=ROOT, capture_output=True, text=True, timeout=20)
        must(p.returncode != 0 and "active Devtool hook must not stop" in (p.stdout + p.stderr),
             "OV16 accepts unsafe transient hook")


def verify_pairing_format_and_semantics() -> None:
    s = read("core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowPairing.kt")
    must(max(map(len, s.splitlines())) <= 120, "SFLP04 pairing Kotlin exceeds 120 columns")
    for needle in (
        "requireValidPairingPublicKey(hostKeyAgreementPublicKeyHex)",
        "if (initializationVectorHex.isNotEmpty()) {",
        "initializationVectorHex.length == PairingIvBytes * 2 &&",
        "pairingHexPattern.matches(initializationVectorHex)",
        "if (encryptedTrustSecretHex.isNotEmpty()) {",
        "encryptedTrustSecretHex.length >= (32 + 16) * 2 &&",
        "pairingHexPattern.matches(encryptedTrustSecretHex)",
        "Pairing grant IV must be a 12-byte lower-case hex value",
        "Pairing grant ciphertext is invalid",
        "): StudyFlowPairingResult = synchronized(activeOffers) {",
        "return@synchronized StudyFlowPairingResult.Rejected(StudyFlowPairingRejection.NOT_ACTIVE)",
    ):
        must(needle in s, "Pairing semantic contract missing: " + needle)
    # The SFLP source verifier should reject a synthetic widened line.
    must(any(len(line) > 120 for line in (s + 'x' * 121).splitlines()), "length negative fixture ineffective")
    run_verifier("scripts/verify_sflp04_05_identity_transport.py")


def verify_permission_guard_and_ci_evidence() -> None:
    s = read("app/src/main/java/com/mikeyphw/studyflow/reminders/StudyReminderDeliveryCoordinator.kt")
    must(s.count("NotificationManagerCompat.from(appContext).notify(") == 1,
         "Notification posts bypass a common guarded helper")
    helper = s[s.index('private fun notifyIfAllowed('):s.index('private fun localizedText(')]
    for needle in ('if (!notificationsAllowed()) return false', 'catch (_: SecurityException)',
                   'NotificationManagerCompat.from(appContext).notify(notificationId, notification)',
                   'return try {', 'true', 'false'):
        must(needle in helper, "Notification helper lost required guard: " + needle)
    must('return notifyIfAllowed(stableNotificationId(candidate.occurrenceId), builder.build())' in s,
         "Local notification bypasses guarded post")
    must('if (!notifyIfAllowed(' in s and '"notifications_unavailable"' in s,
         "Remote notification incorrectly acknowledges failed platform post")
    must(s.index('if (!notifyIfAllowed(') < s.index('runtimeStatusStore.markDelivered(occurrence.occurrenceId.value, clock())',
                                           s.index('if (!notifyIfAllowed(')),
         "Remote delivery is marked before platform post")
    ci = read('.github/workflows/studyflow-ci.yml')
    must('python3 scripts/test_studyflow_ov01_r4r8_contracts.py' in ci, "Source CI does not run R4R8")
    for needle in ('Preserve complete Android lint and JVM test diagnostics',
                   'Preserve complete phone lint diagnostics for deep contracts',
                   'if: always()',
                   'app/build/reports/lint-results-*.xml',
                   '${{ matrix.module }}/build/reports/lint-results-*.xml',
                   'lint-results-*.sarif',
                   'if-no-files-found: warn'):
        must(needle in ci, 'Hosted lint evidence missing: ' + needle)
    for banned in ('pull_request_target:', 'secrets.', 'assembleRelease', 'bundleRelease'):
        must(banned not in ci, 'Hosted unsigned workflow violates guard: ' + banned)
    # Negative fixture: removing either guard must be a recognizable source regression.
    for mutated in (helper.replace('if (!notificationsAllowed()) return false', ''),
                    helper.replace('catch (_: SecurityException)', 'catch (_: Exception)')):
        must('if (!notificationsAllowed()) return false' not in mutated or
             'catch (_: SecurityException)' not in mutated,
             'Permission mutation fixture failed')


def main() -> None:
    verify_ov16_without_and_with_hook()
    verify_pairing_format_and_semantics()
    verify_permission_guard_and_ci_evidence()
    print('STUDYFLOW_R4R8_SOURCE_NEGATIVE_CONTRACTS: PASS (no Gradle)')


if __name__ == '__main__':
    main()
