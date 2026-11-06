#!/usr/bin/env python3
"""Bounded read-only JVM ZIP inventory evidence; RM12 remains the verifier."""
from __future__ import annotations
import argparse
import json
from pathlib import Path, PurePosixPath
import sys
from zipfile import ZipFile, BadZipFile

CAP=4096

def inspect(directory:Path)->dict:
    if not directory.is_dir() or directory.is_symlink():
        raise ValueError('JAR artifact directory unavailable or symlinked')
    jars=list(directory.glob('*.jar'))
    if len(jars)!=1 or not jars[0].is_file() or jars[0].is_symlink():
        raise ValueError('exactly one ordinary JVM JAR required')
    jar=jars[0]
    if jar.stat().st_size>128*1024*1024:
        raise ValueError('JAR exceeds 128 MiB')
    with ZipFile(jar) as z:
        entries=z.infolist()
        if len(entries)>CAP+1:
            raise ValueError('unbounded JAR inventory')
        issues=[];seen=set();classes=0;manifest=False;expanded=0
        for item in entries:
            name=item.filename
            folded=name.casefold()
            if folded in seen:issues.append('duplicate-case-insensitive')
            seen.add(folded)
            if not name or name.startswith('/') or '\\' in name or '..' in PurePosixPath(name).parts:
                issues.append('unsafe-entry-name')
            if item.flag_bits&1:issues.append('encrypted-entry')
            if (item.external_attr>>16)&0o170000==0o120000:issues.append('symlink-entry')
            if item.file_size>16*1024*1024:issues.append('oversized-entry')
            expanded+=item.file_size
            if name.endswith('.class'):classes+=1
            if name.upper()=='META-INF/MANIFEST.MF':manifest=True
        if len(entries)>CAP:issues.append('entry-capacity-exceeded')
        if expanded>128*1024*1024:issues.append('expanded-size-exceeded')
        if z.testzip() is not None:issues.append('corrupt-zip-member')
        return {'kind':'studyflow.sfdt14.jar-inventory','jar':jar.name,
                'entries':len(entries),'classes':classes,'has_manifest':manifest,
                'entry_cap':CAP,'expanded_bytes':expanded,'issues':sorted(set(issues)),
                'not_rm12_acceptance':True,'release_qualified':False}

def main():
    p=argparse.ArgumentParser();p.add_argument('--jar-dir',type=Path,required=True)
    p.add_argument('--out',type=Path,required=True)
    args=p.parse_args()
    try:
        result=inspect(args.jar_dir)
        args.out.parent.mkdir(parents=True,exist_ok=True)
        args.out.write_text(json.dumps(result,indent=2,sort_keys=True)+'\n')
        print('SFDT14_JVM_JAR_INVENTORY',json.dumps(result,sort_keys=True))
        return 0
    except (ValueError,OSError,BadZipFile) as e:
        print('SFDT14_JAR_INVENTORY_REFUSED:',str(e)[:200],file=sys.stderr)
        return 2
if __name__=='__main__':raise SystemExit(main())
