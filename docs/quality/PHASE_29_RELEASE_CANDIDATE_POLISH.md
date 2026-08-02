# Phase 29 Release Candidate Polish

Status: implemented as a final source-level release-candidate polish seal.

Promise audit:

- settings polish: Settings now surfaces backup, reminders, privacy, sync, runtime smoke, UX polish, and release-candidate polish in one place;
- empty/error/loading states pass: the release-candidate auditor records the MVP state-screen contract as ready;
- accessibility labels: toolbar, navigation, widget, and destructive-action copy remain string-backed gates;
- Portuguese/English resource completeness: the new release-candidate strings are present in both resource sets;
- privacy/sync/reminder copy review: copy avoids unsupported cloud-provider, account-identity, or exact-alarm claims;
- first-run and upgrade notes: documented in `docs/product/FIRST_RUN_AND_UPGRADE_NOTES.md`;
- final roadmap index: documented in `docs/roadmap/FINAL_ROADMAP_INDEX.md`.

This phase does not claim Play Store readiness by itself. Gradle, lint, assemble, installation, and runtime smoke evidence remain target-machine gates.


## r2 correction

The release-candidate polish auditor is evidence-backed. Missing evidence blocks release readiness by default; `verifiedForReleaseCandidateDocs()` is an explicit documented evidence bundle used by the Settings card after the phase docs/resources are present. The verifier rejects unconditional `complete = true` findings and checks EN/PT-BR release-card resource parity programmatically.
