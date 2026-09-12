# ATT12 — Final Attention Release Seal

ATT12 closes the ATT01–ATT12 Attention roadmap with a repository-plus-overlay reconciliation rather than a documentation-only seal.

## Final integration scope

- Re-audits the original ATT01–ATT09 handoff artifacts plus the applied ATT10 and ATT11 overlays against the handoff roadmap.
- Re-runs every focused ATT01–ATT11 verifier and the SFLP18/19 notification/media and WEB20 semantic boundaries.
- Adds a final ATT12 promise verifier, Web regression gate, runtime-parity gate, release integrity gate, and explicit final validation task list.
- Consolidates end-user Attention guidance and corrects the cross-platform capability matrix.

## Reconciliation fixes folded into ATT12

1. **ATT07 explicit-target delivery enforcement.** Android, Desktop, and Web now check the computed ATT07 owner/mirror target set at the actual delivery edge. A replicated ledger entry that targets another device remains scheduled locally instead of being surfaced accidentally.
2. **ATT06 Android TV action reachability.** Native TV now renders the bounded `banner.actions` list instead of hard-coding one Open action. Start/review/continue/snooze/dismiss/move-to-phone are reachable when projected by the ATT06 model.
3. **ATT06 Web TV production wiring.** TV browser mode now boots the ATT05 Attention ledger/runtime in a TV-safe foreground-only capability mode, installs the ATT06 TV bridge, and renders a remote-safe foreground banner. TV mode does not advertise system-notification/Web-Push ownership.
4. **Validation-list hygiene.** Duplicate ATT07/08/09 entries introduced by successive overlays are removed from the primary `studyflow_android` Devtool test list and ATT12 is registered exactly once.
5. **Capability truth cleanup.** The canonical matrix no longer calls foreground Web or process-owned Desktop delivery unconditional background delivery, no longer marks TV foreground Attention missing, and repairs the malformed ATT08 compact-companion row and ATT06 TV rows.
6. **Final user/validation documentation.** ATT12 adds one user-facing Attention guide, the full roadmap promise audit, and a concrete final validation matrix/command.

## Termux validation closure (v2)

The first real Termux final-seal run exposed compile/test gaps that source-only sandbox checks could not execute. ATT12 v2 folds those failures into the seal instead of weakening the validation graph:

- Android TV and Desktop ATT01 ledger storage overrides now explicitly satisfy the `Unit` write contract.
- Android declares `kotlinx-serialization-json` directly because ATT01 occurrence payloads expose `JsonObject` through the shared API.
- Android/Desktop cross-module nullable values are copied to locals before use so Kotlin 2.4 smart-cast rules are satisfied.
- ATT07 capability conversion accepts an explicit delivery-surface override, matching ATT11 reliability diagnostics/tests.
- Legacy reminder-planner regressions now assert the ATT02-promised 08:00 local summary and lead-time-versioned planned occurrence identity rather than pre-ATT02 behavior.
- Web TypeScript/runtime parity tasks are serialized under Gradle `--parallel` to avoid multiple memory-heavy `tsc` processes racing on Termux.
- The ATT10 quiet-window implementation no longer emits the two unnecessary-safe-call warnings found by the final seal.

No roadmap behavior was removed to make the gate pass; the fixes align compile contracts and stale regression expectations with the already-documented ATT02/ATT07/ATT10/ATT11 semantics.


## Second Termux validation closure (v3)

The v2 authoritative Termux run compiled the production Android, Android TV, Desktop, and shared Attention sources much further, then exposed a second layer of validation-only drift plus one compact-companion regression. ATT12 v3 is a full replacement overlay against the unchanged post-ATT11 baseline because Devtool rolled v2 back atomically.

- Android TV ATT06 tests now construct the current ATT01 `StudyFlowAttentionOccurrence` model (`workspaceId` + category/timing) instead of retired source/trigger/notification-kind fields.
- The ATT08 stale-companion test uses an explicit 5-second stale threshold so its 9-second-old fixture is genuinely stale; production keeps the documented 120-second default.
- Android source-contract tests use `Files.readAllBytes(..., UTF-8)` rather than Java `Files.readString`, which is absent from the Android unit-test compile surface.
- Desktop logic tests exclude only the Linux ARM64 Skiko native runtime from `testRuntimeClasspath`; the packaged Desktop application still retains `compose.desktop.currentOs`. This keeps headless unit tests from requiring an uncached native rendering binary on Termux.
- ATT01/ATT05/ATT12 share one memory-capped TypeScript compile/runtime-parity task. The full Web test and validation tasks no longer launch duplicate `tsc` processes under Gradle `--parallel`, and the shared compiler is ordered after heavy JVM test lanes.
- The deprecated Locale constructor in the Desktop localization regression was updated to `Locale.forLanguageTag`.

The v2 log also showed that the Desktop test failure was dependency resolution (`dl.google.com` DNS while resolving Skiko), not a failed Desktop assertion. v3 removes that native rendering dependency only from the headless unit-test runtime rather than weakening Desktop product/runtime dependencies.

## Product invariants sealed

- ATT01 remains the canonical durable Attention ledger; ATT10/ATT11 add audit metadata, not a second truth store.
- One ATT07 owner plus bounded mirrors; explicit targets are enforced by delivery call sites.
- Android TV and Web TV remain foreground-only Attention clients.
- Normal Web only claims closed-browser reliability when a real Push subscription/relay path exists.
- Desktop delivery remains process-owned; ATT12 does not claim an OS daemon or launch-on-login service.
- Compact companions are projections of a parent route and never independent delivery owners.
- Calendar export remains user initiated and does not silently mutate external calendar providers.

## Final gate

The terminal task is `verifyAtt12AttentionReleaseFinalSeal`. The intended final Devtool invocation is documented in `docs/validation/ATT12_ATTENTION_FINAL_VALIDATION.md` and explicitly includes ATT12 followed by ATT11 through ATT01 predecessor gates.


## V4 validation closure

The Termux v3 seal exposed final integration defects that source-only validation could not reproduce:
SFLP01 line-length drift, an ATT03 exact-minute refresh-boundary bug, duplicate historical W14 TypeScript
compilation, and parallel JVM test pressure. V4 fixes those causes while retaining the full ATT01–ATT12 gate.
Desktop unit tests remain headless and do not resolve host-native Skiko runtime artifacts.


## V5 validation closure

The Termux v4 seal left three lanes: two Android TV settings assertions, one Desktop settings assertion,
and the TypeScript parity child process. V5 aligns the stale tests with already-delivered product truth:
ATT11 added the fifth TV Attention card/action, and DOV23 delivered native file pickers so they are no
longer deferred. The TypeScript parity runner now discovers a project-local or PATH compiler explicitly,
reports child-process errors/signals, and falls back only when the compiler cannot execute without
diagnostics. That fallback verifies a sealed SHA-256 manifest binding `tsconfig.json`, `package.json`,
all TypeScript/TSX sources, and all committed runtime JavaScript. A real TypeScript compile still remains
the preferred path and any compiler diagnostic remains a hard failure.
