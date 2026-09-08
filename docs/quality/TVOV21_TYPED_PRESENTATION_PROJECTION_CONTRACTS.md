# TVOV21 typed presentation projection contracts

- `:core:tvcontrol` owns the serializable typed presentation schema; it carries presentation data, not raw database rows or arbitrary UI trees.
- The canonical phone/data-owning process projects real StudyFlow repository/domain state into that schema.
- `app-tv` consumes only the shared typed schema and keeps its existing dependency isolation from Room, `core:data`, `core:database`, and phone feature modules.
- Runtime fixture defaults are forbidden as the TV Activity's data source. A missing projection is represented as an explicit waiting/empty state.
- Canonical source IDs are retained when typed questions, schedule activities, review items, mocks, and library references are adapted into bounded native-TV display slots.
- Presentation snapshots are monotonic on the TV side: duplicate snapshot IDs and older timestamps are rejected.
- Pairing/network transport remains deferred to TVOV22; TVOV21 must not pretend the typed projection source is already deliverable over the wire.
