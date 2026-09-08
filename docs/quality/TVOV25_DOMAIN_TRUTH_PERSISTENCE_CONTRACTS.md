# TVOV25 domain truth / persistence contracts

- `app-tv` is presentation/control only; no Room, data repository, or phone feature dependency may be introduced.
- Commands that create or change StudyFlow evidence are mapped to `StudyFlowTvDomainAction` using canonical ids from a READY typed presentation snapshot.
- The phone/data owner executes canonical mutations through existing StudyFlow managers/repositories.
- TV-originated mutations remain pending until a matching `StudyFlowTvDomainActionResult` is acknowledged.
- Action ids are journaled on the phone so normal network/reconnect retry does not replay a completed mutation.
- Persisted repository changes return to TV only through TVOV21 projection + TVOV24 synchronization.
- No TV helper may fabricate answers, review evidence, lifecycle completion, elapsed time, or mock score.
- Mock Exam scoring must come from the existing canonical Practice execution/scoring engine.
- Pre-existing Release size budgets remain 25 MiB target / 35 MiB hard ceiling.
- TVOV26, not TVOV25, owns physical two-device final acceptance.
- TV-originated canonical mutations continue to drain while paired even when presentation casting is stopped; D-pad persistence is not coupled to cast state.
