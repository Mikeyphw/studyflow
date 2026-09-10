# SFLP16 + SFLP17 — Control Protocol and Input Delegation

Status: COMPLETE.

SFLP16 adds a generic control protocol for StudyFlow peers. Control is now a leased, target-gated session rather than an implicit phone-to-TV privilege. A controller must advertise `device.control`, the target must advertise a controllable surface, every command carries a lease token, and receivers reject unknown leases, bad tokens, stale sequences, wrong targets and disallowed commands.

SFLP17 adds input delegation. Text, keyboard, D-pad, select/back, touch and voice-like input can be delegated to another peer only when that peer advertises the needed input capability. Delegations are target-gated, expiring and sequence checked. Events can also be converted into journal/domain operations for cross-device convergence.

Compatibility notes:

- Android phone, Desktop, Web, Web TV and Android TV remain generic SFLP peers.
- Android TV and Web TV can receive control/presentation/input messages without receiving repository/database write authority.
- SFLP12/SFLP13 handoff and SFLP14/SFLP15 projection remain side-by-side with the new control/input protocol.
- Validation is wired through `verifySflp16Sflp17ControlInputDelegation` for the final release gate.
