# Phase 31 Dashboard Action Completion

Phase 31 wires the dashboard primary study action into the existing sessions route and removes the fake default-plan chip interaction.

## Delivered

- Dashboard `Start session` no longer has an empty click handler.
- The primary dashboard action is passed as `onStartSession` and bound to the existing `onOpenSessions` route callback.
- The default-plan indicator is now a passive badge instead of an interactive chip with no behavior.
- Dashboard next-action copy now tells users to open study sessions, not that a recommendation engine will activate later.
- Dashboard EN/PT-BR strings remain in parity.

## Non-goals

- No new session engine was added; this phase uses the existing sessions screen and manager.
- No broad chip audit outside Dashboard was performed. That remains a separate interaction-honesty pass.

## Verification

Run:

```bash
python3 scripts/verify_phase_31.py
```

## r2 cleanup

- Preserved the completed Dashboard session-routing contract while Phase 30 resource/changelog hygiene was tightened.
