#!/usr/bin/env python3
from pathlib import Path
import hashlib, os, zipfile
root=Path(__file__).resolve().parents[1]
dist=Path(os.environ.get('STUDYFLOW_DIST_DIR',root/'dist')).resolve()
release=Path(os.environ.get('STUDYFLOW_RELEASE_DIR',root/'release')).resolve()
if not (dist/'release-manifest.json').is_file(): raise SystemExit('release-manifest.json missing; run build first')
release.mkdir(parents=True,exist_ok=True)
out=release/'studyflow-local-1.0.0.zip'
with zipfile.ZipFile(out,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
    for path in sorted(p for p in dist.rglob('*') if p.is_file()):
        rel=path.relative_to(dist).as_posix(); info=zipfile.ZipInfo(rel,date_time=(1980,1,1,0,0,0)); info.compress_type=zipfile.ZIP_DEFLATED; info.external_attr=(0o644 & 0xFFFF)<<16
        z.writestr(info,path.read_bytes())
digest=hashlib.sha256(out.read_bytes()).hexdigest(); (release/(out.name+'.sha256')).write_text(f'{digest}  {out.name}\n',encoding='utf-8')
print(out); print(digest)
