# UI/UX V2 Overlay Lessons

- **Risk:** feature routes and workspace tabs can redirect into each other. **Prevention:** keep
  compatibility routes and full-management routes separate, then verify every workspace action.
- **Risk:** a focus countdown implemented only in Compose state disappears after process death.
  **Prevention:** persist the target duration and derive displayed time from session timestamps.
- **Risk:** manually invented Room identity hashes create false schema artifacts. **Prevention:** use
  Room's deterministic schema identity algorithm and verify it reproduces prior checked-in hashes.
- **Risk:** split navigation files can make old validators report removed features. **Prevention:**
  update validators to inspect the real architecture, never add marker comments that fake presence.
- **Risk:** Compose directional icons and scoped modifier imports can fail lint or compilation.
  **Prevention:** use auto-mirrored directional icons and receiver-scoped modifiers without imports.
- **Risk:** a redesign can become decorative while existing mutations remain elsewhere.
  **Prevention:** every summary card either performs a real manager action or opens the complete
  existing workflow.
- **Corpus match:** the supplied failure archive contained analogous Room schema, Compose import,
  directional icon, JUnit API, resource, validator, inventory, and source-hash failures.
