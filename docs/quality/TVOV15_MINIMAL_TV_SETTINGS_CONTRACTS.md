# TVOV15 minimal TV settings contracts

- TVOV15 Settings is a native Android Views surface with four small presentation cards.
- Pairing status, manage pairing, and explicit revoke-pairing intent are represented.
- The display/focus accessibility preference is represented without pulling in a new UI stack.
- The local-first privacy reminder states that TV control uses intents/presentation state, not raw database rows.
- TV-safe About/diagnostics remains limited to package/protocol/presentation information.
- There is no account management, no import/export, no SAF picker, no Room/repository access, and no phone feature-module fanout from `:app-tv`.
