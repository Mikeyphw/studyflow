# TVOV07 Today workspace contracts

- The Today workspace must show the next study block, due reviews, practice queue, weekly progress, and schedule/mock-prep context.
- The Today workspace must be D-pad focusable without adding Compose Runtime or AndroidX TV UI.
- Every Today action must route to a concrete TV destination.
- Every Today action must also be representable as a paired-phone control command through the shared TV protocol.
- The TV app must not pull `core:data`, `core:database`, or phone feature modules to render this surface.
