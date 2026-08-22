# Phase 22 + 23 Promise Audit

This audit compares the implementation with the original Phase 22 and Phase 23 roadmap promises and the confirmed
landed Phase 20 source baseline. The overlay is complete against Phase 20 and does not depend on pending Phase 21 code.

## Audit 1: implementation completeness

### Phase 22: Global Search and Smart Recommendations

| Promise | Delivery status and evidence |
|---|---|
| Global search screen | **Delivered.** `feature/search/StudySearchScreen.kt`, `SearchRoute`, and app navigation wiring. |
| Search plans, subjects, topics, questions, notes, materials, flashcards, and errors | **Delivered.** Eight `GlobalSearchContentType` values, eight repository branches, and eight Room search queries. |
| Filters by content type | **Delivered.** Search filter chips and repository filter policy. |
| Recent searches | **Delivered.** Bounded, deduplicated, clearable DataStore-backed history. |
| Jump-to-result navigation | **Delivered.** Every result maps to the correct typed, plan-scoped feature destination. Exact entity focusing is not claimed where the destination feature has no item-focus route. |
| Suggested next study item | **Delivered.** Deterministic `NEXT_STUDY_ITEM` recommendation. |
| Suggested weak-topic review | **Delivered.** Deterministic `WEAK_TOPIC_REVIEW` recommendation. |
| Suggested practice deck | **Delivered.** Deterministic `PRACTICE_DECK` recommendation. |
| Suggested overdue resource | **Delivered.** Deterministic `OVERDUE_RESOURCE` recommendation. |
| Mastery input | **Delivered.** Topic mastery projection and structured `MASTERY` reason. |
| Due-review input | **Delivered.** Due-review projection and `DUE_REVIEW_COUNT` reason. |
| Schedule-adherence input | **Delivered.** Schedule projection and `SCHEDULE_ADHERENCE` reason. |
| Wrong-answer-frequency input | **Delivered.** Error recurrence projection and `RECURRING_ERRORS` reason. |
| Question-accuracy input | **Delivered.** Accuracy projection and `QUESTION_ACCURACY` reason. |
| Goal-deadline input | **Delivered.** Goal projection and `GOAL_DEADLINE` reason. |
| Unfinished-material input | **Delivered.** Material progress projection and resource reasons. |
| Archived/deleted content excluded by default | **Delivered.** Plan-state and parent-archive predicates cover direct subjects/topics plus linked questions, errors, flashcards, materials, activities, reviews, and goals. Schema-backed fixtures verify exclusion. |
| Deterministic tests | **Delivered.** Stable ordering, explicit tie-breakers, domain tests, and SQL behavior checks. |
| Explain why each choice was made | **Delivered.** Structured reason model localized by the feature UI. |
| Toolchain compatibility | **Delivered.** Room-backed implementation with no AppSearch, `kapt`, Kotlin Android plugin, or Room migration. |

### Phase 23: Privacy, Encryption, and Backup Hardening

| Promise | Delivery status and evidence |
|---|---|
| Optional encrypted `.studyplan` | **Delivered.** Versioned authenticated envelope around the existing package. |
| Password-based export encryption | **Delivered.** AES-256-GCM with PBKDF2-HMAC-SHA256, random salt/nonce, authenticated header, minimum password length, and no stored key. |
| Export metadata privacy mode | **Delivered.** Fresh per-export scope, unlinkable pseudonymous root/primary/foreign IDs, generic manifest metadata, day-granular timestamp, and generic export filename. |
| Redact private notes | **Delivered.** Notes table omitted and declared in manifest privacy metadata. |
| Redact study history | **Delivered.** History tables omitted; retained progress and completion traces reset; package invariants reject leakage. |
| Backup integrity report | **Delivered.** Package checksums, exact inventory validation, privacy-boundary reporting, and bounded decoding. |
| Restore dry-run | **Delivered.** Full decode and graph validation with `databaseRowsChanged = 0`; no graph import or snapshot mutation. |
| Recovery explanation UI | **Delivered.** Backup-center explanation and dedicated privacy center. |
| Local data retention policy screen | **Delivered.** Truthful local retention rules in English and Portuguese (Brazil). |
| Encrypted package requires password | **Delivered.** Explicit password-required exception and UI guidance. |
| Wrong password fails safely | **Delivered.** GCM authentication failure prevents package parsing/import; temporary plaintext is discarded. |
| Unencrypted export still works | **Delivered.** Prefix detection preserves the existing ZIP package path. |
| Privacy export excludes selected sections | **Delivered.** Export transformation plus package-level privacy validation tests. |
| Historical local sync boundary | **Superseded.** Current product language is local-first optional cloud sync with sync off by default. |
| Android backup hardening | **Delivered.** Android 11 `fullBackupContent` and Android 12+ `dataExtractionRules` remain wired with valid domains; passwords and archive keys are not persisted. |

## Cryptographic boundary review

Portable password backups are deliberately not Android-Keystore-bound. A Keystore-bound archive would not be
restorable on another device, contradicting the `.studyplan` portability requirement. The password-derived archive key
exists only in memory and is cleared after use. JCA AES-GCM and PBKDF2 are implemented in the pure portability module;
Tink was evaluated but is not claimed or added because it would not replace the password-KDF contract. The decision is
recorded in `docs/architecture/ADR-0027-portability-privacy-encryption.md`.

## Audit 2: independent release recheck

The independent pass started again from the Phase 20 baseline and verified that:

- all eight promised search types exist and map to typed plan-scoped destinations;
- all four recommendation families exist, are deterministic, and expose structured explanations;
- recommendation SQL excludes rows linked through archived subjects, topics, materials, questions, errors, and
  flashcards, while topic subqueries remain scoped to the selected Study Plan;
- metadata-private IDs are stable inside one export but differ across separate exports;
- metadata-private annex payload lookup uses the exported pseudonymous annex ID without leaking the source ID into
  the package entry path; the r3 repair also asserts the manifest annex ID, decoded archive metadata, and ZIP entry names stay private;
- encrypted restore streams through app-private cache, supports the same practical package ceiling as unencrypted
  restore, and parses the package only after authentication succeeds;
- wrong-password and ciphertext-tamper paths do not import partial plaintext;
- unencrypted packages retain the previous import path;
- note/history redaction and privacy declarations are enforced as package invariants;
- dry-run performs no graph import or database mutation;
- historical verifiers use a durable “phase N or later” parser instead of enumerating future exact versions;
- the overlay contains complete Phase 22 and 23 source, tests, resources, Gradle wiring, docs, and validation tasks;
- no Phase 21 module or source file is required;
- no local Devtool runtime state, directory-only inventory entry, nonexistent staging path, or deletion is packaged.

## Promise conclusion

Every original Phase 22 and Phase 23 roadmap item is delivered. No additional promise is silently represented as
complete: exact entity-focused navigation, cloud sync, Android-Keystore-bound portable archives, and Tink integration
are not claimed.

## Validation boundary

Artifact/source checks performed during generation include the historical verifier chain through Phase 23, Kotlin PSI
syntax parsing, XML/TOML parsing, Room-schema SQL preparation and behavior fixtures, focused Kotlin compilation,
30 focused unit-test executions with functional assertions, cryptographic vector/round-trip/tamper checks, clean-copy
overlay application twice, inventory/source/output hash verification, and final staging-path simulation.

The artifact-generation container does not provide the Android SDK or Gradle 9.5 execution environment. Full Android
Gradle configuration, main/test compilation, resource merging, lint, APK assembly, installation, and device smoke tests
remain mandatory target Devtool gates. They are configured in `.devtool.toml` and are not claimed as locally run.
