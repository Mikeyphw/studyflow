# TVOV27 — Android TV full native material viewer

TVOV27 promotes the Android-TV Library from short reference excerpts to a viewer-capable surface.
Presentation snapshots carry an additive viewer descriptor only; database rows and phone file paths remain forbidden.

The TV runtime supports paginated text, image zoom, PDF page rendering/navigation through Android `PdfRenderer`,
and media playback/seek through `VideoView`. File import, deletion, rename, export, and storage management remain phone/Desktop workflows.
TV-accessible URIs and SFLP material-stream handoff are accepted sources; unavailable sources fail visibly rather than pretending to render.
