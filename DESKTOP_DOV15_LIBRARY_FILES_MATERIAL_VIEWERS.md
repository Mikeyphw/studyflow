# Desktop DOV15 — Library, files and material viewers

DOV15 replaces the Desktop library shell with durable local resource ownership.

- Library metadata persists in `library-v1.properties`.
- Local imports are copied into `library-files/<material-id>/` under the DOV08 workspace; the catalog stores a stable `content://studyflow-desktop/...` URI instead of the original source path.
- The URI carries a URL-safe encoded workspace-relative token, so spaces and ordinary filesystem characters round-trip safely without enabling workspace escape.
- HTTP/HTTPS resources, favorites and catalog metadata persist across restart.
- The in-app viewer provides bounded text previews, image dimensions, streaming PDF page-token estimation and binary metadata.
- Permanent Plan deletion purges both library metadata and app-owned imported bytes. Failed imports also clean copied bytes.
- Native OS file picking, open-with and browser launching remain intentionally assigned to DOV23.
- `Full material viewing` remains LIMITED until those native integrations and the DOV24 acceptance seal are complete.
