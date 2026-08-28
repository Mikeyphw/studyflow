# Concurso Público overlay completion matrix

| Overlay | Delivered contract | Schema |
|---|---|---:|
| CPOV00 | Gradle >=9.7 stable baseline, NDK r29 | 9 |
| CPOV01 | Public-exam target/profile shell | 9 |
| CPOV02 | Edital import preview and normalization | 9 |
| CPOV03 | Edital revision history, diff and coverage | 10 |
| CPOV04 | Persistent study-cycle engine | 11 |
| CPOV05 | Explainable “what should I study now?” priority | 11 |
| CPOV06 | Banca-aware question provenance/import/filters | 12 |
| CPOV07 | Frozen banca blueprint mocks and scoring policies | 12 |
| CPOV08 | Error Notebook 2.0 and configurable 1/7/30 review policy | 12 |
| CPOV09 | Structured Lei Seca / legislation study | 13 |
| CPOV10 | Derived Concurso Dashboard and Edital Matrix | 13 |
| CPOV11 | 30/15/7/2/1-day final-stretch strategy | 13 |
| CPOV12 | Public-exam onboarding, local templates and goal presets | 13 |
| CPOV13 | Full migration/portability/build/connected release seal | 13 |

## Cross-overlay invariants

- every persisted record is Study Plan-owned;
- bulk/revision/schedule mutations are preview-first and explicit;
- no fabricated banca, cutoff, incidence, edital or legal-source data;
- no duplicate session, schedule, mock, review or goal engine;
- exam dates are date-only values;
- coroutine cancellation is rethrown;
- mutation previews are version-bound where stale writes are possible;
- all persisted CPOV concepts participate in backup/restore or are explicitly derived;
- English and pt-BR resource parity is maintained;
- source validators never claim signed-artifact or real-device evidence.

## Final validation boundary

CPOV13 makes the repository build/test/portability contract complete. The existing final-release
verdict remains deliberately fail-closed until its signed-artifact and explicit manual device
evidence are supplied on a target machine.
